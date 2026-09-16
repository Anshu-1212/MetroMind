# MetroMind — Graph Model

This document explains how MetroMind represents the metro network as an in-memory weighted graph, and how future routing algorithms will consume it.

## Architecture

```
metro-network.json (raw data)
        ↓
MetroNetwork data model
        ↓
MetroGraphBuilder
        ↓
MetroGraph
        ↓
Adjacency List
        ↓
Future Algorithms (BFS, Dijkstra, A*)
```

## 1. Why MetroMind Uses a Graph

Metro networks are naturally graphs:

- **Stations** are vertices
- **Movement between adjacent stations** is an edge
- **Weighted edges** capture distance, travel time, and line membership

Graph algorithms (BFS, Dijkstra, A\*) are the established way to answer route queries such as "shortest distance from A to B" or "fastest route with a line change". Modelling the network as a graph from the start keeps route queries as pure graph operations, independent of the raw data format.

## 2. Vertex Representation

Every station in `metro-network.json` becomes one vertex in the graph. Vertices are keyed by the **stable station ID** (e.g. `RAJIV_CHOWK`), never by the human-readable name.

Implementation: keys of the adjacency list `Map<String, List<GraphEdge>>`.

## 3. Edge Representation — `GraphEdge`

A `GraphEdge` is an immutable, directed edge carrying exactly what future algorithms need:

| Field | Type | Purpose |
|-------|------|---------|
| `destination` | String | Destination station ID |
| `line` | String | Metro line ID this edge belongs to |
| `distanceKm` | double | Edge weight for shortest-distance routing |
| `travelTimeMinutes` | int | Edge weight for fastest routing |

Equality and `hashCode` are defined over all four fields, so two edges between the same stations on **different lines** are distinct objects.

`GraphEdge` is deliberately separate from the raw `Connection` record — see section 8.

## 4. Adjacency-List Structure

The graph uses **an adjacency list, not an adjacency matrix**:

```java
Map<String, List<GraphEdge>>  // station ID -> outgoing edges
```

```
A → [ Edge(B, BLUE, 1.2km, 2min), Edge(C, YELLOW, 1.5km, 3min) ]
B → [ Edge(A, BLUE, 1.2km, 2min) ]
C → [ Edge(A, YELLOW, 1.5km, 3min) ]
```

**Why an adjacency list:** a metro network is sparse — each station connects to only a handful of neighbours, never to all other stations. An adjacency matrix would waste O(V²) memory; an adjacency list is **O(V + E)**. HashMap-based lookup gives O(1) vertex access and O(deg) neighbour iteration, which is exactly what Dijkstra and A\* need.

## 5. Why Edges Are Bidirectional

Metro movement is symmetric: a train that goes A→B also goes B→A with the same distance and travel time (the Phase 2 dataset documents no one-way segments). Therefore every raw connection is expanded during construction into **two** directed edges:

```
raw connection A→B   ⇒   edge A→B  and  edge B→A
```

This keeps future graph traversals simple: algorithms move along outgoing edges without ever special-casing direction. Nothing in the raw data is modified to achieve the reverse edge — the builder performs the transformation.

## 6. How Line Information Is Preserved

Each `GraphEdge` carries a `line` field copied from the raw connection. This matters because:

- a station pair can be connected by more than one line (parallel edges), and
- future minimum-interchange routing needs to know which line a passenger is travelling on.

Example — Rajiv Chowk on both Yellow and Blue lines:

```
RAJIV_CHOWK → [ Edge(NEW_DELHI, YELLOW, ...), Edge(PATEL_CHOWK, YELLOW, ...),
                Edge(KAROL_BAGH, BLUE, ...),  Edge(ANAND_VIHAR, BLUE, ...) ]
```

The four edges are **not** duplicates: each belongs to a different (neighbour, line) pair.

## 7. How Interchanges Are Represented

An interchange station is a single vertex whose **line membership is preserved on its edges**. There are **no artificial self-loop edges** representing "changing lines at the same station". For example, Rajiv Chowk is one vertex; a passenger changing there simply moves to an edge whose `line` differs from the edge they arrived on.

Line-to-line transfer logic (including any future interchange penalty) belongs to **future routing algorithms**, not to the graph representation.

## 8. Why `Connection` and `GraphEdge` Are Separate

These two types live in different layers for a clear reason:

- **`Connection`** (`com.metromind.data`) is a raw, bidirectional **source-of-truth record** that mirrors the JSON on disk. It describes a fact about the network: "these two stations are adjacent on this line".
- **`GraphEdge`** (`com.metromind.graph`) is a **directed, algorithm-ready** object optimised for traversal: pre-expanded into forward/reverse directions, immutable, and carrying exactly the weights algorithms inspect.

Keeping them separate means the raw data format can change without touching algorithm code, and the graph cannot be silently mutated by callers.

## 9. How `MetroGraphBuilder` Transforms the Data

`MetroGraphBuilder.from(MetroNetwork)`:

1. Adds every station as an empty adjacency-list entry (vertex).
2. For each raw connection `A→B` on line `L` with distance `d` and time `t`:
   - adds `Edge(B, L, d, t)` to A's list, and
   - adds `Edge(A, L, d, t)` to B's list.
3. Converts each adjacency list into an unmodifiable list held in an unmodifiable map.

**Self-loop policy:** a connection where `from == to` is rejected with `IllegalArgumentException` at build time.

**Duplicate-edge policy:** exact duplicate edges (same destination, line, distance, time) introduced by repeated source records are deduplicated via a `LinkedHashSet`. Legitimate parallel edges between the same stations on **different lines** are preserved exactly once each.

## 10. How Future Algorithms Will Consume `MetroGraph`

The `MetroGraph` API is intentionally small and read-only:

```java
Set<String> getStationIds()
int getStationCount()
int getEdgeCount()
boolean containsStation(String stationId)
List<GraphEdge> getNeighbors(String stationId)   // unmodifiable
List<GraphEdge> getOutgoingEdges(String stationId)
```

A future Dijkstra implementation can simply write:

```java
for (GraphEdge edge : graph.getNeighbors(current)) {
    double newDistance = dist.get(current) + edge.getDistanceKm();
    // relax edge.getDestination()
}
```

Algorithms observe `destination`, `distanceKm`, `travelTimeMinutes`, and `line` via the edge accessors without knowing how the graph is stored internally.

## Current Dataset Limitations

The graph faithfully represents **the current validated dataset**, including its documented limitations (see `data/SOURCES.md`):

- **42 stations**, **2 lines**, **41 connections** → **82 directed edges**.
- The Blue Line has three connections that span several omitted intermediate stations (e.g. `JANAKPURI_WEST → PATEL_NAGAR` at 9.1 km). The graph keeps these as single edges; they are **not** "repaired" or split in this phase. When the full network is added, those records should be split at the real intermediate stations.
- Several real-world interchange stations (e.g. Kashmere Gate, Hauz Khas) are single-line in the dataset, so they are not interchange vertices here. This is a dataset-scope limitation, not a graph-model defect.