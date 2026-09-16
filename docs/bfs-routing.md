# MetroMind — BFS Routing

This document explains how MetroMind's first routing algorithm — Breadth-First Search (BFS) — works and how it fits the overall architecture.

## Architecture

```
metro-network.json
        ↓
MetroNetwork data model
        ↓
MetroGraphBuilder
        ↓
MetroGraph (adjacency list)
        ↓
BFSRouter
        ↓
RouteResult
        ↓
Future REST API / Frontend
```

`BFSRouter` depends only on `MetroGraph`. It never reads the JSON file and never builds the graph itself — it only searches.

## 1. What BFS Is

Breadth-First Search traverses a graph **level by level**. Starting from the source station, it first visits every neighbour (1 hop away), then every neighbour-of-a-neighbour (2 hops away), and so on, in expanding rings.

## 2. Why MetroMind Uses BFS

BFS answers a fundamental passenger question: *"what is the fewest number of stations I must travel through?"* When a user has not specified a cost metric, the minimum-hop route is a natural default. BFS is also the simplest correct unweighted search, which makes it a solid, auditable first algorithm before weighted search (Dijkstra, A\*) is added.

## 3. What BFS Optimizes

BFS finds the route with the **minimum number of edges**. In MetroMind terms, that is the **minimum number of station-to-station hops**.

> BFS does **not** minimize physical distance or travel time. Two hops of 10 km each are "shorter" for BFS than one hop of 9.9 km that happens to need more hops — wait, no. Let that be stated precisely below.

## 4. Example

```
      A
      │
  ┌───┼───┐
  │   │   │
  B   C   D
  │   │
  E   F───G
  │
  H
```

From **A** to **H**, BFS explores level by level:

| Level | Stations | Meaning |
|-------|----------|---------|
| 0 | A | source |
| 1 | B, C, D | one hop from A |
| 2 | E, F | two hops (from B and C) |
| 3 | H, G | three hops (from E and F) |

The found route is `A → B → E → H` (3 hops). BFS never uses distance or time; all these transitions count as one hop each.

## 5. The Algorithm

### Queue

```java
Queue<String> queue = new ArrayDeque<>();
```

A FIFO queue guarantees level-by-level exploration. The first stations enqueued (neighbours of the source) are the first processed, so no station two hops out is ever processed before all one-hop stations.

### Visited set

```java
Set<String> visited = new HashSet<>();
```

Prevents re-visiting a station. A station is added to visited **the moment it is discovered (enqueued)** — not when removed — so a station is never enqueued twice and the queue holds no duplicates.

### Predecessor map

```java
Map<String, String> predecessor;
```

`predecessor.put(neighbour, current)` records, for each discovered station, the station that discovered it. This is the breadcrumb trail used to rebuild the path.

### Main loop

```
enqueue(source); mark source visited

while queue is not empty:
    current = dequeue()
    if current == destination: reconstruct path; stop
    for each neighbour of current:
        if neighbour not visited:
            mark visited
            predecessor[neighbour] = current
            enqueue neighbour
```

### Path reconstruction

Walking the predecessor map backwards from the destination, then reversing:

```
D → C → B → A   (walk backwards)
A → B → C → D   (reversed = answer)
```

Reconstruction uses only the predecessor map — no second search is run.

## 6. Special Cases

| Case | Behavior |
|------|----------|
| **Source == destination** | A found route containing exactly one station, `hopCount = 0`. |
| **Reachable destination** | Ordered route from source to destination; `hopCount = stations - 1`. |
| **Unreachable destination** | `RouteResult.notFound()` — empty station list, no partial route returned. |
| **Unknown station ID** | `RouteResult.notFound()` — treated like an unreachable station. |
| **Null source/destination** | `IllegalArgumentException` — a programming error, not a routing outcome. |

## 7. Why Weights Are Ignored

BFS is defined for **unweighted** graphs. `GraphEdge` carries `distanceKm`, `travelTimeMinutes`, and `line`, but `BFSRouter` never reads them — every station-to-station transition counts as exactly one hop, regardless of physical length. Minimising hops is a different objective from minimising kilometres or minutes; those are weighted problems for Dijkstra/A\* in later phases.

## 8. Parallel Edges

When two stations are joined by more than one line edge (e.g. `A → B` on both BLUE and YELLOW), moving between them is still **one** station-level hop. Because `B` is added to `visited` the first time it is discovered, the second edge never causes a duplicate visit. This is exactly correct for hop minimisation — the line choice is a problem for future minimum-interchange routing.

## 9. Time Complexity — O(V + E)

- Every vertex is enqueued at most once and processed once.
- When a vertex is processed, all its incident edges are scanned.
- Combined: O(V + E).

## 10. Space Complexity — O(V)

The queue, visited set, and predecessor map each hold at most one entry per vertex: O(V). The `RouteResult` path is at most V stations.

## 11. Limitations

- **Hop count is not distance or time.** BFS is wrong for "fastest" or "shortest-distance" queries.
- **No interchange awareness.** BFS returns station IDs only; it does not account for line transfers. With only two lines intersecting at a single point the current dataset rarely forces a transfer, but real line-change minimization is a future objective.
- **Not fare-aware.** No cost concept beyond hop count.
- **Single-source, single-target.** Each query targets one destination; result reconstruction is per-query.

## Result Contract

`RouteResult` is immutable and carries only the shape of the answer:

- `isFound()` — whether a route exists
- `getStationIds()` — ordered station IDs (source → destination)
- `getHopCount()` — `stationIds.size() - 1` when found, `0` otherwise

Internal BFS state (queue, visited set, predecessor map) is never exposed.