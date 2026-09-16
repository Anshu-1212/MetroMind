/**
 * Client-side demo routers (BFS / Dijkstra / A*).
 *
 * These are a React-independent frontend convenience: they re-express the same
 * three algorithms from the backend in TypeScript so the Phase 8 visualisation
 * has honest route data to draw. They do NOT claim to execute the Java
 * implementations — there is no REST bridge yet (a later phase). Each router
 * consumes the derived {@link NetworkView} and returns a uniform
 * {@link RouteResult} plus the order stations were explored, so the UI renders
 * one structure regardless of which algorithm ran.
 */

import type { NetworkView } from './graph'

export type RouteMetric = 'hops' | 'distanceKm' | 'travelTimeMinutes'

export interface RouteDemand {
  sourceId: string
  destinationId: string
}

export interface RouteResult {
  algorithm: string
  metric: RouteMetric
  sourceId: string
  destinationId: string
  found: boolean
  stationIds: string[]
  hopCount: number
  totalDistanceKm: number
  totalTravelTimeMinutes: number
  /** Station IDs in the order they were explored (for animation). */
  explored: string[]
}

function notFound(
  algorithm: string,
  metric: RouteMetric,
  sourceId: string,
  destinationId: string,
  explored: string[],
): RouteResult {
  return {
    algorithm,
    metric,
    sourceId,
    destinationId,
    found: false,
    stationIds: [],
    hopCount: 0,
    totalDistanceKm: 0,
    totalTravelTimeMinutes: 0,
    explored,
  }
}

/** BFS — route with the fewest hops (unweighted). */
export function bfs(view: NetworkView, demand: RouteDemand): RouteResult {
  return bfsInner(view, demand.sourceId, demand.destinationId, 'BFS')
}

function bfsInner(
  view: NetworkView,
  sourceId: string,
  destinationId: string,
  algorithm: string,
): RouteResult {
  if (!view.stations.has(sourceId) || !view.stations.has(destinationId)) {
    return notFound(algorithm, 'hops', sourceId, destinationId, [])
  }
  const explored: string[] = []
  const previous = new Map<string, string>()
  const queue = [sourceId]
  const visited = new Set<string>([sourceId])
  while (queue.length > 0) {
    const current = queue.shift()!
    explored.push(current)
    if (current === destinationId) {
      const path = reconstruct(destinationId, previous)
      return summarize(view, algorithm, 'hops', sourceId, destinationId, path, explored)
    }
    const neighbors = view.adjacency.get(current) ?? []
    for (const edge of neighbors) {
      if (!visited.has(edge.to)) {
        visited.add(edge.to)
        previous.set(edge.to, current)
        queue.push(edge.to)
      }
    }
  }
  return notFound(algorithm, 'hops', sourceId, destinationId, explored)
}

/** Dijkstra — route minimising {@code distanceKm} or {@code travelTimeMinutes}. */
export function dijkstra(
  view: NetworkView,
  demand: RouteDemand,
  metric: 'distanceKm' | 'travelTimeMinutes',
): RouteResult {
  return dijkstraInner(view, demand.sourceId, demand.destinationId, metric, 'Dijkstra')
}

function dijkstraInner(
  view: NetworkView,
  sourceId: string,
  destinationId: string,
  metric: 'distanceKm' | 'travelTimeMinutes',
  algorithm: string,
): RouteResult {
  if (!view.stations.has(sourceId) || !view.stations.has(destinationId)) {
    return notFound(algorithm, metric, sourceId, destinationId, [])
  }
  const explored: string[] = []
  const best = new Map<string, number>([[sourceId, 0]])
  const previous = new Map<string, string>()
  const open = new Set<string>([sourceId])

  while (open.size > 0) {
    let current = ''
    let currentCost = Infinity
    for (const id of open) {
      const cost = best.get(id) ?? Infinity
      if (cost < currentCost) {
        currentCost = cost
        current = id
      }
    }
    open.delete(current)
    explored.push(current)
    if (current === destinationId) {
      const path = reconstruct(destinationId, previous)
      return summarize(view, algorithm, metric, sourceId, destinationId, path, explored)
    }
    const neighbors = view.adjacency.get(current) ?? []
    for (const edge of neighbors) {
      const weight = metric === 'distanceKm' ? edge.distanceKm : edge.travelTimeMinutes
      const candidate = currentCost + weight
      if (candidate < (best.get(edge.to) ?? Infinity)) {
        best.set(edge.to, candidate)
        previous.set(edge.to, current)
        open.add(edge.to)
      }
    }
  }
  return notFound(algorithm, metric, sourceId, destinationId, explored)
}

