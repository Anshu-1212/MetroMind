# MetroMind — Metro Route Visualization

This document covers MetroMind's frontend layer: the interactive metro map, station search
and selection, route and algorithm visualization, and the network statistics panel.
As of **Phase 11**, the frontend is fully integrated with the Spring Boot backend API.

## 1. Goals of this Phase

Phase 8 established the visual interface, and Phase 11 connected it to the real Java
routing engine. The frontend provides:

- an **interactive metro map** (SVG) with zoom and pan,
- **station search / autocomplete** over the real station names,
- **source / destination selection** with swap and reset,
- **route visualization** — sequence, hops, distance, and travel time,
- a reusable **algorithm-visualization structure** for BFS / Dijkstra / A*,
- a **statistics panel** for the network and the current route.

## 2. Architecture & data flow

```
data/metro-network.json   (single source of truth, unchanged)
        │  imported at build time (Vite bundles the JSON)
        ▼
src/network/types.ts      raw JSON contracts (NetworkFile, StationData, …)
        │
src/network/graph.ts      buildNetworkView → derived NetworkView
        │
src/api/routes.ts         REST API client (POST /api/routes)
        │
src/components/*          presentational React components
```

The split is deliberate and matches the project's layering rules:

- **Parsing is separate from visualization.** The React components never read JSON
  keys; they consume the derived `NetworkView` produced by `graph.ts`.
- **Routing is performed on the backend.** The frontend sends requests to the
  Spring Boot API and renders the returned `RouteResult`.
- **The dataset is single source of truth.** `loadNetwork.ts` imports
  `data/metro-network.json` directly; the frontend holds no duplicate copy.

## 3. The interactive map

`StationMap.tsx` renders an SVG viewBox of `1000 × 800` units.

- Every **station** is a circle placed by an equal-area projection of its real
  latitude/longitude. Interchange stations are drawn larger.
- Every **connection** from the dataset is a segment stroked in its line's color.
- **Zoom / pan**: wheel zoom-in on the pointer, drag-to-pan, and controls.
- **Click-to-select**: clicking a node picks it as departure or destination.
- The route returned by the API is drawn as a translucent amber overlay.

## 4. Station search

`StationSearch.tsx` is an autocomplete box fed the real station names. Matching is a
case-insensitive substring filter.

## 5. Source / destination selection

`SourceDestinationBar.tsx` wires search boxes to the app state and exposes
**swap** and **reset**. The parent (App) issues an API request as soon as both ends are chosen.

## 6. Route visualization

`RouteResult.tsx` renders a single `RouteResult` returned by the backend: hop count,
total distance (km), total travel time (min), and an ordered station list.

## 7. Algorithm visualization (BFS / Dijkstra / A*)

`AlgorithmPanel.tsx` displays one card per algorithm. When a route is calculated,
the panel shows the found route, its totals, and status. Results come from the **live
backend routing API** (`POST /api/routes`), which executes the real Java
BFS / Dijkstra / A* implementations.

## 8. Statistics panel

`StatisticsPanel.tsx` shows network totals derived from the real data, plus the current
route's metrics and algorithm name.

## 9. Responsive design

The layout uses a CSS grid that collapses gracefully for smaller viewports. Colors use
CSS custom properties with a `prefers-color-scheme` dark theme.

## 10. Verification

- Frontend: `npm run build` and `npm run lint` pass cleanly.
- Backend: `mvn clean test` passes (202 tests).
- Live Integration: `verify-routes-api.mjs` confirms that the frontend client correctly
  communicates with the Spring Boot API for all algorithms.

## 11. Summary

MetroMind now features a fully integrated frontend and backend. The interactive map
provides a visualization of routes computed by the authoritative Java engine, with
support for multiple algorithms and metrics. Local development and production
deployment are configured via environment variables for the API base URL and CORS
origins.
