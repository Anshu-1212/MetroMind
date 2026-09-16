# MetroMind

Intelligent Metro Route Planner

## Overview

MetroMind is a full-stack web application that models a metro network as a weighted graph and provides intelligent route planning. It combines graph-based pathfinding algorithms with an interactive user interface to help commuters find the best routes through a metro system.

The project currently contains a validated Delhi Metro dataset (42 stations, 2 lines), a graph representation built from that data, and two routing algorithms: BFS (minimum station hops) and Dijkstra (minimum distance or minimum travel time).

## Implemented Algorithms

- **BFS** — minimum station hops
- **Dijkstra** — minimum distance (`RouteMetric.DISTANCE`)
- **Dijkstra** — minimum travel time (`RouteMetric.TRAVEL_TIME`)

## Planned Features

> These features are planned for future phases and are **not yet implemented**.

- **A\* pathfinding** — heuristic-guided optimal routing
- **Cheapest routing** — minimum-fare path computation
- **Minimum-interchange routing** — routes that minimize line changes
- **Trie-based station autocomplete** — fast, prefix-based station search
- **Interactive metro map** — visual network representation
- **Algorithm visualization** — step-by-step rendering of pathfinding algorithms
- **Route comparison** — side-by-side evaluation of multiple route options
- **Service disruption handling** — dynamic rerouting around closed lines or stations
- **Route analytics** — statistics on travel time, distance, and cost

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | React, TypeScript, Vite |
| Backend | Java, Spring Boot, Maven |
| Algorithms | Graph Algorithms (BFS, Dijkstra, A\*) |
| Data Format | JSON |

## Project Structure

```
MetroMind/
├── frontend/       React frontend (TypeScript + Vite)
├── backend/        Spring Boot backend (Java + Maven)
├── data/           Metro network data (JSON)
├── docs/           Documentation and diagrams
├── .gitignore
└── README.md
```

## Local Development

### Frontend

```bash
cd frontend
npm install
npm run dev        # Start development server
npm run build      # Production build
```

### Backend

```bash
cd backend
mvn spring-boot:run   # Start the server
mvn clean package     # Build the JAR
```

The backend runs at `http://localhost:8080`. Verify with:

```bash
curl http://localhost:8080/api/health
```

## Current Status

**Phase 5 — Dijkstra Routing**

The project now contains:
- A validated Delhi Metro dataset (42 stations, 2 lines, 41 connections)
- A graph representation built from that data (bidirectional weighted adjacency list)
- A BFS minimum-hop routing algorithm (Phase 4)
- A Dijkstra routing algorithm with two independent objectives:
  - minimum distance (kilometres)
  - minimum travel time (minutes)

No A\*, Trie, REST route API, or frontend route UI are implemented yet.
