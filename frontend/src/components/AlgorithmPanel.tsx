import type { NetworkView } from '../network/graph'
import type { RouteResult } from '../api/routeResult'

/** One algorithm run as handed to the visualisation component. */
export interface AlgorithmRun {
  /** Stable key (e.g. {@code "bfs"}). */
  key: string
  /** Display name (e.g. {@code "BFS"}). */
  name: string
  /** Objective label (e.g. {@code "min hops"}). */
  objective: string
  /** Lifecycle of this run's query against the backend. */
  state: 'idle' | 'loading' | 'ready' | 'error'
  /** The run's result — only when {@code state} is {@code "ready"}. */
  result: RouteResult | null
  /** Friendly message — only when {@code state} is {@code "error"}. */
  error?: string
}

interface AlgorithmPanelProps {
  view: NetworkView
  runs: AlgorithmRun[]
  /** Key of the algorithm currently selected for the large map/highlight. */
  activeKey: string
  onActivate: (key: string) => void
}

const EXPLORED_CAP = 12

/**
 * Reusable algorithm-visualisation panel.
 *
 * <p>This is <b>structure, not computation</b>: it renders whatever
 * {@link RouteResult} it is handed. Results come from the live backend routing
 * API (Phase 10 {@code POST /api/routes}), which runs the real Java
 * BFS / Dijkstra / A* implementations. Because the API does not expose an
 * exploration <em>order</em>, the {@code explored} strip renders only when a
 * result actually carries one — none is fabricated.</p>
 */
export default function AlgorithmPanel({
  view,
  runs,
  activeKey,
  onActivate,
}: AlgorithmPanelProps) {
  return (
    <section className="algorithm-panel" aria-label="Algorithm visualization">
      <header className="panel-heading">
        <h2>Algorithm visualization</h2>
        <span className="api-tag">live backend routing API</span>
      </header>
      <div className="algorithm-runs">
        {runs.map((run) => {
          const isActive = run.key === activeKey
          const result = run.result
          return (
            <article
              key={run.key}
              className={`algorithm-run${isActive ? ' active' : ''}`}
              onClick={() => onActivate(run.key)}
              role="button"
              tabIndex={0}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  onActivate(run.key)
                  event.preventDefault()
                }
              }}
            >
              <header className="algorithm-run-header">
                <h3>{run.name}</h3>
                <span className="algorithm-objective">{run.objective}</span>
              </header>

              {run.state === 'loading' ? (
                <p className="algorithm-loading">Calculating route with the backend…</p>
              ) : run.state === 'error' ? (
                <p className="algorithm-error">{run.error}</p>
              ) : result && result.found ? (
                <>
                  <p className="algorithm-path">
                    {result.stationIds
                      .map((id) => view.stations.get(id)?.name ?? id)
                      .join(' → ')}
                  </p>
                  <div className="algorithm-stats">
                    <span>{result.hopCount} hops</span>
                    <span>{result.totalDistanceKm.toFixed(1)} km</span>
                    <span>{result.totalTravelTimeMinutes.toFixed(1)} min</span>
                  </div>
                  {result.explored.length > 0 && (
                    <ExploredOrder result={result} view={view} />
                  )}
                </>
              ) : result ? (
                <p className="algorithm-no-path">No route found</p>
              ) : (
                <p className="algorithm-pending">Pick both a start and a destination</p>
              )}
            </article>
          )
        })}
      </div>
    </section>
  )
}

/** A compact strip of the first few explored stations (in visit order). */
function ExploredOrder({
  result,
  view,
}: {
  result: RouteResult
  view: NetworkView
}) {
  const shown = result.explored.slice(0, EXPLORED_CAP)
  const truncated = result.explored.length > EXPLORED_CAP
  return (
    <div className="explored-strip">
      <span className="explored-label">explored:</span>
      {shown.map((id, i) => (
        <span key={id} className={`explored-station${i === 0 ? ' start' : ''}`}>
          {view.stations.get(id)?.name ?? id}
        </span>
      ))}
      {truncated && <span className="explored-more">…</span>}
    </div>
  )
}