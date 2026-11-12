#!/usr/bin/env node
/**
 * Reproducible end-to-end check that the frontend's routing integration talks
 * to the genuine Phase 10 backend API.
 *
 * It imports the <b>real</b> client ({@code src/api/routes.ts}, transpiled in
 * memory with the project's installed TypeScript) and drives it against a live
 * Spring Boot backend — the identical request/response contract the browser
 * uses. Requires:
 *
 *   - the backend running (default http://localhost:8080); and
 *   - run from {@code frontend/}:  node scripts/verify-routes-api.mjs
 *
 * Set {@code VITE_API_BASE_URL} to point the imported client at a different
 * backend, exactly as the deployed frontend would.
 */
import * as ts from 'typescript'
import { readFile, writeFile, rm, mkdtemp } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const clientSource = path.join(here, '..', 'src', 'api', 'routes.ts')

const BASE_URL = (
  process.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
).replace(/\/+$/, '')

const SUCCESS = 'PASS'
const FAILURE = 'FAIL'
let failures = 0

function check(name, condition, detail = '') {
  if (condition) {
    console.log(`${SUCCESS}  ${name}`)
  } else {
    failures += 1
    console.log(`${FAILURE}  ${name}${detail ? ` — ${detail}` : ''}`)
  }
}

const ok = (res) => res.ok

