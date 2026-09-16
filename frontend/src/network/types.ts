/**
 * TypeScript contracts that mirror the raw dataset stored at
 * {@code data/metro-network.json} — the single source of truth for MetroMind.
 *
 * <p>The frontend imports that JSON file directly at build time (Vite bundles it
 * into the compiled JS), so there is exactly one copy of the network data in the
 * repo. These interfaces describe the file's shape so the rest of the frontend
 * can work against typed data.</p>
 *
 * <p>Responsibility split: these types document the raw file, the routines in
 * {@code graph.ts} derive a navigable graph from it, and the React components
 * render that graph. The components never read JSON keys directly.</p>
 */

/** Metadata block ({@code network}) at the top of the file. */
export interface NetworkMetadata {
  name: string
  version: string
  description: string
  totalStations: number
  totalLines: number
  totalConnections: number
}

/** A single station as stored in the dataset. */
export interface StationData {
  id: string
  name: string
  latitude: number
  longitude: number
  lines: string[]
  interchange: boolean
}

/** A metro line as stored in the dataset. */
export interface LineData {
  id: string
  name: string
  color: string
  stations: string[]
}

/** A single track connection between two adjacent stations. */
export interface ConnectionData {
  from: string
  to: string
  line: string
  distanceKm: number
  travelTimeMinutes: number
}

/** The complete {@code metro-network.json} document structure. */
export interface NetworkFile {
  network: NetworkMetadata
  stations: StationData[]
  lines: LineData[]
  connections: ConnectionData[]
}
