# MetroMind — Data Sources

This document describes where the metro network data came from, how it was processed, and what assumptions were made.

## Data Sources Consulted

### 1. Station Names and Line Assignments

- **Source:** Wikipedia — "List of Delhi Metro stations"
- **URL:** https://en.wikipedia.org/wiki/List_of_Delhi_Metro_stations
- **What was obtained:**
  - Complete list of all Delhi Metro stations
  - Line membership for each station
  - Station ordering within each line
  - Interchange station identification (marked with `*` on Wikipedia)
  - Terminal station identification
- **Date checked:** September 2026
- **Confidence:** High — Wikipedia's Delhi Metro station list is actively maintained and widely cross-referenced

### 2. Geographic Coordinates

- **Source:** OpenStreetMap via Overpass API
- **URL:** https://overpass-api.de/api/interpreter
- **Query used:**
  ```
  [out:json][timeout:30];
  node["station"="subway"]["network"~"Delhi Metro"](area.delhi);
  out body;
  ```
- **What was obtained:**
  - Latitude and longitude (decimal degrees, WGS84) for each station
  - Coordinates are from OSM contributors and verified through community editing
- **Date checked:** September 2026
- **Confidence:** High for core stations; coordinates are community-verified and frequently updated

### 3. Distances Between Stations

- **Method:** Haversine formula applied to station coordinates
- **Formula:** Great-circle distance between consecutive station coordinates
- **Confidence:** Derived data — Haversine gives straight-line distance, which is an approximation of actual rail distance (actual track distance is typically 10–20% longer due to curves and alignment)
- **Used for:** Establishing relative scale; actual rail distances may differ

### 4. Travel Times Between Stations

- **Method:** Estimated using average operational speed of ~33 km/h (Delhi Metro's typical average speed including acceleration, deceleration, and dwell time at stations)
- **Confidence:** Estimated — based on published total line travel times from DMRC
- **Yellow Line (Samaypur Badli → Huda City Centre):** ~65 minutes for ~45 km → average ~41 km/h
- **Blue Line (Dwarka Sector 21 → Noida Electronic City):** ~80 minutes for ~53 km → average ~40 km/h
- **Used for:** Relative comparison; actual times vary by time of day and operational conditions

## What Was NOT Consulted

- DMRC official fare charts (not publicly available in machine-readable format)
- Real-time train scheduling data
- Track geometry or alignment data
- Official DMRC GTFS feed (not publicly released as of this writing)

## Dataset Scope

The dataset covers a **subset** of the Delhi Metro network:

| Line | Stations in Dataset | Total Stations (DMRC) | Coverage |
|------|--------------------|-----------------------|----------|
| Yellow Line | 21 | 37 | ~57% |
| Blue Line | 22 | 50+ | ~44% |
| **Total (unique)** | **42** | **243+** | ~17% |

### Omitted Stations

The following stations are present on the actual lines but omitted from this starter dataset:

**Blue Line — omitted between Janakpuri West and Patel Nagar (`JANAKPURI_WEST → PATEL_NAGAR` connection):**
- Rajouri Garden
- Other intermediate stations in west/south-west Delhi

**Blue Line — omitted between Kirti Nagar and Ramakrishna Ashram Marg (`KIRTI_NAGAR → RAMAKRISHNA_ASHRAM_MARG` connection):**
- Ashok Park Main
- Other intermediate stations

**Blue Line — omitted between Karol Bagh and Anand Vihar (`RAJIV_CHOWK → ANAND_VIHAR` connection):**
- Barakhamba Road
- Mandi House
- Pragati Maidan (Supreme Court)
- Indraprastha
- Yamuna Bank
- Laxmi Nagar
- Preet Vihar
- Karkarduma
- And several others

These omissions are documented to ensure data integrity. The `note` field on affected connections flags where gaps exist. When the full network is built, these connections should be split at the actual intermediate stations.

## Derived Values

| Value | Derivation | Confidence |
|-------|-----------|------------|
| `distanceKm` | Haversine formula from coordinates | Medium — approximates rail distance |
| `travelTimeMinutes` | Estimated from average speed | Low — used for relative comparison |
| `interchange` flag | Derived from `lines` array length > 1 | High — structurally derived |
| Line colors | Visual convention (Yellow = #FFCC00, Blue = #0078BF) | Informational — not official DMRC colors |

## Assumptions

1. **Bidirectional movement** — all connections are treated as bidirectional unless noted otherwise
2. **No interchange penalty** — changing lines at an interchange station is not modelled as a separate connection; the routing engine will handle this in a later phase
3. **Station IDs are stable** — machine-readable IDs use UPPER_SNAKE_CASE and are not expected to change
4. **Coordinates are WGS84** — standard GPS coordinate system
5. **Single-track assumption** — each connection represents one direction; the reverse direction uses the same distance and time