/** A* — shortest distance using the Haversine straight-line heuristic. */
export function aStar(view: NetworkView, demand: RouteDemand): RouteResult {
  if (!view.stations.has(demand.sourceId) || !view.stations.has(demand.destinationId)) {
    return notFound('A*', 'distanceKm', demand.sourceId, demand.destinationId, [])
  }
  const sourceId = demand.sourceId
  const destinationId = demand.destinationId
  const goal = view.stations.get(destinationId)!
  const explored: string[] = []
  const gScore = new Map<string, number>([[sourceId, 0]])
  const previous = new Map<string, string>()
  const open: string[] = [sourceId]
  const fScore = new Map<string, number>([[sourceId, haversine(view, sourceId, goal)]])

  while (open.length > 0) {
    let bestIndex = 0
    for (let i = 1; i < open.length; i++) {
      if ((fScore.get(open[i]) ?? Infinity) < (fScore.get(open[bestIndex]) ?? Infinity)) {
        bestIndex = i
      }
    }
    const current = open.splice(bestIndex, 1)[0]
    explored.push(current)
    if (current === destinationId) {
      const path = reconstruct(destinationId, previous)
      return summarize(view, 'A*', 'distanceKm', sourceId, destinationId, path, explored)
    }
    const gCurrent = gScore.get(current) ?? Infinity
    const neighbors = view.adjacency.get(current) ?? []
    for (const edge of neighbors) {
      const tentative = gCurrent + edge.distanceKm
      if (tentative < (gScore.get(edge.to) ?? Infinity)) {
        gScore.set(edge.to, tentative)
        previous.set(edge.to, current)
        fScore.set(edge.to, tentative + haversine(view, edge.to, goal))
        if (!open.includes(edge.to)) {
          open.push(edge.to)
        }
      }
    }
  }
  return notFound('A*', 'distanceKm', sourceId, destinationId, explored)
}

/** Walk the parent chain from {@code node} back to the start. */
function reconstruct(node: string, previous: Map<string, string>): string[] {
  const path = [node]
  let cursor = node
  while (previous.has(cursor)) {
    cursor = previous.get(cursor)!
    path.push(cursor)
  }
  return path.reverse()
}

/** Fill hop/distance/time totals for a found {@code path}. */
function summarize(
  view: NetworkView,
  algorithm: string,
  metric: RouteMetric,
  sourceId: string,
  destinationId: string,
  path: string[],
  explored: string[],
): RouteResult {
  let distance = 0
  let time = 0
  for (let i = 1; i < path.length; i++) {
    const edge = findEdge(view, path[i - 1], path[i])
    if (edge) {
      distance += edge.distanceKm
      time += edge.travelTimeMinutes
    }
  }
  return {
    algorithm,
    metric,
    sourceId,
    destinationId,
    found: true,
    stationIds: path,
    hopCount: path.length - 1,
    totalDistanceKm: distance,
    totalTravelTimeMinutes: time,
    explored,
  }
}

/** Find the edge from {@code a} to {@code b} in the adjacency. */
function findEdge(view: NetworkView, a: string, b: string) {
  return (view.adjacency.get(a) ?? []).find((e) => e.to === b)
}

/** Great-circle (straight-line) distance from {@code fromId} to {@code to} in km. */
function haversine(view: NetworkView, fromId: string, to: { latitude: number; longitude: number }): number {
  const from = view.stations.get(fromId)
  if (!from) return 0
  const toRad = (deg: number): number => (deg * Math.PI) / 180
  const dLat = toRad(to.latitude - from.latitude)
  const dLon = toRad(to.longitude - from.longitude)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(from.latitude)) * Math.cos(toRad(to.latitude)) * Math.sin(dLon / 2) ** 2
  return 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(a)))
}