# MetroMind — REST Routing API

Introduced in **Phase 10**, the Spring Boot backend now exposes the existing Java
routing algorithms (BFS, Dijkstra, A\*) through a single, strongly typed HTTP
endpoint. This phase adds only the **backend REST bridge** — the React frontend
does **not** call this API yet (see
[README](../README.md#current-status) — frontend routing remains the
client-side demo until a later phase).

## Endpoint

| Item | Value |
|------|-------|
| Method | `POST` |
| Path | `/api/routes` |
| Content-Type | `application/json` (required) |
| Response Content-Type | `application/json` |
| Base URL (local) | `http://localhost:8080` |

The service runs at `http://localhost:8080` locally (or the platform-provided
`PORT` when deployed). `GET /api/health` remains available for health checks.

## Request

```json
{
  "sourceId": "RAJIV_CHOWK",
  "destinationId": "NEW_DELHI",
  "algorithm": "BFS",
  "metric": null
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `sourceId` | string | yes | The station ID of the source. IDs are the dataset's `UPPER_SNAKE` form (e.g. `RAJIV_CHOWK`, `NEW_DELHI`). |
| `destinationId` | string | yes | The station ID of the destination. |
| `algorithm` | enum | yes | One of the supported algorithms below. Values are case-sensitive. |
| `metric` | enum | optional | One of the supported metrics. Required only when `algorithm` is `DIJKSTRA`. Ignored for `BFS` / `ASTAR`. |

### Supported algorithms

Each value dispatches to the project's **existing, unchanged** Java router; the
API never re-implements or mocks routing.

| Value | Existing implementation | Objective |
|-------|--------------------------|-----------|
| `BFS` | `com.metromind.routing.BFSRouter` | Minimum number of station hops |
| `DIJKSTRA` | `com.metromind.routing.DijkstraRouter` | Minimum weighted cost in the chosen `metric` (distance or travel time) |
| `ASTAR` | `com.metromind.routing.AStarRouter` | Minimum distance, guided by the Haversine geographic heuristic |

### Supported metrics

`metric` must be one of the backend's existing `RouteMetric` values. Only
`DIJKSTRA` uses it.

| Value | Meaning |
|-------|---------|
| `DISTANCE` | Minimise total distance (kilometres) |
| `TRAVEL_TIME` | Minimise total travel time (minutes) |

## Response

### 200 — valid request

```json
{
  "algorithm": "BFS",
  "metric": null,
  "sourceId": "RAJIV_CHOWK",
  "destinationId": "NEW_DELHI",
  "found": true,
  "stationIds": ["RAJIV_CHOWK", "NEW_DELHI"],
  "hopCount": 1,
  "totalDistanceKm": 1.2,
  "totalTravelTimeMinutes": 2.0,
  "explored": []
}
```

| Field | Type | Description |
|-------|------|-------------|
| `algorithm` | enum | Echo of the requested (and executed) algorithm. |
| `metric` | enum or `null` | Echo of the metric for `DIJKSTRA`; `null` for `BFS` / `A*`. |
| `sourceId` | string | Echo of the requested source. |
| `destinationId` | string | Echo of the requested destination. |
| `found` | boolean | `true` when a route exists between the two stations. |
| `stationIds` | string[] | The station IDs in travel order, from source to destination. Empty when `found` is `false`. |
| `hopCount` | integer | Number of connections (`stationIds.length - 1`). |
| `totalDistanceKm` | number | Sum of the connection distances along the returned path (kilometres, 3-decimal precision). |
| `totalTravelTimeMinutes` | number | Sum of the connection travel times along the returned path (minutes, 3-decimal precision). |
| `explored` | string[] | Station exploration order. Always `[]` — no router exposes exploration order, so the API never fabricates it. |

**Semantics**

- The response maps 1:1 from the real algorithm result. `stationIds` and
  `hopCount` are the router's own values; `totalDistanceKm` and
  `totalTravelTimeMinutes` are derived by summing the single connection stored
  per consecutive pair along the returned path (exact for the current dataset,
  which has no parallel edges).
- Same-station requests return `found: true`, `hopCount: 0`, an empty path cost,
  and `stationIds: [sourceId]`.
- When both stations are valid but no route exists, the API answers **200** with
  `found: false` and zeroed totals. (The current 42-station dataset is a single
  connected component, so this path is exercised by tests over a synthetic
  two-component graph.)
- `sourceId == destinationId` is the shortest possible route; the routers return
  it as a one-station route with zero cost.

### Error behavior

Errors always use a small, consistent body — no stack traces or internal details
ever reach the client:

```json
{ "error": "STATION_NOT_FOUND", "message": "Unknown station: xyz" }
```

| HTTP | `error` code | When |
|------|--------------|------|
| `400` | `INVALID_REQUEST` | Missing/blank `sourceId` or `destinationId`; missing `algorithm`; `DIJKSTRA` without `metric`; malformed or non-JSON body. |
| `400` | `UNSUPPORTED_ALGORITHM` | `algorithm` value is not `BFS`, `DIJKSTRA`, or `ASTAR`. |
| `400` | `UNSUPPORTED_METRIC` | `metric` value is not `DISTANCE` or `TRAVEL_TIME`. |
| `404` | `STATION_NOT_FOUND` | A station ID does not exist in the network (checked before any routing runs). |
| `405` | `METHOD_NOT_ALLOWED` | A request uses a method other than `POST` on `/api/routes`. |
| `500` | `INTERNAL_ERROR` | An unexpected server-side failure (generic message only). |

## Cross-origin access (CORS)

The API allows cross-origin requests **per configured origin**, never permissive
`*`:

- Property: `app.cors.allowed-origins` (comma-separated list)
- Default: `http://localhost:5173` (the Vite development origin)
- Mapped to `/api/**` with methods `GET` and `POST`

To allow the deployed (Vercel) frontend origin later, set
`app.cors.allowed-origins=https://your-app.vercel.app` as an environment variable
— no code change and no permissive wildcard needed.

## Local example

```bash
# Health check
curl http://localhost:8080/api/health

# Minimum-hop route (BFS)
curl -X POST http://localhost:8080/api/routes \
  -H 'Content-Type: application/json' \
  -d '{"sourceId":"RAJIV_CHOWK","destinationId":"NEW_DELHI","algorithm":"BFS"}'

# Minimum-distance route (Dijkstra)
curl -X POST http://localhost:8080/api/routes \
  -H 'Content-Type: application/json' \
  -d '{"sourceId":"DWARKA_SECTOR_21","destinationId":"HAUZ_KHAS","algorithm":"DIJKSTRA","metric":"DISTANCE"}'

# Minimum-travel-time route (Dijkstra)
curl -X POST http://localhost:8080/api/routes \
  -H 'Content-Type: application/json' \
  -d '{"sourceId":"DWARKA_SECTOR_21","destinationId":"HAUZ_KHAS","algorithm":"DIJKSTRA","metric":"TRAVEL_TIME"}'

# Minimum-distance route (A*)
curl -X POST http://localhost:8080/api/routes \
  -H 'Content-Type: application/json' \
  -d '{"sourceId":"RAJIV_CHOWK","destinationId":"SAMAYPUR_BADLI","algorithm":"ASTAR"}'
```

## API implementation notes

- **Controller is thin.** `RouteController` only parses the body and delegates.
  Validation, algorithm selection, and execution live in `RouteService`, the
  adapter that owns the real routers — no routing logic lives in HTTP code.
- **No duplicated algorithms.** All three branches call the existing
  `com.metromind.routing` classes over the same `com.metromind.graph.MetroGraph`
  built from the single `data/metro-network.json`.
- **Deterministic.** The same request always returns the same path and totals.