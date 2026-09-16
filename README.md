# MetroMind

Intelligent Metro Route Planner

## Overview

MetroMind is a full-stack web application that models a metro network as a weighted
graph and provides intelligent route planning. It combines graph-based pathfinding
algorithms with an interactive user interface to help commuters find the best routes
through a metro system.

The project currently contains a validated Delhi Metro dataset (42 stations, 2 lines,
41 connections), a graph representation built from that data, three routing algorithms
(BFS minimum hops, Dijkstra minimum distance or minimum travel time, and A* minimum
distance guided by a Haversine geographic heuristic), a Trie-based station-name
search index for fast, case-insensitive station lookup and prefix autocomplete, and
an interactive React frontend that visualises the network on an SVG metro map with
station search, source/destination selection, route and algorithm panels, and
network statistics.

## Implemented Algorithms

- **BFS** — minimum station hops
- **Dijkstra** — minimum distance (`RouteMetric.DISTANCE`)
- **Dijkstra** — minimum travel time (`RouteMetric.TRAVEL_TIME`)
- **A\*** — minimum distance using a geographic (Haversine) heuristic
- **Trie** — case-insensitive station-name search and prefix autocomplete
- **Route visualization** (frontend) — interactive SVG metro map, station search,
  source/destination selection, route and algorithm panels, and network statistics

## Planned Features

> These features are planned for future phases and are **not yet implemented**.

- **Cheapest routing** — minimum-fare path computation
- **Minimum-interchange routing** — routes that minimize line changes
- **Route comparison** — side-by-side evaluation of multiple route options
- **Service disruption handling** — dynamic rerouting around closed lines or stations

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | React, TypeScript, Vite |
| Backend | Java, Spring Boot, Maven |
| Algorithms | Graph Algorithms (BFS, Dijkstra, A\*), Trie Search |
| Data Format | JSON |

## Project Structure

```
MetroMind/
├── frontend/       React frontend (TypeScript + Vite)
├── backend/        Spring Boot backend (Java + Maven)
├── data/           Metro network data (JSON)
├── docs/           Documentation and diagrams
├── .gitignore
└── README.md
```

## Local Development

### Frontend

```bash
cd frontend
npm install
npm run dev        # Start development server
npm run build      # Production build
```

### Backend

```bash
cd backend
mvn spring-boot:run   # Start the server
mvn clean package     # Build the JAR
```

The backend runs at `http://localhost:8080`. Verify with:

```bash
curl http://localhost:8080/api/health
```

## Deployment

MetroMind is a monorepo with two deployables that are currently **independent**
— the frontend does not call the backend. Full details in
[docs/deployment.md](docs/deployment.md).

```
Browser ──► Vercel (frontend, static)      Render (backend, Java service)
                 │ bundles data/metro-network.json │  /api/health only
                 │ runs client-side demo routers   │  routing not exposed
                 └──────── no HTTP bridge ─────────┘
```

- **Frontend → Vercel.** Project root `frontend/`; Vercel is auto-configured by
  the repo-root [vercel.json](vercel.json) (`rootDirectory: "frontend"`,
  `buildCommand: "npm run build"`, `outputDirectory: "dist"`). The network
  dataset is bundled into the build, so **no environment variables are needed**.
- **Backend → Render.** Prepared in [render.yaml](render.yaml) using Render's
  Java runtime (`rootDir: backend`, `mvn clean package`, then
  `java -jar target/metromind-backend-0.1.0.jar`; Java 21, Spring Boot 3.5).
  The server honors a platform-provided `PORT` (`server.port=${PORT:8080}`).
- **Current API status: `GET /api/health` is the only endpoint.** There is no
  REST routing API, and the deployed frontend does not call the backend —
  its BFS / Dijkstra / A* views run the documented client-side demo routers.

**Production build commands:**

```bash
cd frontend && npm run build && npm run lint   # → frontend/dist/
cd backend  && mvn clean package               # → backend/target/metromind-backend-0.1.0.jar
```

## Current Status

**Phase 9 — Deployment & Production Readiness**

The project is deployment-ready: the frontend builds for production (static, to
be hosted on Vercel) and the backend builds a reproducible Spring Boot JAR
(prepared for Render). The two are currently **independent** — see the
[Deployment](#deployment) section and [docs/deployment.md](docs/deployment.md)
for details and for the honest listing of what is *not* wired up (no REST route
API, no frontend→backend bridge).

**Phase 8 — Metro Route Visualization**

The project now contains:
- A validated Delhi Metro dataset (42 stations, 2 lines, 41 connections)
- A graph representation built from that data (bidirectional weighted adjacency list)
- A BFS minimum-hop routing algorithm (Phase 4)
- A Dijkstra routing algorithm with two independent objectives (Phase 5):
  - minimum distance (kilometres)
  - minimum travel time (minutes)
- An A\* routing algorithm (Phase 6):
  - minimum distance, guided by the Haversine geographic heuristic
- A Trie-based station-name search index (Phase 7):
  - case-insensitive exact lookup and prefix search
  - deterministic, case-insensitive autocomplete suggestions
- An interactive React/TypeScript frontend (Phase 8):
  - SVG metro map with zoom, pan, and click-to-select (loads the shared
    `data/metro-network.json` at build time — single source of truth)
  - dataset-backed station search with autocomplete (no Trie reimplementation)
  - source/destination selection with swap and reset
  - route panel: sequence, hops, distance, travel time
  - reusable algorithm-visualization UI for BFS / Dijkstra / A*
  - network statistics panel (stations, connections, lines, interchange, route)

> **Frontend routing caveat.** The BFS / Dijkstra / A* results drawn in the
> frontend come from clearly separated, documented client-side *demo* routers
> (`frontend/src/network/routing.ts`), not from the Java implementations — no
> REST API exists yet, so the backend algorithms are not reachable from the
> browser. When a real backend bridge is added in a later phase, the same
> uniform `RouteResult` shape means the visualization UI needs no changes.

No REST route API or backend↔frontend integration is implemented yet.
