/**
 * The single, consistent frontend representation of a computed route, and the
 * small adapter that maps the Phase 10 backend response onto it.
 *
 * <p>The UI consumes {@link RouteResult} everywhere a route is shown; only
 * {@link mapRouteResponseToUiResult} ever builds one (from the real API
 * response). The {@code stationIds} come from the backend and match the dataset
 * IDs the loaded {@code NetworkView} keys by, so the map renders them directly.</p>
 */
import type { RouteAlgorithm, RouteMetric, RouteResponse } from './routes'

/**
 * The metric the UI labels a route with. It is a <em>presentation</em> label
 * derived from the algorithm that produced the route (BFS → hops, Dijkstra →
 * the requested metric, A* → distance) — never read off the wire for BFS/A*,
 * whose backend responses carry {@code metric: null}.
 */
export type UiMetric = 'hops' | 'distanceKm' | 'travelTimeMinutes'

/** Uniform, UI-facing route document consumed by the panels. */
export interface RouteResult {
  algorithm: RouteAlgorithm
  metric: UiMetric
  sourceId: string
  destinationId: string
  found: boolean
  stationIds: string[]
  hopCount: number
  totalDistanceKm: number
  totalTravelTimeMinutes: number
  /**
   * Station exploration order, if the backend provided one. The Phase 10 API
   * always sends an empty list, and no order is ever fabricated here.
   */
  explored: string[]
}

/**
 * Map the backend's {@link RouteResponse} onto the UI's {@link RouteResult}
 * without touching the wire values — stationIds, hopCount, and the totals are
 * echoed back exactly as the Java algorithms produced them.
 */
export function mapRouteResponseToUiResult(response: RouteResponse): RouteResult {
  return {
    algorithm: response.algorithm,
    metric: uiMetricOf(response.algorithm, response.metric),
    sourceId: response.sourceId,
    destinationId: response.destinationId,
    found: response.found,
    stationIds: response.stationIds,
    hopCount: response.hopCount,
    totalDistanceKm: response.totalDistanceKm,
    totalTravelTimeMinutes: response.totalTravelTimeMinutes,
    explored: response.explored,
  }
}

function uiMetricOf(algorithm: RouteAlgorithm, apiMetric: RouteMetric | null): UiMetric {
  switch (algorithm) {
    case 'BFS':
      return 'hops'
    case 'ASTAR':
      return 'distanceKm'
    case 'DIJKSTRA':
      return apiMetric === 'TRAVEL_TIME' ? 'travelTimeMinutes' : 'distanceKm'
  }
}