/**
 * The backend algorithms the frontend can ask for, mapped to the exact Phase 10
 * request values.
 *
 * <p>Each UI card is a stable {@link AlgorithmKey}; {@code algorithm} / {@code metric}
 * fields below mirror the backend enums exactly (case-sensitive). The metric is
 * meaningful only for Dijkstra — BFS and A* send {@code metric: null} per the
 * backend contract, and no invented metrics are introduced.</p>
 */
import type { RouteAlgorithm, RouteMetric, RouteRequest } from './routes'

/** The four route cards shown in the algorithm panel. */
export type AlgorithmKey = 'bfs' | 'dijkstra-distance' | 'dijkstra-time' | 'astar'

export interface AlgorithmDescriptor {
  key: AlgorithmKey
  name: string
  objective: string
  algorithm: RouteAlgorithm
  metric: RouteMetric | null
}

export const ALGORITHMS: AlgorithmDescriptor[] = [
  {
    key: 'bfs',
    name: 'BFS',
    objective: 'minimum hops',
    algorithm: 'BFS',
    metric: null,
  },
  {
    key: 'dijkstra-distance',
    name: 'Dijkstra (distance)',
    objective: 'minimum distance',
    algorithm: 'DIJKSTRA',
    metric: 'DISTANCE',
  },
  {
    key: 'dijkstra-time',
    name: 'Dijkstra (time)',
    objective: 'minimum travel time',
    algorithm: 'DIJKSTRA',
    metric: 'TRAVEL_TIME',
  },
  {
    key: 'astar',
    name: 'A*',
    objective: 'minimum distance (heuristic)',
    algorithm: 'ASTAR',
    metric: null,
  },
]

export const ALGORITHM_BY_KEY: Record<AlgorithmKey, AlgorithmDescriptor> = {
  bfs: ALGORITHMS[0],
  'dijkstra-distance': ALGORITHMS[1],
  'dijkstra-time': ALGORITHMS[2],
  astar: ALGORITHMS[3],
}

/** Build a ready-to-send request for a descriptor plus the chosen station pair. */
export function routeRequestFor(
  descriptor: AlgorithmDescriptor,
  sourceId: string,
  destinationId: string,
): RouteRequest {
  return {
    sourceId,
    destinationId,
    algorithm: descriptor.algorithm,
    metric: descriptor.metric,
  }
}