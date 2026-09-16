# MetroMind — Metro Data Model

This document explains how the Delhi Metro network is represented as data and how it will eventually become a weighted graph for route planning.

## Overview

The data model follows a three-layer structure:

```
Raw Metro Data        (metro-network.json)
        ↓
Validated Data Model  (Station / MetroLine / Connection)
        ↓
Future: Weighted Graph (MetroGraph — Phase 3+)
```

Phase 2 creates the validated data model. The graph implementation comes later.

## Data Layers

### Layer 1: Stations (Vertices)

Each station is a **vertex** in the future graph. Stations store:

- A unique ID (machine-readable)
- A human-readable name
- Geographic coordinates (latitude/longitude)
- Which metro lines serve the station
- Whether the station is an interchange

**Key design decision:** Station IDs are stable identifiers independent of names. The future graph will reference stations by ID, not by name.

```json
{
  "id": "RAJIV_CHOWK",
  "name": "Rajiv Chowk",
  "latitude": 28.6327,
  "longitude": 77.2193,
  "lines": ["YELLOW", "BLUE"],
  "interchange": true
}
```

### Layer 2: Lines (Ordered Station Sequences)

Each metro line defines an **ordered sequence** of stations. This ordering establishes which stations are adjacent on that line.

Lines serve two purposes:
1. **Grouping** — they identify which stations belong together on a single route
2. **Ordering** — they define the physical adjacency of stations (station A → station B on the Yellow Line means A and B are consecutive)

```json
{
  "id": "YELLOW",
  "name": "Yellow Line",
  "color": "#FFCC00",
  "stations": ["SAMAYPUR_BADLI", "ROHINI_SECTOR_18_19", "...", "HAUZ_KHAS"]
}
```

### Layer 3: Connections (Edges)

Each connection is a **weighted edge** between two adjacent stations on the same line. Connections carry:

- Source and destination station IDs
- The line they belong to
- Distance in kilometres
- Estimated travel time in minutes

```json
{
  "from": "NEW_DELHI",
  "to": "RAJIV_CHOWK",
  "line": "YELLOW",
  "distanceKm": 1.2,
  "travelTimeMinutes": 2
}
```

Connections are **bidirectional** — the same connection applies in both directions.

## How Lines Become a Graph

Consider a simplified Yellow Line:

```
Station A → Station B → Station C → Station D
```

The connections for this line would be:

| From | To | Line | Distance | Time |
|------|-----|------|----------|------|
| A | B | YELLOW | 1.3 km | 2 min |
| B | C | YELLOW | 1.1 km | 2 min |
| C | D | YELLOW | 0.9 km | 2 min |

In the future weighted graph (Phase 3), this becomes:

```
A ——(1.3km)—— B ——(1.1km)—— C ——(0.9km)—— D
```

Each edge has two weights: distance and travel time. The routing algorithm will choose which weight to optimise based on the user's preference (shortest distance vs. fastest route).

## Interchanges

An interchange station is served by **multiple lines**. For example, Rajiv Chowk is on both the Yellow and Blue lines:

```
Yellow Line: ... → New Delhi → [Rajiv Chowk] → Patel Chowk → ...
Blue Line:   ... → Karol Bagh → [Rajiv Chowk] → Anand Vihar → ...
```

At Rajiv Chowk, a passenger can transfer between the Yellow and Blue lines. This is represented structurally:

1. **Station membership** — Rajiv Chowk's `lines` array contains both `["YELLOW", "BLUE"]`
2. **Two connections from the same station** — one on the Yellow Line, one on the Blue Line
3. **Interchange flag** — `interchange: true` because `lines.length > 1`

**Important:** The interchange itself is **not** a travel edge. It is a property of the station. The future routing engine will handle line transfers as a separate operation from inter-station movement.

## Minimum-Interchange Routing

The data model supports future minimum-interchange routing because:

- Each connection belongs to exactly one line
- Interchange stations are explicitly identified
- The routing engine can track which line the passenger is currently on
- A line transfer at an interchange is a distinct state change (same station, different line)

This means the graph traversal can distinguish between:

| Action | Representation |
|--------|---------------|
| Moving to the next station | Follow a connection on the current line |
| Transferring lines | Stay at the same station, switch to a different line's connections |

## Coordinate Data and A\* Heuristic

Station coordinates (latitude/longitude) will support the A\* search algorithm's heuristic function. The heuristic estimates the remaining distance from any station to the destination using the **Haversine formula** (great-circle distance).

This requires:
- Accurate coordinates for every station ✓ (from OpenStreetMap)
- Coordinates stored in the station model ✓
- A distance function that can be called during A\* search (Phase 3)

## Future Fare Calculations

The data model does not currently include fare information. When fares are added in a later phase, they can be:

1. **Added to connections** — each edge carries a fare weight
2. **Computed from distance** — fare = f(distance) using a fare function
3. **Zone-based** — stations grouped into fare zones, fare = f(zones crossed)

The connection model's `distanceKm` field supports distance-based fare calculation. No schema changes will be needed.

## Service Disruptions

When service disruptions are modelled in a future phase, the approach will be:

1. A connection can be marked as **disabled** (not removed)
2. The routing engine skips disabled connections during traversal
3. Affected stations remain in the graph (passengers may still be able to reach them via other lines)

## Example: Building a Route

To find the shortest-distance route from **Samaypur Badli** to **Karol Bagh**:

1. **Start at SAMAYPUR_BADLI** on the Yellow Line
2. **Follow Yellow Line connections south** to RAJIV_CHOWK (10 connections)
3. **Transfer at RAJIV_CHOWK** — switch to Blue Line
4. **Follow Blue Line connections west** to KAROL_BAGH (2 connections)

Total distance: sum of all connection distances along the path.
Total time: sum of all connection travel times + interchange time.

This is the kind of computation the future Phase 3 graph engine will perform.
