# MetroMind — Dijkstra Routing

This document explains MetroMind's second routing algorithm — Dijkstra's
shortest-path algorithm — and how it differs from the BFS routing added in
Phase 4.

## Architecture

```
metro-network.json
        ↓
MetroNetwork data model
        ↓
MetroGraphBuilder
        ↓
MetroGraph (weighted adjacency list)
        ↓
DijkstraRouter (RouteMetric.DISTANCE | RouteMetric.TRAVEL_TIME)
        ↓
RouteResult (with totalCost)
        ↓
Future REST API / Frontend
```

`DijkstraRouter` depends only on `MetroGraph`, exactly like `BFSRouter`. It
never loads the JSON file and never builds the graph — it only searches a graph
that is handed to it.

## 1. What Dijkstra's Algorithm Is

Dijkstra's algorithm finds the route whose *sum of selected edge weights* is
minimised, when all weights are non-negative. It processes vertices in order of
their current best (tentative) distance, using a priority queue to always expand
the cheapest frontier first, and "relaxes" edges: if a cheaper way to reach a
neighbour is found, the neighbour's best distance and predecessor are updated.

## 2. Why MetroMind Needs Dijkstra After BFS

BFS answers *"what is the fewest number of stations I must pass through?"* That
is the natural default for a passenger with no stated preference. But real
commuters optimise more than hop count:

- *"I want the shortest ride in kilometres."* → **DISTANCE**
- *"I want the fastest ride in minutes."* → **TRAVEL_TIME**

Both questions require minimising a sum of weighted edges, which is exactly what
Dijkstra does. BFS cannot answer them because it ignores weights entirely.

## 3. BFS vs Dijkstra

| | BFS | Dijkstra |
|---|---|---|
| Data structure | FIFO `Queue` | `PriorityQueue` (binary heap) |
| Objective | min number of edges (hops) | min sum of selected non-negative weights |
| Weights used | none | `distanceKm` or `travelTimeMinutes` (chosen explicitly) |
| Time | O(V + E) | O((V + E) log V) |
| Space | O(V) | O(V + E) (including graph storage) |
| Phase | 4 | 5 |

> BFS minimises the **number of edges**. Dijkstra minimises the **sum of
> selected non-negative edge weights**. They are different problems: a 2-hop,
> 30 km route beats a 3-hop, 5 km route for Dijkstra if metric is distance.

## 4. Routing Objectives — `RouteMetric`

Distance and travel time are two independent objectives and are **never combined
into one arbitrary score**. The caller chooses exactly one per query.

```java
enum RouteMetric {
    DISTANCE,      // minimize sum(edge.getDistanceKm())
    TRAVEL_TIME    // minimize sum(edge.getTravelTimeMinutes())
}
```

Usage:

```java
DijkstraRouter router = new DijkstraRouter(graph);

// Minimum total kilometres
RouteResult distance = router.route("DWARKA_SECTOR_21", "HAUZ_KHAS", RouteMetric.DISTANCE);

// Minimum total minutes
RouteResult time = router.route("DWARKA_SECTOR_21", "HAUZ_KHAS", RouteMetric.TRAVEL_TIME);
```

The two queries may legitimately return different routes, because each minimises
a different quantity.

## 5. PriorityQueue Usage

```java
record QueueEntry(String stationId, double distance) { }

PriorityQueue<QueueEntry> queue = new PriorityQueue<>(
        Comparator.comparingDouble(QueueEntry::distance));
```

The queue always yields the entry with the smallest tentative distance. Because
Java's `PriorityQueue` is a binary heap, each insert / remove costs O(log V).

## 6. Distance Map

```java
Map<String, Double> distances = new HashMap<>();
distances.put(source, 0.0);
```

`distances` stores every station's best known distance. Untouched stations are
implicitly at infinity via `distances.getOrDefault(station, INFINITY)`.

**Relaxation** — the heart of Dijkstra:

```java
double weight    = selectedWeight(edge, metric);          // km or minutes
double newDistance = currentDistance + weight;
if (newDistance < distances.getOrDefault(neighbor, INFINITY)) {
    distances.put(neighbor, newDistance);                 // strictly better
    predecessors.put(neighbor, current);
    queue.add(new QueueEntry(neighbor, newDistance));
}
```

A neighbour's distance is only overwritten when the new candidate is *strictly*
lower, so `distances` always holds the true shortest distances once processing
finishes.

## 7. Predecessor Map

```java
Map<String, String> predecessors = new HashMap<>();
```

When a station's best distance is improved, the station that discovered it is
recorded. This is the same predecessor concept BFS uses in Phase 4.

## 8. Stale Priority-Queue Entries

When a station is improved, a *new* entry is inserted but the *old* (longer)
entry is left in the heap:

```
A initially queues B at 10.
Later A→C→B improves B to 2.
The heap now contains (B, 10) and (B, 2).
```

When `(B, 10)` is popped, it is **stale**: its recorded distance no longer
matches `distances.get("B")` (now 2). It is skipped:

