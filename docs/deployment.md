# MetroMind — Deployment & Production Readiness

Phase 9 and Phase 11 cover deployment configuration and frontend/backend integration. This
document describes the monorepo deployment architecture, how each component is deployed, and how HTTP communication is configured.

## 1. Architecture

```
                       ┌────────────────────────────┐
                       │  Vercel (static hosting)   │
        Browser  ◄────►│  React + TypeScript + Vite │
                       │  bundles data/metro-       │
                       │  network.json at build     │
                       └────────────────────────────┘
                                  │
                                  │  HTTP POST /api/routes
                                  ▼
                       ┌────────────────────────────┐
                       │  Render (Java service)     │
  GET /api/health  ◄───┤  Spring Boot 3.5 · Java 21 │
  POST /api/routes ◄───┤  REST API                  │
                       └────────────────────────────┘
```

Two deployables share one Git repository:

- **Frontend** — a static single-page app (React 19 + TypeScript + Vite). It loads
  the network directly from the bundled `data/metro-network.json` for visualization
  and issues HTTP `POST /api/routes` calls via `src/api/routes.ts` to compute paths.
- **Backend** — a Spring Boot service. It exposes `GET /api/health` and
  `POST /api/routes`, which dispatches to the **real** Java routing algorithms
  (BFS, Dijkstra, A*) over the dataset.

## 2. Frontend — Vercel

The Vercel project root is the `frontend/` directory. A minimal
repo-root [`vercel.json`](../vercel.json) configures build settings.

| Setting            | Value            |
|--------------------|------------------|
| Framework          | Vite (autodetected) |
| Project root       | `frontend/`      |
| Install command    | `npm install` (default) |
| Build command      | `npm run build`  |
| Output directory   | `dist`           |
| Node.js            | Vite 8 requires Node `^20.19.0` or `>=22.12.0` |
| Environment variables | `VITE_API_BASE_URL` (points to backend URL, e.g., Render) |

`vercel.json`:

```json
{
  "$schema": "https://openapi.vercel.sh/vercel.json",
  "rootDirectory": "frontend",
  "buildCommand": "npm run build",
  "outputDirectory": "dist"
}
```

## 3. Backend — Render

The backend is prepared in [`render.yaml`](../render.yaml) using Render's native
Java runtime.

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

## 4. Environment variables

- **Frontend:** `VITE_API_BASE_URL` specifies the backend API endpoint. In local development, it defaults to `http://localhost:8080`. In production on Vercel, configure this variable to point to the Render backend service.
- **Backend:** `app.cors.allowed-origins` configures allowed CORS origins. In local development, it defaults to `http://localhost:5173`. In production, set this to the deployed Vercel frontend URL.

## 5. Backend API status

- `GET /api/health` — health check.
- `POST /api/routes` — routing bridge. Parses
  `{sourceId, destinationId, algorithm, metric}`, validates requests, and runs
  the **real** Java BFS / Dijkstra / A* routers.
- CORS: per-origin allowlist via `app.cors.allowed-origins`.

## 6. Local workflow

```bash
# Frontend
cd frontend
npm install           # installs dev + prod dependencies
npm run dev           # dev server on http://localhost:5173
npm run build         # production build -> dist/
npm run lint          # oxlint

# Backend
cd backend
mvn spring-boot:run   # starts on http://localhost:8080
mvn clean package     # builds target/metromind-backend-0.1.0.jar
mvn clean test        # full test suite
```