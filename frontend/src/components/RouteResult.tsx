import type { NetworkView } from '../network/graph'
import type { RouteResult } from '../api/routeResult'

interface RouteResultProps {
  view: NetworkView
  result: RouteResult
}

/**
 * Displays a route — its station sequence and the journey totals — for a single
 * {@link RouteResult}. Rendered for whichever algorithm is being inspected and
 * given a "primary" treatment in the route panel.
 *
 * <p>The component is purely presentational: it decides nothing, spawns no
 * routes, and renders whatever {@link RouteResult} the caller produced from the
 * backend routing API.</p>
 */
export default function RouteResult({ view, result }: RouteResultProps) {
  const stations = result.stationIds
    .map((id) => view.stations.get(id))
    .filter((s) => s !== undefined)

  if (!result.found || stations.length === 0) {
    return (
      <div className="route-no-result">
        No route found between those stations.
      </div>
    )
  }

  const metricLabel =
    result.metric === 'hops'
      ? 'hops'
      : result.metric === 'distanceKm'
        ? 'distance'
        : 'travel time'

  return (
    <div className="route-result">
      <div className="route-metrics">
        <span className="route-metric">
          <strong>{stations.length - 1}</strong> hops
        </span>
        <span className="route-metric">
          <strong>{round(result.totalDistanceKm)}</strong> km
        </span>
        <span className="route-metric">
          <strong>{round(result.totalTravelTimeMinutes)}</strong> min
        </span>
        <span className="route-metric-badge">{metricLabel}</span>
      </div>

      <ol className="route-steps">
        {stations.map((station, index) => {
          const next = stations[index + 1]
          const edge = next
            ? (view.adjacency.get(station.id) ?? []).find((e) => e.to === next.id)
            : undefined
          const colour = edge ? view.lines.get(edge.line)?.color : undefined
          return (
            <li key={station.id} className="route-step">
              <span
                className="route-step-dot"
                style={{ borderColor: colour, background: index === 0 ? '#16a34a' : index === stations.length - 1 ? '#2563eb' : '#ffffff' }}
              />
              <span className="route-step-name">{station.name}</span>
              {edge && (
                <span className="route-step-edge">
                  {edge.line}
                </span>
              )}
            </li>
          )
        })}
      </ol>
    </div>
  )
}

function round(value: number): string {
  return value.toFixed(1)
}