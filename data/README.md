# MetroMind — Data Directory

This directory contains validated metro network data in JSON format.

## Dataset Purpose

The data represents a metro network as a set of **stations**, **lines**, and **connections** — the raw inputs for building a weighted graph that will power future routing algorithms (BFS, Dijkstra, A\*).

## Files

| File | Description |
|------|-------------|
| `metro-network.json` | Complete metro network dataset |
| `SOURCES.md` | Data provenance and methodology |
| `README.md` | This file |

## JSON Structure

The dataset file `metro-network.json` has four top-level sections:

```json
{
  "network": { ... },      // Metadata about the network
  "stations": [ ... ],     // All stations
  "lines": [ ... ],        // All metro lines
  "connections": [ ... ]   // All adjacent-station connections
}
```

## Station Model

Each station contains:

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique machine-readable identifier (UPPER_SNAKE_CASE) |
| `name` | string | Human-readable station name |
| `latitude` | number | Geographic latitude (decimal degrees, WGS84) |
| `longitude` | number | Geographic longitude (decimal degrees, WGS84) |
| `lines` | string[] | List of line IDs serving this station |
| `interchange` | boolean | `true` if the station serves more than one line |

**Example:**
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

## Line Model

Each line contains:

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique line identifier |
| `name` | string | Human-readable line name |
| `color` | string | Visual colour code (hex) |
| `stations` | string[] | Ordered list of station IDs (north-to-south or west-to-east) |

**Example:**
```json
{
  "id": "YELLOW",
  "name": "Yellow Line",
  "color": "#FFCC00",
  "stations": ["SAMAYPUR_BADLI", "...", "HAUZ_KHAS"]
}
```

## Connection Model

Each connection represents an edge between two adjacent stations on the same line:

| Field | Type | Description |
|-------|------|-------------|
| `from` | string | Source station ID |
| `to` | string | Destination station ID |
| `line` | string | Line ID this connection belongs to |
| `distanceKm` | number | Distance in kilometres (Haversine) |
| `travelTimeMinutes` | int | Estimated travel time in minutes |
| `note` | string | *(optional)* Documents omitted intermediate stations |

Connections are **bidirectional** — the reverse direction uses the same distance and time.

**Example:**
```json
{
  "from": "NEW_DELHI",
  "to": "RAJIV_CHOWK",
  "line": "YELLOW",
  "distanceKm": 1.2,
  "travelTimeMinutes": 2
}
```

## Interchange Representation

An interchange is a station served by **more than one line**. The interchange flag is structurally derived from the `lines` array length:

- `lines.length > 1` → `interchange: true`
- `lines.length == 1` → `interchange: false`

Interchanges are **not** represented as separate travel edges. The future routing engine will handle line transfers at interchange stations as a distinct operation from inter-station movement.

## Coordinate Representation

- **Format:** Decimal degrees (WGS84)
- **Source:** OpenStreetMap Overpass API
- **Range (Delhi):** Latitude ~28.4–28.9°N, Longitude ~76.8–77.5°E
- **Use case:** Will support A\* heuristic distance calculations in Phase 3+

## Current Dataset Statistics

| Metric | Count |
|--------|-------|
| Stations | 42 |
| Lines | 2 (Yellow, Blue) |
| Connections | 41 |
| Interchange stations | 1 (Rajiv Chowk) |

## Validation

Run the validation tests from the backend:

```bash
cd backend
mvn test -Dtest=com.metromind.data.MetroNetworkValidationTest
```

Validates:
- Unique station IDs
- Valid coordinates
- Referential integrity (all line/station references resolve)
- Connection adjacency (connections are between consecutive stations on the same line)
- Positive distances and travel times
- Interchange flag consistency
- Network metadata consistency