async function postRoutes(body, headers = {}) {
  return fetch(`${BASE_URL}/api/routes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  })
}

// ------------------------------------------------------------------ client compile

// Transpile the real client to ESM in a temp dir and import it so the tests
// exercise exactly the code the browser ships (no copy, no re-implementation).
const transpiled = ts.transpileModule(await readFile(clientSource, 'utf8'), {
  compilerOptions: {
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.ESNext,
  },
  fileName: clientSource,
})
const tmpDir = await mkdtemp(path.join(os.tmpdir(), 'metromind-verify-'))
const modulePath = path.join(tmpDir, 'routes.mjs')
await writeFile(modulePath, transpiled.outputText)
const client = await import(pathToFileURL(modulePath).href)
const { calculateRoute, API_BASE_URL, RouteApiError } = client

check('client API_BASE_URL resolved', API_BASE_URL === BASE_URL, `got ${API_BASE_URL}`)

// ------------------------------------------------------------------ health

const health = await fetch(`${BASE_URL}/api/health`)
check('GET /api/health is 200', ok(health), `status ${health.status}`)
const healthBody = await health.json()
check('health body reports ok', healthBody?.status === 'ok')

// ------------------------------------------------------------------ real routes

try {
  const bfs = await calculateRoute({
    sourceId: 'RAJIV_CHOWK',
    destinationId: 'NEW_DELHI',
    algorithm: 'BFS',
    metric: null,
  })
  check('BFS: algorithm echoed', bfs.algorithm === 'BFS')
  check('BFS: found', bfs.found)
  check('BFS: starts and ends at the requested stations',
    bfs.stationIds[0] === 'RAJIV_CHOWK' && bfs.stationIds.at(-1) === 'NEW_DELHI')
  check('BFS: hopCount is stationIds.length - 1',
    bfs.hopCount === bfs.stationIds.length - 1, `hops ${bfs.hopCount}`)
  check('BFS: distance and time are numbers > 0',
    bfs.totalDistanceKm > 0 && bfs.totalTravelTimeMinutes > 0)
  check('BFS: metric is null on the wire', bfs.metric === null)
  check('BFS: explored is an array (empty per API contract)', Array.isArray(bfs.explored))
} catch (error) {
  check('BFS route (calculateRoute)', false, String(error))
}

try {
  const byDistance = await calculateRoute({
    sourceId: 'DWARKA_SECTOR_21',
    destinationId: 'HAUZ_KHAS',
    algorithm: 'DIJKSTRA',
    metric: 'DISTANCE',
  })
  check('Dijkstra DISTANCE: metric echoed', byDistance.metric === 'DISTANCE')
  check('Dijkstra DISTANCE: found', byDistance.found)
  check('Dijkstra DISTANCE: hopCount consistent',
    byDistance.hopCount === byDistance.stationIds.length - 1)
} catch (error) {
  check('Dijkstra DISTANCE route (calculateRoute)', false, String(error))
}

try {
  const byTime = await calculateRoute({
    sourceId: 'DWARKA_SECTOR_21',
    destinationId: 'HAUZ_KHAS',
    algorithm: 'DIJKSTRA',
    metric: 'TRAVEL_TIME',
  })
  check('Dijkstra TRAVEL_TIME: metric echoed', byTime.metric === 'TRAVEL_TIME')
  check('Dijkstra TRAVEL_TIME: found', byTime.found)
} catch (error) {
  check('Dijkstra TRAVEL_TIME route (calculateRoute)', false, String(error))
}

try {
  const astar = await calculateRoute({
    sourceId: 'RAJIV_CHOWK',
    destinationId: 'SAMAYPUR_BADLI',
    algorithm: 'ASTAR',
    metric: null,
  })
  check('A*: algorithm echoed', astar.algorithm === 'ASTAR')
  check('A*: found', astar.found)
  check('A*: hopCount is stationIds.length - 1',
    astar.hopCount === astar.stationIds.length - 1)
} catch (error) {
  check('A* route (calculateRoute)', false, String(error))
}

// ------------------------------------------------------------------ client errors

async function expectError(name, body, status, code) {
  let threw = null
  try {
    await calculateRoute(body)
  } catch (error) {
    threw = error
  }
  check(name, threw instanceof RouteApiError && threw.status === status && threw.code === code,
    threw ? `status ${threw.status} code ${threw.code}` : 'no error thrown')
}

await expectError('client: unknown station -> 404 STATION_NOT_FOUND',
  { sourceId: 'NOT_A_STATION', destinationId: 'NEW_DELHI', algorithm: 'BFS', metric: null },
  404, 'STATION_NOT_FOUND')

await expectError('client: unsupported algorithm -> 400 UNSUPPORTED_ALGORITHM',
  { sourceId: 'RAJIV_CHOWK', destinationId: 'NEW_DELHI', algorithm: 'FLOYD', metric: null },
  400, 'UNSUPPORTED_ALGORITHM')

await expectError('client: Dijkstra without metric -> 400 INVALID_REQUEST',
  { sourceId: 'RAJIV_CHOWK', destinationId: 'NEW_DELHI', algorithm: 'DIJKSTRA', metric: null },
  400, 'INVALID_REQUEST')

const methodNotAllowed = await fetch(`${BASE_URL}/api/routes`, { method: 'GET' })
check('HTTP: GET /api/routes -> 405 METHOD_NOT_ALLOWED',
  methodNotAllowed.status === 405 &&
    (await methodNotAllowed.json())?.error === 'METHOD_NOT_ALLOWED',
  `status ${methodNotAllowed.status}`)

// ------------------------------------------------------------------ CORS from the Vite origin

const VITE_ORIGIN = 'http://localhost:5173'
const preflight = await fetch(`${BASE_URL}/api/routes`, {
  method: 'OPTIONS',
  headers: {
    Origin: VITE_ORIGIN,
    'Access-Control-Request-Method': 'POST',
  },
})
check('CORS: preflight from Vite origin is 200', preflight.status === 200, `status ${preflight.status}`)
check('CORS: preflight allows the Vite origin',
  preflight.headers.get('Access-Control-Allow-Origin') === VITE_ORIGIN,
  `header: ${preflight.headers.get('Access-Control-Allow-Origin') ?? 'missing'}`)

const corsPost = await postRoutes(
  { sourceId: 'RAJIV_CHOWK', destinationId: 'NEW_DELHI', algorithm: 'BFS', metric: null },
  { Origin: VITE_ORIGIN },
)
check('CORS: actual POST answers with the Vite origin',
  corsPost.status === 200 &&
    corsPost.headers.get('Access-Control-Allow-Origin') === VITE_ORIGIN,
  `status ${corsPost.status}`)

const foreignPreflight = await fetch(`${BASE_URL}/api/routes`, {
  method: 'OPTIONS',
  headers: { Origin: 'https://evil.example', 'Access-Control-Request-Method': 'POST' },
})
check('CORS: unapproved origin is denied', foreignPreflight.status === 403,
  `status ${foreignPreflight.status}`)

// ------------------------------------------------------------------ summary

await rm(tmpDir, { recursive: true, force: true })

console.log(failures === 0
  ? `\nAll ${SUCCESS} — the frontend client is verified against ${BASE_URL}.`
  : `\n${failures} check(s) FAILED against ${BASE_URL}.`)
if (failures > 0) process.exitCode = 1