/**
 * Derives navigable, frontend-ready views of the metro network from the raw
 * dataset in {@code types.ts}.
 *
 * <p><strong>Parsing is kept separate from visualisation.</strong> This module
 * turns the raw JSON records (stations / lines / connections keyed by ID) into
 * plain derived structures that the React components consume directly:</p>
 *
 * <ul>
 *   <li><b>{@link StationView}</b> — a station plus projected SVG coordinates
 *       ({@code x},{@code y}), so the map layer never does lat/lon math.</li>
 *   <li><b>{@link EdgeView}</b> — every graph edge. For a two-directional track
 *       a connection appears twice here (once per direction), mirroring how the
 *       underlying routers traverse the graph.</li>
 *   <li><b>Adjacency</b> — {@code stationId -> EdgeView[]}, the index used by
 *       the client-side demo routers.</li>
 *   <li><b>Per-line geometry</b> — each line's ordered station list resolved to
 *       projected coordinates, for drawing the colour-coded route polylines.</li>
 * </ul>
 *
 * <p>This module is pure TypeScript: no React, no DOM, no browser APIs. It is
 * also intentionally independent of the <em>routing</em> implementation — route
 * computation lives in {@code routing.ts} and only consumes the derived
 * adjacency produced here. The two never call each other.</p>
 */

import type {
  NetworkFile,
  StationData,
} from './types'

/** Ratio of padding added around the station extent when projecting. */
export const PADDING_RATIO = 0.08

/** Width of the SVG map viewport in user units. */
export const VIEW_WIDTH = 1000

/** Height of the SVG map viewport in user units. */
export const VIEW_HEIGHT = 800

/** A station resolved for rendering, including its projected coordinates. */
export interface StationView {
  id: string
  name: string
  latitude: number
  longitude: number
  x: number
  y: number
  interchange: boolean
  lines: string[]
}

/** A single navigable track edge as consumed by the routers. */
export interface EdgeView {
  from: string
  to: string
  line: string
  distanceKm: number
  travelTimeMinutes: number
}

/** A line resolved to its ordered, projected geometry for drawing. */
export interface LineGeometry {
  id: string
  name: string
  color: string
  lineId: string
  stationIds: string[]
  /** Projected ({@code [x, y]}) point for each station in {@code stationIds}. */
  points: Array<[number, number]>
}

/** The derived, frontend-facing view of the whole network. */
export interface NetworkView {
  name: string
  stations: Map<string, StationView>
  edges: EdgeView[]
  /** stationId -> list of incident edges (both directions). */
  adjacency: Map<string, EdgeView[]>
  /** lineId -> resolved geometry. */
  lines: Map<string, LineGeometry>
  lineOrder: string[]
  minLat: number
  maxLat: number
  minLon: number
  maxLon: number
}

/**
 * Build the derived {@link NetworkView} from a parsed {@link NetworkFile}.
 *
 * <p>The caller loads the JSON once (see {@code loadNetwork.ts}) and passes the
 * result here. Null or malformed input is rejected up front rather than producing
 * a half-populated view.</p>
 *
 * @throws TypeError if {@code file} is null or lacks the expected document shape
 */
export function buildNetworkView(file: NetworkFile): NetworkView {
  if (!file || !Array.isArray(file.stations) || !Array.isArray(file.connections)) {
    throw new TypeError('buildNetworkView: malformed network file')
  }

  const { minLat, maxLat, minLon, maxLon } = coordinateExtents(file.stations)
  const { x, y } = projection(minLat, maxLat, minLon, maxLon)

  const stations = new Map<string, StationView>()
  for (const s of file.stations) {
    stations.set(s.id, {
      id: s.id,
      name: s.name,
      latitude: s.latitude,
      longitude: s.longitude,
      x: x(s.longitude),
      y: y(s.latitude),
      interchange: s.interchange,
      lines: s.lines,
    })
  }

  const edges: EdgeView[] = []
  for (const c of file.connections) {
    edges.push({
      from: c.from,
      to: c.to,
      line: c.line,
      distanceKm: c.distanceKm,
      travelTimeMinutes: c.travelTimeMinutes,
    })
    // The network is bidirectional (a track is traversable both ways): mirror
    // each recorded connection so adjacency lists work for both directions.
    edges.push({
      from: c.to,
      to: c.from,
      line: c.line,
      distanceKm: c.distanceKm,
      travelTimeMinutes: c.travelTimeMinutes,
    })
  }

  const adjacency = new Map<string, EdgeView[]>()
  for (const e of edges) {
    pushAdjacency(adjacency, e.from, e)
  }

  const lineOrder: string[] = []
  const lines = new Map<string, LineGeometry>()
  for (const l of file.lines) {
    lineOrder.push(l.id)
    const stationIds = l.stations.filter((id) => stations.has(id))
    const points = stationIds
      .map((id) => stations.get(id))
      .filter((s): s is StationView => s !== undefined)
      .map((s) => [s.x, s.y] as [number, number])
    lines.set(l.id, {
      id: l.id,
      name: l.name,
      color: l.color,
      lineId: l.id,
      stationIds,
      points,
    })
  }

  return {
    name: file.network?.name ?? 'MetroMind',
    stations,
    edges,
    adjacency,
    lines,
    lineOrder,
    minLat,
    maxLat,
    minLon,
    maxLon,
  }
}

/** The smallest bounding box containing every station. */
function coordinateExtents(
  stations: StationData[],
): { minLat: number; maxLat: number; minLon: number; maxLon: number } {
  let minLat = Infinity
  let maxLat = -Infinity
  let minLon = Infinity
  let maxLon = -Infinity
  for (const s of stations) {
    minLat = Math.min(minLat, s.latitude)
    maxLat = Math.max(maxLat, s.latitude)
    minLon = Math.min(minLon, s.longitude)
    maxLon = Math.max(maxLon, s.longitude)
  }
  return { minLat, maxLat, minLon, maxLon }
}

/** Simple equal-area projection functions ({@code [lat, lon]} -> {@code [x, y]}). */
function projection(
  minLat: number,
  maxLat: number,
  minLon: number,
  maxLon: number,
): { x: (lng: number) => number; y: (lat: number) => number } {
  const xPad = (maxLon - minLon) * PADDING_RATIO
  const yPad = (maxLat - minLat) * PADDING_RATIO
  const lonMin = minLon - xPad
  const lonMax = maxLon + xPad
  const latMin = minLat - yPad
  const latMax = maxLat + yPad
  const xSpan = lonMax - lonMin || 1
  const ySpan = latMax - latMin || 1
  // Longitude grows eastwards (x); latitude grows northwards (y, inverted
  // because the SVG y-axis points down).
  const x = (lng: number): number => ((lng - lonMin) / xSpan) * VIEW_WIDTH
  const y = (lat: number): number => ((latMax - lat) / ySpan) * VIEW_HEIGHT
  return { x, y }
}

/** Append {@code edge} to {@code map} under {@code key}, creating the list if needed. */
function pushAdjacency(map: Map<string, EdgeView[]>, key: string, edge: EdgeView): void {
  const list = map.get(key)
  if (list) {
    list.push(edge)
  } else {
    map.set(key, [edge])
  }
}
