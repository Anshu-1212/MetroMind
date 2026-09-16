# MetroMind — A* Routing

This document explains MetroMind's third routing algorithm — A* (A-star) — which
adds geographic guidance to the shortest-distance search introduced by Dijkstra
in Phase 5.

## Architecture

```
metro-network.json
        ↓
MetroNetwork data model
   ├──────────────┐
   ↓              ↓
MetroGraphBuilder  station coordinates (lat/lon)
   ↓              ↓
MetroGraph      GeoPoint map
   └────────┬─────┘
            ↓
        AStarRouter   (g + h, Haversine heuristic)
            ↓
        RouteResult (with totalCost = total kilometres)
            ↓
        Future REST API / Frontend
```

`AStarRouter` receives everything it needs in its constructor — the `MetroGraph`
and an immutable `Map<String, GeoPoint>` of station coordinates. It never loads
`metro-network.json` itself; loading and graph building happen upstream.

## 1. What A* Is

A* is Dijkstra's algorithm augmented with a **heuristic**: a cheap estimate of
how close each station is to the destination. Instead of ordering candidate
stations purely by the cost already paid (`g`), it orders them by
`f(n) = g(n) + h(n)` — cost-so-far *plus* estimated-remaining-cost. This steers
the search toward the destination instead of expanding evenly in all
directions.

## 2. Why MetroMind Needs A* After BFS and Dijkstra

BFS answers "minimum hops", Dijkstra answers "minimum distance or time". Both
search the whole frontier evenly. A* answers the *same distance question as
Dijkstra* but uses the fact that station coordinates are known to focus the
search. On a large rail network the difference is practical: A* examines only
the stations that are plausibly on the way, while Dijkstra examines everything
cheap-by-arrival. MetroMind implements A* to demonstrate the classic
heuristic-search pattern after the two "plain" algorithms.

## 3. The A* Formula

```
f(n) = g(n) + h(n)
```

`f(n)` is the priority given to station `n` in the queue. The station with the
smallest `f` is expanded next.

## 4. g(n) — actual cost from source

`g(n)` is the accumulated rail distance actually travelled from the source to
station `n`:

```
g(n) = sum(edge.distanceKm) along the chosen path to n
```

`g[source] = 0`; every other station starts conceptually at infinity. A
neighbour's `g` is only overwritten when a *strictly cheaper* path is found
(this is the same relaxation as Dijkstra).

## 5. h(n) — heuristic estimate to the destination

`h(n)` is the **Haversine (great-circle) distance** from station `n` to the
destination, in kilometres:

```
h(n) = haversine(location(n), location(destination))
```

It is a pure geography term: it reads the coordinates of `n` and of the
destination and returns the straight-line distance between them.

## 6. f(n) — queue priority

`f(n) = g(n) + h(n)` is stored in each priority-queue entry along with the
station ID and `g`. Java's `PriorityQueue` pops the entry with the smallest `f`.

## 7. PriorityQueue

```java
record QueueEntry(String stationId, double gScore, double fScore) { }

PriorityQueue<QueueEntry> open = new PriorityQueue<>(
        Comparator.comparingDouble(QueueEntry::fScore));
```

A station may be inserted several times if a cheaper path to it is discovered;
the oldest, most expensive entry is left behind (see §10).

## 8. g-Score Map

```java
Map<String, Double> gScore = new HashMap<>();
```

`gScore` holds each reached station's best-known distance. The relaxation step:

```java
for (GraphEdge edge : graph.getNeighbors(current)) {
    double tentativeG = currentG + edge.getDistanceKm();
    if (tentativeG < gScore.getOrDefault(neighbor, INFINITY)) {
        gScore.put(neighbor, tentativeG);
        predecessor.put(neighbor, current);
        open.add(new QueueEntry(neighbor, tentativeG, tentativeG + heuristic(neighbor, destination)));
    }
}
```

## 9. Predecessor Map

```java
Map<String, String> predecessor = new HashMap<>();
```

When a station's best distance is improved, the station that discovered it is
recorded — the same breadcrumb concept as BFS and Dijkstra.

## 10. Stale Queue Entries

Because a station can be re-inserted when improved, the heap may hold an old
`(n, largerG)` entry alongside the new `(n, smallerG)`. When the old entry is
polled, its recorded `g` no longer equals `gScore.get(n)`, so it is skipped:

```java
if (entry.gScore() != gScore.get(current)) {
    continue;   // stale — a shorter path is already known
}
```

A station is never permanently "closed" by a stale pop. Under an admissible
*and consistent* heuristic this skip rarely triggers, but it is what keeps the
implementation correct if a better `g` ever arrives late.

## 11. The Haversine Heuristic

`Haversine.distanceKm(a, b)` computes the great-circle distance between two
`GeoPoint`s:

```
a = sin²(Δφ/2) + cos φ₁ · cos φ₂ · sin²(Δλ/2)
c = 2 · atan2(√a, √(1 − a))
d = R · c              R ≈ 6371 km
```

It is the single implementation of geographic distance in the codebase
(`com.metromind.routing.Haversine`), reused by the router (as `h`) and by the
tests.

## 12. Why Haversine Fits Distance Routing

