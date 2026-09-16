import type { NetworkView } from '../network/graph'
import type { RouteResult } from '../network/routing'

/** One algorithm run as handed to the visualisation component. */
export interface AlgorithmRun {
  /** Stable key (e.g. {@code "bfs"}). */
  key: string
  /** Display name (e.g. {@code "BFS"}). */
  name: string
  /** Objective label (e.g. {@code "min hops"}). */
  objective: string
  /** The run's result — null until a route has been computed. */
  result: RouteResult | null
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
 * {@link RouteResult} documents it is handed, alongside each run's
 * exploration order (the order the <em>demo</em> router visited stations). It
 * makes no claim of executing the Java BFS/Dijkstra/A* implementations — those
 * live in the backend and are not yet connected (no REST API). The panel is
 * designed so a later backend integration can feed identically-shaped results
 * through the very same UI.</p>
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
        <span className="demo-tag">frontend demo — not the Java engine</span>
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

              {result && result.found ? (
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
                  <ExploredOrder result={result} view={view} />
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