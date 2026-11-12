/**
 * Strongly typed client for the MetroMind backend REST routing API — the single
 * place the frontend talks to the backend.
 *
 * <p>Phase 11 wires the React app to the Phase 10 endpoint
 * {@code POST /api/routes}, which runs the <b>real</b> Java BFS / Dijkstra / A*
 * routers over {@code data/metro-network.json}. Components never call
 * {@code fetch()} directly: they ask for a route through {@link calculateRoute}
 * and receive a {@link RouteResponse} back. The module is dependency-free
 * ({@code fetch} + {@code import.meta.env}), so it also runs unchanged under
 * Node — the verification script in {@code scripts/verify-routes-api.mjs}
 * imports this exact code and drives it against a live backend.</p>
 */

/** Algorithms the backend supports (Phase 10 request enum, case-sensitive). */
export type RouteAlgorithm = 'BFS' | 'DIJKSTRA' | 'ASTAR'

/** Metrics the backend supports (Phase 10 of {@code RouteMetric}); Dijkstra only. */
export type RouteMetric = 'DISTANCE' | 'TRAVEL_TIME'

/** The exact request body the backend expects (station IDs in dataset form). */
export interface RouteRequest {
  sourceId: string
  destinationId: string
  algorithm: RouteAlgorithm
  /** Required only for DIJKSTRA; ignored by BFS and A*. */
  metric: RouteMetric | null
}

/**
 * The Phase 10 response document, mirrored verbatim from the backend.
 *
 * <p>{@code explored} is always an empty list: the Java routers expose an
 * exploration <em>count</em>, not an order, so the backend never sends one and
 * this frontend never fabricates one.</p>
 */
export interface RouteResponse {
  algorithm: RouteAlgorithm
  metric: RouteMetric | null
  sourceId: string
  destinationId: string
  found: boolean
  stationIds: string[]
  hopCount: number
  totalDistanceKm: number
  totalTravelTimeMinutes: number
  explored: string[]
}

/**
 * Thrown by {@link calculateRoute} for every non-200 answer and for an
 * unreachable/malformed backend, so the UI can render one friendly message.
 */
export class RouteApiError extends Error {
  /** HTTP status that the backend answered with; 0 when it was never reached. */
  readonly status: number
  /** The backend's stable error code (e.g. {@code STATION_NOT_FOUND}) when known. */
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'RouteApiError'
    this.status = status
    this.code = code
  }
}

/** Local development backend. Configure a deployed URL via {@code VITE_API_BASE_URL}. */
const DEFAULT_API_BASE_URL = 'http://localhost:8080'

// Vite injects `import.meta.env` (typed by vite/client); the cast keeps this
// module importable under plain Node too (where `import.meta.env` is undefined).
const viteEnv =
  (import.meta as unknown as { env?: Record<string, string | undefined> }).env ?? {}

/** Base URL of the routing API, from {@code VITE_API_BASE_URL}. */
export const API_BASE_URL = (
  viteEnv.VITE_API_BASE_URL ?? DEFAULT_API_BASE_URL
).replace(/\/+$/, '')

/**
 * POSTs a route request to the backend and returns the parsed, shape-checked
 * {@link RouteResponse}.
 *
 * @param request the station pair + algorithm/metric to run
 * @param signal optional {@link AbortSignal} to cancel an in-flight request
 * @throws {@link RouteApiError} on any HTTP error, network failure, or
 *         malformed response (an aborted request rethrows the abort error)
 */
export async function calculateRoute(
  request: RouteRequest,
  signal?: AbortSignal,
): Promise<RouteResponse> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}/api/routes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
      signal,
    })
  } catch (error) {
    if (isAbortError(error)) {
      throw error
    }
    throw new RouteApiError(
      0,
      'NETWORK_ERROR',
      'The routing service could not be reached. Start the Spring Boot backend and check that VITE_API_BASE_URL points at it.',
    )
  }

  let body: unknown = null
  const text = await response.text().catch(() => '')
  if (text) {
    try {
      body = JSON.parse(text) as unknown
    } catch {
      body = null
    }
  }

  if (!response.ok) {
    throw toHttpError(response.status, body)
  }

  const parsed = parseRouteResponse(body)
  if (parsed === null) {
    throw new RouteApiError(
      response.status,
      'BAD_RESPONSE',
      'The routing service returned an unexpected response.',
    )
  }
  return parsed
}

/** Build a {@link RouteApiError} from a non-200 status and optional body. */
function toHttpError(status: number, body: unknown): RouteApiError {
  if (body !== null && typeof body === 'object') {
    const record = body as { error?: unknown; message?: unknown }
    if (typeof record.error === 'string') {
      const message =
        typeof record.message === 'string' && record.message !== ''
          ? record.message
          : friendlyHttpMessage(status)
      return new RouteApiError(status, record.error, message)
    }
  }
  return new RouteApiError(status, `HTTP_${status}`, friendlyHttpMessage(status))
}

/** User-facing fallback messages for statuses the backend has no body for. */
function friendlyHttpMessage(status: number): string {
  switch (status) {
    case 400:
      return 'The request was not valid.'
    case 404:
      return 'One of the selected stations is unknown to the routing service.'
    case 405:
      return 'The routing endpoint does not accept this request method.'
    case 500:
      return 'The routing service encountered an internal error.'
    default:
      return `The routing service answered with HTTP ${status}.`
  }
}

/** Shape-check the backend body; null when it is not a usable response. */
function parseRouteResponse(body: unknown): RouteResponse | null {
  if (body === null || typeof body !== 'object') {
    return null
  }
  const b = body as Record<string, unknown>
  if (typeof b.algorithm !== 'string') return null
  if (b.metric !== null && b.metric !== 'DISTANCE' && b.metric !== 'TRAVEL_TIME') return null
  if (typeof b.sourceId !== 'string' || typeof b.destinationId !== 'string') return null
  if (typeof b.found !== 'boolean') return null
  if (!Array.isArray(b.stationIds) || !b.stationIds.every((id) => typeof id === 'string')) return null
  if (typeof b.hopCount !== 'number') return null
  if (typeof b.totalDistanceKm !== 'number' || typeof b.totalTravelTimeMinutes !== 'number') {
    return null
  }
  const explored = Array.isArray(b.explored)
    ? (b.explored.filter((id): id is string => typeof id === 'string'))
    : []
  return {
    algorithm: b.algorithm as RouteAlgorithm,
    metric: b.metric as RouteMetric | null,
    sourceId: b.sourceId,
    destinationId: b.destinationId,
    found: b.found,
    stationIds: b.stationIds as string[],
    hopCount: b.hopCount,
    totalDistanceKm: b.totalDistanceKm,
    totalTravelTimeMinutes: b.totalTravelTimeMinutes,
    explored,
  }
}

/** True when a fetch failure is a deliberate {@link AbortController} abort. */
function isAbortError(error: unknown): boolean {
  if (typeof error !== 'object' || error === null) {
    return false
  }
  if (typeof DOMException !== 'undefined' && error instanceof DOMException) {
    return error.name === 'AbortError'
  }
  return (error as { name?: unknown }).name === 'AbortError'
}