A rail route between two stations is never *shorter* than the straight line
between them — rails curve, detour around city blocks, and follow the surface
while the straight line is the geodesic. Expressed per-edge: every
`edge.distanceKm` is at least the Haversine distance of its endpoints. This is
exactly the property the heuristic needs (next section). Note that this is a
fact about **physical distance**, not time.

## 13. Admissibility Assumption

Admissibility means the heuristic never overestimates the remaining cost:

```
h(n) ≤ true remaining rail distance from n to the destination
```

Because every rail edge is ≥ the straight line between its endpoints,
`haversine(n, destination)` is a lower bound on the sum of edge distances still
to be covered. Hence `h` is admissible, which is the condition that guarantees
A* returns the true minimum-distance path.

Caveat: the Phase 2 dataset rounds `distanceKm` to one decimal, so a few edges
under-report by up to 0.05 km. The heuristic is admissible to within that
rounding, and the real-network test verifies A*'s distance equals Dijkstra's
distance exactly.

## 14. Why This Heuristic Must NOT Be Used for Travel Time

Nothing above says anything about minutes. `travelTimeMinutes` in the dataset
are coarse estimates (derived from an average speed), not a geometric quantity:
two edges of equal rail distance can have very different travel times, and a
straight-line bound does not bound minutes of travel. Using the distance formula
as a time heuristic would **not** be admissible and could make A* return a
suboptimal time route while appearing principled. MetroMind therefore does **not**
offer travel-time A* — the API is deliberately
`route(source, destination)` with no metric parameter, so the distance heuristic
can never be misapplied to time.

## 15. Path Reconstruction

Same as Dijkstra: walk `predecessor` backwards from the destination until the
source (which has none), then reverse. The result starts at the source, ends at
the destination, and satisfies `hopCount == stationIds.size() - 1`. Reuses
`RouteResult` unchanged; `totalCost` is the total distance in kilometres.

## 16. A* vs Dijkstra

| | Dijkstra | A* |
|---|---|---|
| Priority | `f(n) = g(n)` | `f(n) = g(n) + h(n)` |
| Search shape | even expansion in all directions | biased toward the destination |
| Heuristic | none | Haversine to destination |
| Minimum-distance result | guaranteed | guaranteed (admissible h) |
| Worst-case time | O((V + E) log V) | O((V + E) log V) |

When `h(n) = 0` for every node, A* behaves exactly like Dijkstra. A* uses
information about the destination to examine promising nodes first.

It is *not* claimed that A* always examines fewer nodes: performance depends on
graph structure, heuristic quality, and the specific source/destination.

## 17. A* vs BFS

| | BFS | A* |
|---|---|---|
| Objective | minimum hops | minimum distance |
| Weights | ignored | used (`edge.distanceKm`) |
| Structure | FIFO queue | priority queue by g + h |
| Result | fewest stations | shortest kilometres (possibly *more* hops) |

A 2-hop 8 km route beats a 1-hop 30 km route for A*; BFS would call the 1-hop
route shorter. The two deliberately differ, exactly as in the
Dijkstra-vs-BFS comparison of Phase 5.

## 18. Time Complexity

Worst case with an adjacency list and a binary-heap priority queue (Java's
`PriorityQueue`):

```
O((V + E) log V)
```

Every vertex can be enqueued several times, each heap operation is O(log V), and
every edge is relaxed once when its source is finalised. This is the same worst
case as Dijkstra. In practice a good heuristic reduces the number of expanded
vertices, but **no fixed speedup is claimed** — it depends on the graph, the
heuristic, and the query.

## 19. Space Complexity

```
O(V + E)
```

`MetroGraph` stores the adjacency lists (O(V + E)); the g-score map, predecessor
map, and priority queue each hold at most O(V) entries. The coordinate map is
O(V).

## 20. Limitations

- **Distance-only.** No travel-time objective (see §14); no metric parameter.
- **Requires coordinates.** The constructor rejects a graph station without
  coordinates; A* cannot run on purely topological data.
- **Admissibility rests on data quality.** If an edge were shorter than the
  straight line between its stations beyond the 0.05 km rounding slack, the
  lower-bound guarantee would weaken (the implementation would still return a
  route, but it might not be provably optimal).
- **Worst case is still Dijkstra's.** Heuristic gains are empirical, not
  guaranteed.
- **No interchange or fare awareness** — remains future work.

## Simple Conceptual Example

Four stations near a straight line: `A`, `B`, `C`, `D`, with `D` the
destination.

```
A ──(8 km)── B ──(8 km)── D
│
└──(30 km)─────────────────┘
        (direct A→D)
```

At `A`: `g(A)=0`, `h(A)→D` ≈ 16 km straight line ⇒ `f(A) ≈ 16`.
The candidate `B` has `f(B) = g(B) + h(B) = 8 + 8 ≈ 16`, while the direct edge
gives `f(D) = 30`. The heuristic keeps the search on the two-edge path through
`B`, and A* returns `A → B → D` at 16 km — the minimum distance — while barely
considering the 1-hop but 30 km route.

## Summary

- **BFS** (Phase 4): FIFO queue, unweighted — minimum **hops**.
- **Dijkstra** (Phase 5): priority queue by `g` — minimum **distance** or
  **time**, chosen explicitly.
- **A\*** (Phase 6): priority queue by `g + h` — minimum **distance**, guided by
  the Haversine heuristic to the destination.