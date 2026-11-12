import { useMemo, useRef, useState } from 'react'
import './App.css'
import { loadNetwork } from './network/loadNetwork'
import type { NetworkView } from './network/graph'
import { calculateRoute, RouteApiError } from './api/routes'
import {
  mapRouteResponseToUiResult,
  type RouteResult as UiRouteResult,
} from './api/routeResult'
import {
  ALGORITHMS,
  ALGORITHM_BY_KEY,
  routeRequestFor,
  type AlgorithmKey,
} from './api/algorithms'
import StationMap from './components/StationMap'
import SourceDestinationBar from './components/SourceDestinationBar'
import RouteResult from './components/RouteResult'
import AlgorithmPanel from './components/AlgorithmPanel'
import StatisticsPanel from './components/StatisticsPanel'

/**
 * Lifecycle of one algorithm's route query against the backend. {@code ready}
 * results are {@link UiRouteResult}s mapped from the real API response; a
 * {@code found: false} result is a normal, successful answer (not an error).
 */
type RunState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'ready'; result: UiRouteResult }
  | { status: 'error'; message: string }

const IDLE_RUNS: Record<AlgorithmKey, RunState> = {
  bfs: { status: 'idle' },
  'dijkstra-distance': { status: 'idle' },
  'dijkstra-time': { status: 'idle' },
  astar: { status: 'idle' },
}

function App() {
  // Load the shared dataset once; build the derived network view once.
  const view: NetworkView = useMemo(() => loadNetwork(), [])

  const [sourceId, setSourceId] = useState<string | null>(null)
  const [destinationId, setDestinationId] = useState<string | null>(null)
  const [activeKey, setActiveKey] = useState<AlgorithmKey>('bfs')
  const [runs, setRuns] = useState<Record<AlgorithmKey, RunState>>(IDLE_RUNS)
  const inFlight = useRef<AbortController | null>(null)

  const stations = useMemo(() => Array.from(view.stations.values()), [view])

  // The one authoritative route-calculation path: React -> POST /api/routes ->
  // Spring Boot -> Java BFS/Dijkstra/A*. Triggered by whichever input changed
  // (stations, algorithm, swap, reset); any in-flight request for an older pair
  // is aborted so a stale result is never displayed.
  function routeNow(nextSource: string | null, nextDestination: string | null, key: AlgorithmKey) {
    if (nextSource === null || nextDestination === null || nextSource === nextDestination) {
      inFlight.current?.abort()
      setRuns(IDLE_RUNS)
      return
    }
    const request = routeRequestFor(ALGORITHM_BY_KEY[key], nextSource, nextDestination)
    inFlight.current?.abort()
    const controller = new AbortController()
    inFlight.current = controller
    setRuns((prev) => ({ ...prev, [key]: { status: 'loading' } }))

    calculateRoute(request, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return
        setRuns((prev) => ({
          ...prev,
          [key]: { status: 'ready', result: mapRouteResponseToUiResult(response) },
        }))
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        setRuns((prev) => ({
          ...prev,
          [key]: { status: 'error', message: friendlyRouteError(error) },
        }))
      })
  }

  const activeRun = runs[activeKey]

  // Only real, found routes highlight the map; a found:false answer shows the
  // "no route" state but highlights nothing.
  const activeRouteIds =
    activeRun.status === 'ready' && activeRun.result.found
      ? activeRun.result.stationIds
      : []

  function pickStation(id: string) {
    let nextSource = sourceId
    let nextDestination = destinationId
    if (nextSource === id) {
      nextSource = null
    } else if (nextDestination === id) {
      nextDestination = null
    } else if (nextSource === null) {
      nextSource = id
    } else {
      nextDestination = id
    }
    setSourceId(nextSource)
    setDestinationId(nextDestination)
    routeNow(nextSource, nextDestination, activeKey)
  }

  function onSourceChange(id: string | null) {
    setSourceId(id)
    routeNow(id, destinationId, activeKey)
  }

  function onDestinationChange(id: string | null) {
    setDestinationId(id)
    routeNow(sourceId, id, activeKey)
  }

  function onActivate(key: AlgorithmKey) {
    setActiveKey(key)
    routeNow(sourceId, destinationId, key)
  }

  function swap() {
    setSourceId(destinationId)
    setDestinationId(sourceId)
    routeNow(destinationId, sourceId, activeKey)
  }

  function resetAll() {
    inFlight.current?.abort()
    setSourceId(null)
    setDestinationId(null)
    setActiveKey('bfs')
    setRuns(IDLE_RUNS)
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>MetroMind</h1>
        <p className="subtitle">Intelligent Metro Route Planner</p>
        <span className="network-badge">{view.name}</span>
      </header>

      <main className="layout">
        <section className="controls-column">
          <SourceDestinationBar
            stations={stations}
            sourceId={sourceId}
            destinationId={destinationId}
            onSourceChange={onSourceChange}
            onDestinationChange={onDestinationChange}
            onSwap={swap}
            onReset={resetAll}
          />

          {activeRun.status === 'loading' && (
            <section className="route-panel" aria-live="polite">
              <header className="panel-heading">
                <h2>Route</h2>
                <span className="route-source-target">
                  {view.stations.get(sourceId!)?.name} → {view.stations.get(destinationId!)?.name}
                </span>
              </header>
              <div className="route-loading">Calculating route with the live routing API…</div>
            </section>
          )}

          {activeRun.status === 'ready' && (
            <section className="route-panel">
              <header className="panel-heading">
                <h2>Route</h2>
                <span className="route-source-target">
                  {view.stations.get(sourceId!)?.name} → {view.stations.get(destinationId!)?.name}
                </span>
              </header>
              <RouteResult view={view} result={activeRun.result} />
            </section>
          )}

          {activeRun.status === 'error' && (
            <section className="route-panel" role="alert">
              <header className="panel-heading">
                <h2>Route</h2>
              </header>
              <div className="route-error">{activeRun.message}</div>
            </section>
          )}

          <AlgorithmPanel
            view={view}
            runs={ALGORITHMS.map((descriptor) => {
              const state = runs[descriptor.key]
              return {
                key: descriptor.key,
                name: descriptor.name,
                objective: descriptor.objective,
                state: state.status,
                result: state.status === 'ready' ? state.result : null,
                error: state.status === 'error' ? state.message : undefined,
              }
            })}
            activeKey={activeKey}
            onActivate={(key) => onActivate(key as AlgorithmKey)}
          />
        </section>

        <section className="map-column">
          <StationMap
            view={view}
            selectedId={null}
            sourceId={sourceId}
            destinationId={destinationId}
            routeIds={activeRouteIds}
            onSelect={pickStation}
          />
        </section>

        <section className="stats-column">
          <StatisticsPanel
            view={view}
            raw={{
              totalStations: view.stations.size,
              totalConnections: Math.floor(view.edges.length / 2),
              totalLines: view.lineOrder.length,
            }}
            result={activeRun.status === 'ready' ? activeRun.result : null}
          />
        </section>
      </main>
    </div>
  )
}

/** Turn any {@link calculateRoute} failure into a user-friendly message. */
function friendlyRouteError(error: unknown): string {
  if (error instanceof RouteApiError && error.message) {
    return error.message
  }
  return 'The routing service could not be reached.'
}

export default App