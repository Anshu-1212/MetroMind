# MetroMind — Metro Route Visualization

This document covers MetroMind's first frontend layer (Phase 8): the interactive
metro map, station search and selection, route and algorithm visualisation, and
the network statistics panel. It explains the frontend's relationship to the
backend and to the shared dataset, and — importantly — what the frontend does
**not** do yet.

## 1. Goals of this Phase

Phase 8 turns the validated Phase 2 dataset into something a commuter can see and
interact with. Before this phase MetroMind could *compute* routes (backend BFS /
Dijkstra / A* and the Phase 7 Trie) but had no way to *show* them. This phase
builds the first usable visualisation layer in the existing React + TypeScript +
Vite frontend:

- an **interactive metro map** (SVG) with zoom and pan,
- **station search / autocomplete** over the real station names,
- **source / destination selection** with swap and reset,
- **route visualisation** — sequence, hops, distance, and travel time,
- a reusable **algorithm-visualisation structure** for BFS / Dijkstra / A*,
- a **statistics panel** for the network and the current route.

## 2. Architecture & data flow

```
data/metro-network.json   (single source of truth, unchanged)
        │  imported at build time (Vite bundles the JSON)
        ▼
src/network/types.ts      raw JSON contracts (NetworkFile, StationData, …)
        │
src/network/graph.ts      buildNetworkView → derived NetworkView
        │                   (projected coordinates, edges, adjacency, line geometry)
src/network/loadNetwork.ts entry point used once by App
        │
src/network/routing.ts    BFS / Dijkstra / A* demo routers → RouteResult
        │
src/components/*          presentational React components (map, search, panels)
```

The split is deliberate and matches the project's layering rules:

- **Parsing is separate from visualisation.** The React components never read JSON
  keys; they consume the derived `NetworkView` produced by `graph.ts`.
- **Routing is separate from rendering.** The routers (`routing.ts`) consume only
  the adjacency inside `NetworkView` and return a uniform `RouteResult`. The
  panels just render that result.
- **The dataset is single source of truth.** `loadNetwork.ts` imports
  `data/metro-network.json` directly; the frontend holds no duplicate copy.

## 3. The interactive map

`StationMap.tsx` renders an SVG viewBox of `1000 × 800` units.

- Every **station** is a circle placed by an equal-area projection of its real
  latitude/longitude (north-up, with `PADDING_RATIO` padding around the extent).
  Interchange stations are drawn larger.
- Every **connection** from the dataset is a segment stroked in its line's colour
  (read from `metro-network.json`). IDs and names are preserved verbatim.
- **Zoom / pan**: wheel zoom-in on the pointer, drag-to-pan, and the `+` / `−` /
  `Reset view` controls.
- **Click-to-select**: clicking a node picks it; the app assigns it as the
  departure station, then as the destination on the second click.
- The currently inspected route is drawn as a translucent amber overlay.

## 4. Station search

`StationSearch.tsx` is a keyboard-friendly autocomplete box fed the real station
names. Matching is a simple, case-insensitive substring filter.

> This is **not** a reimplementation of the Phase 7 backend `StationTrie` and does
> not pretend to be one: the Trie remains the authoritative station-name index,
> and there is no browser↔backend bridge to it yet. For 42 stations a linear,
> case-insensitive filter on every keystroke is fast and honest. When a real API
> exists, the same component can be re-pointed at Trie-backed results.

## 5. Source / destination selection

`SourceDestinationBar.tsx` wires two search boxes to the app state and exposes
**swap** (`⇅`) and **reset**. Route computation is not started by the bar itself;
the parent (App) runs the demo routers as soon as both ends are chosen.

## 6. Route visualisation

`RouteResult.tsx` renders a single `RouteResult`: hop count, total distance (km),
total travel time (min), the metric badge, and an ordered station list — the
departure dot is green, the arrival dot is blue, and each step shows the line it
travels on. It is purely presentational and reusable: the same component will
render backend-produced results verbatim once a bridge exists.

## 7. Algorithm visualisation (BFS / Dijkstra / A*)

`AlgorithmPanel.tsx` is a reusable *structure*, not a computation. Callers hand it
a list of `AlgorithmRun` documents (`name`, `objective`, `RouteResult | null`);
the panel shows one card per algorithm with the found route, its totals, and the
order the run explored stations (`explored` strip). Clicking a card activates it
for the large map overlay.

**Honesty constraint.** The results shown come from the *frontend demo* routers in
`routing.ts` — plain TypeScript ports of BFS / Dijkstra / A* that run in the
browser. They are **not** the Java implementations in the backend
(`com.metromind.routing`), which are not yet reachable from the frontend (no REST
API). This is stated in the UI ("frontend demo — not the Java engine") and is the
whole point of the uniform `RouteResult` shape: when a real backend integration
lands, the exact same panel renders its output with no UI changes.

## 8. Statistics panel

`StatisticsPanel.tsx` shows network totals — stations, connections, lines, and the
number of interchange stations (all derived from the real data) — plus the current
route's hops, distance, travel time, and which algorithm produced it.

## 9. Responsive design

The layout is a CSS grid: controls column + map column + statistics column. It
collapses to two columns at ≤ 1200 px (statistics drops below) and to a single
vertical stack at ≤ 820 px with the map remaining primary. Colors use CSS custom
properties with a `prefers-color-scheme` dark theme.

## 10. Verification

- Frontend: `npm run build` (TypeScript `tsc -b` + Vite) exits 0 and produces
  `dist/`. `npm run lint` (oxlint) is clean.
- Backend unchanged: `mvn clean test` stays at **177 tests, 0 failures**
  (all of Phases 1–7 preserved).
- The demo routing was smoke-tested against the real JSON (Node):
  `Rajiv Chowk → New Delhi` = 1 hop / 1.2 km / 2 min; a cross-line trip
  `Samaypur Badli → Dwarka Sector 21` = 33 hops / 57 km / 96 min via the Rajiv
  Chowk interchange.

## 11. Limitations

- **No backend connection.** The algorithms visualised are frontend demo ports,
  not the Java engine (see §7). No REST API is implemented or mocked.
- **No route animation frames per middle step** — the exploration order is shown
  as an ordered strip rather than an animated sequence.
- **Straight-line edges.** Connections are drawn as straight segments; there is no
  schematic/curved line rendering yet.
- **Name-selecting interaction** uses two search boxes plus map clicks; there is
  no drag-to-route gesture.
- **No maps, live data, deployment, or login** — all out of scope for this phase.

## 12. Summary

Phase 8 gives MetroMind its first usable visual frontend on top of the real Delhi
Metro dataset: an interactive map, dataset-backed station search, source /
destination selection, route and algorithm visualisation, and network statistics —
all modular, responsive, and with the frontend's demo routers clearly separated
from the Java routing engine until a real integration exists.