# MetroMind — Deployment & Production Readiness

Phase 9 covers deployment configuration for both halves of the monorepo. This
document says exactly what is deployed, how to deploy each part, and — just as
importantly — what is **not** wired together yet. Phase 10 added a backend REST
routing bridge (`POST /api/routes`, see [rest-api.md](rest-api.md)); the
**frontend still does not call the backend**, so the two deployables remain
independent from the browser's point of view.

## 1. Architecture

```
                       ┌────────────────────────────┐
                       │  Vercel (static hosting)   │
        Browser  ◄────►│  React + TypeScript + Vite │
                       │  bundles data/metro-       │
                       │  network.json at build     │
                       └────────────────────────────┘
                                  ▲
                                  │  (no HTTP calls today)
                                  │
                       ┌────────────────────────────┐
                       │  Render (Java service)     │
  GET /api/health  ◄───┤  Spring Boot 3.5 · Java 21 │
  POST /api/routes ◄───┤  REST bridge (Phase 10)    │
                       └────────────────────────────┘
```

Two independent deployables share one Git repository:

- **Frontend** — a static single-page app (React + TypeScript + Vite). It loads
  the network directly from the bundled `data/metro-network.json` and runs
  **client-side demo routers** (`frontend/src/network/routing.ts`) for
  BFS / Dijkstra / A*. It needs **no server and no environment variables**.
- **Backend** — a Spring Boot service. It exposes `GET /api/health` and the
  Phase 10 REST routing bridge, `POST /api/routes`, which dispatches to the
  **real** Java routing algorithms (BFS, Dijkstra, A*) over the same dataset.

The **frontend is not wired to the backend**: the deployed frontend does not call
the API and keeps its client-side demo routers. The backend bridge exists and is
fully tested; frontend↔API integration is reserved for a later phase (see
`docs/route-visualization.md`).

## 2. Frontend — Vercel

The Vercel project root is the `frontend/` directory. A minimal
repo-root [`vercel.json`](../vercel.json) pins the settings so no dashboard
configuration is required.

| Setting            | Value            |
|--------------------|------------------|
| Framework          | Vite (autodetected) |
| Project root       | `frontend/`      |
| Install command    | `npm install` (default) |
| Build command      | `npm run build`  |
| Output directory   | `dist`           |
| Node.js            | Vite 8 requires Node `^20.19.0` or `>=22.12.0` (default Vercel Node works) |
| Environment variables | none required |

`vercel.json`:

```json
{
  "$schema": "https://openapi.vercel.sh/vercel.json",
  "rootDirectory": "frontend",
  "buildCommand": "npm run build",
  "outputDirectory": "dist"
}
```

How the data reaches production: `frontend/src/network/loadNetwork.ts` imports
`data/metro-network.json` (single source of truth), and Vite inlines it into the
JS bundle at build time. The deployed `dist/` therefore **contains the full
network** — no fetching, no API, no CDN dependency. Verify this locally by
searching the built bundle for a station name (e.g. `Rajiv Chowk`).

## 3. Backend — Render

The backend is prepared in [`render.yaml`](../render.yaml) using Render's native
Java runtime and the repository's existing Maven structure — no Dockerfile, no
database, no extra infrastructure.

```yaml
services:
  - type: web
    name: metromind-backend
    runtime: java
    rootDir: backend
    buildCommand: mvn clean package
    startCommand: java -jar target/metromind-backend-0.1.0.jar
    healthCheckPath: /api/health
```

| Concern            | Value                                              |
|--------------------|----------------------------------------------------|
| Runtime            | Java (Maven)                                       |
| Java version       | 21 (`<java.version>21</java.version>` in `pom.xml`)|
| Build              | `mvn clean package` (Spring Boot fat JAR)          |
| Start              | `java -jar target/metromind-backend-0.1.0.jar`     |
| Port               | `server.port=${PORT:8080}` — Render's `PORT` env is honored; defaults to `8080` locally |
| Health check       | `GET /api/health` → `200 {"status":"ok", ...}`     |

Reproducible build: no build steps depend on localhost, the machine, or a
database. `mvn clean test` is runnable on any machine with JDK 21 + Maven.

## 4. Environment variables

- **Frontend:** none. The dataset is bundled, not fetched. No `VITE_*`
  variables are read, so no `.env`/`.env.example` is needed.
- **Backend:** none required. `PORT` is optional and defaults to `8080`.
  `app.cors.allowed-origins` is optional and defaults to the Vite dev origin
  (`http://localhost:5173`); set it to the deployed frontend's origin when the
  frontend starts calling the API.

If future phases introduce `VITE_*` variables, Vercel supports them through its
environment-variable UI; a `.env.example` would be added then.

## 5. Current backend API status

- `GET /api/health` — health check. Returns service name/status.
- `POST /api/routes` — **the Phase 10 routing bridge.** Parses
  `{sourceId, destinationId, algorithm, metric}`, validates the request, and runs
  the **real** Java BFS / Dijkstra / A* routers over the single
  `data/metro-network.json`. Full request/response/error contract:
  [rest-api.md](rest-api.md).
- CORS: per-origin allowlist via `app.cors.allowed-origins` (default
  `http://localhost:5173`), methods `GET`/`POST`, mapped to `/api/**`. No
  permissive wildcard.
- The **frontend does not call any backend endpoint** yet — this is deliberate
  and documented.

## 6. Local workflow

Local development and production builds are unchanged from Phase 1–8:

```bash
# Frontend
cd frontend
npm install           # installs dev + prod dependencies
npm run dev           # dev server
npm run build         # production build -> dist/
npm run lint          # oxlint

# Backend
cd backend
mvn spring-boot:run   # starts on http://localhost:8080
mvn clean package     # builds target/metromind-backend-0.1.0.jar
mvn clean test        # full test suite (202 tests)
```

> Tip: a `NODE_ENV=production` shell overrides npm's install behaviour and
> omits dev dependencies. Run the frontend build/lint with `NODE_ENV` unset (or
> `NODE_ENV=development`) so TypeScript, Vite and oxlint are installed.

## 7. Current limitations (honest list)

- **The frontend does not call the backend REST bridge yet** — the deployed
  frontend still shows routes computed by its own browser-side demo routers.
  The `POST /api/routes` bridge exists and is tested, but no frontend code calls
  it (Phase 10 is backend-only by design).
- **No authentication, database, live metro data, maps, or geolocation.**
- Backend exposes only `/api/health` and `POST /api/routes`. The Trie
  station-name search index is library code and is **not** exposed over HTTP.
  No other endpoints exist or are mocked.
- The dataset is a 42-station subset of the Delhi Metro (documented in
  `data/SOURCES.md`).