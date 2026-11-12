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
search index for fast, case-insensitive station lookup and prefix autocomplete, an
interactive React frontend that visualises the network on an SVG metro map with
station search, source/destination selection, route and algorithm panels, and
network statistics, and a Spring Boot REST API (`POST /api/routes`) that exposes
the routing algorithms to HTTP clients.

## Implemented Algorithms

- **BFS** — minimum station hops
- **Dijkstra** — minimum distance (`RouteMetric.DISTANCE`)
- **Dijkstra** — minimum travel time (`RouteMetric.TRAVEL_TIME`)
- **A\*** — minimum distance using a geographic (Haversine) heuristic
- **Trie** — case-insensitive station-name search and prefix autocomplete
- **Route visualization** (frontend) — interactive SVG metro map, station search,
  source/destination selection, route and algorithm panels, and network statistics
- **REST routing API** (backend) — `POST /api/routes` exposing BFS, Dijkstra, and A\*
  over HTTP (see [docs/rest-api.md](docs/rest-api.md))

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

MetroMind is a monorepo with two deployables.

```
Browser ──► Vercel (frontend, static)      Render (backend, Java service)
                 │ bundles data/metro-network.json │  POST /api/routes (BFS/Dijkstra/A*)
                 │ calls API via fetch()           │  GET /api/health
                 └─────────── API bridge ──────────┘
```

- **Frontend → Vercel.** Project root `frontend/`; Vercel is auto-configured by
  the repo-root [vercel.json](vercel.json). The network dataset is bundled into the
  build, so no backend-network-load is needed. The API base URL is configured via
  `VITE_API_BASE_URL`.
- **Backend → Render.** Prepared in [render.yaml](render.yaml) using Render's
  Java runtime. The server honors a platform-provided `PORT`
  (`server.port=${PORT:8080}`).
- **Current API status.** `GET /api/health` and the REST routing bridge
  `POST /api/routes` — request/response schemas, supported algorithms, and metrics
  are in [docs/rest-api.md](docs/rest-api.md). The frontend communicates with the
  backend through the `POST /api/routes` endpoint.

**Production build commands:**

```bash
cd frontend && npm run build && npm run lint   # → frontend/dist/
cd backend  && mvn clean package               # → backend/target/metromind-backend-0.1.0.jar
```

## Current Status

**Phase 11 — Frontend/Backend Integration**

The frontend is now fully integrated with the Spring Boot REST API. All routing
algorithms (BFS, Dijkstra, A*) are computed on the backend and fetched via
`POST /api/routes`.

**Phase 10 — Spring Boot REST Routing API**

The backend exposes its routing through a strongly typed REST bridge:
`POST /api/routes` accepts `{sourceId, destinationId, algorithm, metric}` and
returns the path computed by the real Java algorithms. See
[docs/rest-api.md](docs/rest-api.md).

**Phase 9 — Deployment & Production Readiness**

The project is deployment-ready: the frontend builds for production (static, to
be hosted on Vercel) and the backend builds a reproducible Spring Boot JAR
(prepared for Render). CORS is configured to allow the frontend to call the
backend API.

**Phase 8 — Metro Route Visualization**

The project contains an SVG metro map, station search, source/destination
selection, and algorithm/statistics panels, now backed by the real backend API.
