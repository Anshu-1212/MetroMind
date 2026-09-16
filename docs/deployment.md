# MetroMind — Deployment & Production Readiness

Phase 9 covers deployment configuration for both halves of the monorepo. This
document says exactly what is deployed, how to deploy each part, and — just as
importantly — what is **not** wired together yet.

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
        /api/health  ◄─┤  Spring Boot 3.5 · Java 21 │
                       └────────────────────────────┘
```

Two independent deployables share one Git repository:

- **Frontend** — a static single-page app (React + TypeScript + Vite). It loads
  the network directly from the bundled `data/metro-network.json` and runs
  **client-side demo routers** (`frontend/src/network/routing.ts`) for
  BFS / Dijkstra / A*. It needs **no server and no environment variables**.
- **Backend** — a Spring Boot service. Today it exposes **one endpoint**,
  `GET /api/health`. The Java routing algorithms (BFS, Dijkstra, A*) and the Trie
  search index exist but are **not reachable over HTTP**.

There is **no REST bridge** between the frontend and the backend: the deployed
frontend does not call the backend, and the backend's routing algorithms are not
exposed. This is a deliberate, documented gap (`docs/route-visualization.md`),
not a misconfiguration.

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

If future phases introduce `VITE_*` variables, Vercel supports them through its
environment-variable UI; a `.env.example` would be added then.

## 5. Current backend API status

- `GET /api/health` — the **only** public endpoint. Returns service name/status.
- **No** route endpoints (`/api/routes`, search, etc.) exist or are mocked.
- The frontend **does not** call the backend, so no CORS configuration is
  currently needed and none is present.

Do not assume any routing endpoint exists until a later phase adds one.

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
mvn clean test        # full test suite (177 tests)
```

> Tip: a `NODE_ENV=production` shell overrides npm's install behaviour and
> omits dev dependencies. Run the frontend build/lint with `NODE_ENV` unset (or
> `NODE_ENV=development`) so TypeScript, Vite and oxlint are installed.

## 7. Current limitations (honest list)

- **No REST routing API** — the deployed frontend shows routes computed by its
  own browser-side demo routers; it does **not** call the Java algorithms.
- **No authentication, database, live metro data, maps, or geolocation.**
- Backend exposes only `/api/health`; the Java graph/routing/search code is
  library code and is unit-tested but not accessible from the network.
- The dataset is a 42-station subset of the Delhi Metro (documented in
  `data/SOURCES.md`).