```java
if (currentDistance != distances.get(current)) {
    continue;   // stale entry — already superseded
}
```

This is why Dijkstra does **not** use a permanent "visited" flag at poll time:
an outdated entry must never cause a station to be skipped permanently. The
first valid (non-stale) pop of a destination is its true shortest distance, so
the search can break at that point. Non-negative weights make this early exit
correct.

## 9. Path Reconstruction

Walking the predecessor map backwards from the destination, then reversing:

```
A → B → C → D          (result, reversed)
predecessors: B→A, C→B, D→C
walk: D → C → B → A    (A has no predecessor — stop)
reverse: A → B → C → D
```

`DijkstraRouter` reuses the RouteResult shape: for a found route,
`hopCount == stationIds.size() - 1` and endpoints match source/destination.

## 10. Distance Routing — `RouteMetric.DISTANCE`

Weight of each edge = `edge.getDistanceKm()`. The result minimises
`sum(distanceKm)` along the route.

| Route | Distance |
|---|---|
| A → B → C | 2.0 + 3.0 = **5.0 km** ✓ |
| A → D → C | 1.0 + 6.0 = 7.0 km |

Dijkstra selects **A → B → C** even though both routes have the same hop count.
`result.getTotalCost()` = 5.0 (kilometres).

## 11. Travel-Time Routing — `RouteMetric.TRAVEL_TIME`

Weight of each edge = `edge.getTravelTimeMinutes()`. The result minimises
`sum(travelTimeMinutes)`.

| Route | Time |
|---|---|
| A → B → C | 8 + 8 = 16 min |
| A → D → C | 4 + 4 = **8 min** ✓ |

Dijkstra selects **A → D → C** even if its physical distance were larger. The
algorithm is optimising travel time, *not* distance.

## 12. Non-Negative Edge Weights

Dijkstra is correct only when every weight is non-negative. This holds by
construction in MetroMind: `GraphEdge`'s constructor rejects `distanceKm <= 0`
and `travelTimeMinutes <= 0`, so every graph from `MetroGraphBuilder` contains
only strictly positive weights. Dijkstra therefore relies on existing validation
and never silently accepts a negative weight (that would imply a negative-cost
cycle and require Bellman–Ford, which is out of scope).

## 13. Source == Destination

A route from a station to itself is:

- `found = true`
- `stationIds = [source]`
- `hopCount = 0`
- `totalCost = 0` for **both** metrics

## 14. Unreachable Stations

- **Unreachable destination** (stations exist, no path): `RouteResult.notFound()`
  — empty station list, `hopCount = 0`, `totalCost` undefined (NaN). No partial
  route, no infinite cost, no fallback.
- **Unknown station ID**: `notFound()`, matching BFS.
- **Null source / destination / metric**: `IllegalArgumentException` — a
  programming error, not a routing outcome.

## 15. Parallel Edges

The graph may hold several edges between the same two stations on different
lines with different weights. BFS collapses them to station reachability;
Dijkstra must **not** collapse them, because the weights can differ per edge.
Dijkstra relaxes every `GraphEdge` object individually and keeps the cheaper one
for the selected metric.

## 16. Total Cost Exposure

`RouteResult` is extended with `getTotalCost()`:

- **DISTANCE route** → total kilometres
- **TRAVEL_TIME route** → total minutes
- **0** for source == destination
- **`Double.NaN`** for BFS results (unweighted, no single scalar cost) and
  not-found results

Distance accumulates as `double` with no per-edge rounding; travel time is the
exact integer sum carried as a `double`. Formatting/rounding belongs to the
future presentation layer, not the algorithm.

## 17. Time Complexity — O((V + E) log V)

With an adjacency list and a binary-heap priority queue (Java `PriorityQueue`):

- Each vertex can be inserted several times; every insertion/removal is
  O(log V).
- Each edge is relaxed once when its source vertex is finalised.
- Total: **O((V + E) log V)**, often written O(E log V) for sparse graphs.

## 18. Space Complexity — O(V + E)

- `MetroGraph` itself stores the adjacency lists: O(V + E).
- Dijkstra's auxiliary structures — distance map, predecessor map, and priority
  queue — each hold at most O(V) entries.
- Total: **O(V + E)**.

## 19. Limitations

- **Requires non-negative weights** (guaranteed here by `GraphEdge`).
- **Single-source, single-target** per query.
- **No interchange awareness** — returns route station IDs; line minimisation is
  a future objective.
- **Not fare-aware** — no monetary cost concept.
- **Two separate objectives require two separate queries** — a query cannot
  optimise distance and time simultaneously.

## Summary

- **BFS** (Phase 4): FIFO queue, unweighted, minimises **number of edges**,
  O(V + E).
- **Dijkstra** (Phase 5): priority queue, weighted, minimises the **sum of
  selected non-negative edge weights** (distance or travel time, chosen
  explicitly), O((V + E) log V).