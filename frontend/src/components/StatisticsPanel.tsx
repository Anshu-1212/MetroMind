import type { NetworkView } from '../network/graph'
import type { RouteResult } from '../network/routing'

interface StatisticsPanelProps {
  view: NetworkView
  raw: {
    totalStations: number
    totalLines: number
    totalConnections: number
  }
  /** The currently inspected route (may be an unfound result). */
  result: RouteResult | null
}

/**
 * Network and route statistics.
 *
 * <p>Totals come straight from the shared dataset metadata (single source of
 * truth); route figures from the caller-supplied {@link RouteResult}. Pure and
 * presentational.</p>
 */
export default function StatisticsPanel({ view, raw, result }: StatisticsPanelProps) {
  const interchangeCount = Array.from(view.stations.values()).filter(
    (s) => s.interchange,
  ).length

  return (
    <section className="statistics-panel" aria-label="Statistics">
      <header className="panel-heading">
        <h2>Statistics</h2>
      </header>

      <dl className="stat-grid">
        <Stat label="Total stations" value={raw.totalStations} />
        <Stat label="Total connections" value={raw.totalConnections} />
        <Stat label="Total lines" value={raw.totalLines} />
        <Stat label="Interchange stations" value={interchangeCount} />
      </dl>

      <h3 className="stat-subheading">Current route</h3>
      <dl className="stat-grid">
        {result && result.found ? (
          <>
            <Stat label="Hops" value={`${result.hopCount}`} />
            <Stat label="Distance" value={`${result.totalDistanceKm.toFixed(1)} km`} />
            <Stat label="Travel time" value={`${result.totalTravelTimeMinutes.toFixed(1)} min`} />
            <Stat label="Algorithm" value={result.algorithm} />
          </>
        ) : (
          <div className="stat-none">
            Select a start and a destination to see route statistics.
          </div>
        )}
      </dl>
    </section>
  )
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="stat">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}