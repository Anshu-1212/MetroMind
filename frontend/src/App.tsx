import { useMemo, useState } from 'react'
import './App.css'
import { loadNetwork } from './network/loadNetwork'
import type { NetworkView } from './network/graph'
import { bfs, dijkstra, aStar, type RouteDemand, type RouteResult as RoutingResult } from './network/routing'
import StationMap from './components/StationMap'
import SourceDestinationBar from './components/SourceDestinationBar'
import RouteResult from './components/RouteResult'
import AlgorithmPanel from './components/AlgorithmPanel'
import StatisticsPanel from './components/StatisticsPanel'

type AlgorithmKey = 'bfs' | 'dijkstra-distance' | 'dijkstra-time' | 'astar'

interface AlgorithmSummaries {
  runs: Array<{
    key: string
    name: string
    objective: string
    result: RoutingResult | null
  }>
  active: RoutingResult | null
}

function App() {
  // Load the shared dataset once; build the derived network view once.
  const view: NetworkView = useMemo(() => loadNetwork(), [])

  const [sourceId, setSourceId] = useState<string | null>(null)
  const [destinationId, setDestinationId] = useState<string | null>(null)
  const [activeKey, setActiveKey] = useState<AlgorithmKey>('bfs')

  const stations = useMemo(() => Array.from(view.stations.values()), [view])

  const summaries: AlgorithmSummaries | null = useMemo(() => {
    if (sourceId === null || destinationId === null || sourceId === destinationId) {
      return null
    }
    const demand: RouteDemand = { sourceId, destinationId }
    const runs = [
      { key: 'bfs', name: 'BFS', objective: 'minimum hops', result: bfs(view, demand) },
      { key: 'dijkstra-distance', name: 'Dijkstra (distance)', objective: 'minimum distance', result: dijkstra(view, demand, 'distanceKm') },
      { key: 'dijkstra-time', name: 'Dijkstra (time)', objective: 'minimum travel time', result: dijkstra(view, demand, 'travelTimeMinutes') },
      { key: 'astar', name: 'A*', objective: 'minimum distance (heuristic)', result: aStar(view, demand) },
    ]
    const active = runs.find((r) => r.key === activeKey)?.result ?? runs[0].result
    return { runs, active }
  }, [view, sourceId, destinationId, activeKey])

  const activeRouteIds = useMemo(() => summaries?.active?.stationIds ?? [], [summaries])

  function pickStation(id: string) {
    if (sourceId === id) {
      setSourceId(null)
    } else if (destinationId === id) {
      setDestinationId(null)
    } else if (sourceId === null) {
      setSourceId(id)
    } else {
      setDestinationId(id)
    }
  }

  function resetAll() {
    setSourceId(null)
    setDestinationId(null)
    setActiveKey('bfs')
  }

  function swap() {
    setSourceId(destinationId)
    setDestinationId(sourceId)
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>MetroMind</h1>
        <p className="subtitle">Intelligent Metro Route Planner</p>
        <span className="network-badge">{view.name}</span>
      </header>

      <main className="layout">
        <section className="controls-column">
          <SourceDestinationBar
            stations={stations}
            sourceId={sourceId}
            destinationId={destinationId}
            onSourceChange={setSourceId}
            onDestinationChange={setDestinationId}
            onSwap={swap}
            onReset={resetAll}
          />

          {(summaries?.active && summaries.active.found) && (
            <section className="route-panel">
              <header className="panel-heading">
                <h2>Route</h2>
                <span className="route-source-target">
                  {view.stations.get(sourceId!)?.name} → {view.stations.get(destinationId!)?.name}
                </span>
              </header>
              <RouteResult view={view} result={summaries.active} />
            </section>
          )}

          <AlgorithmPanel
            view={view}
            runs={summaries?.runs ?? []}
            activeKey={activeKey}
            onActivate={(key) => setActiveKey(key as AlgorithmKey)}
          />
        </section>

        <section className="map-column">
          <StationMap
            view={view}
            selectedId={null}
            sourceId={sourceId}
            destinationId={destinationId}
            routeIds={activeRouteIds}
            onSelect={pickStation}
          />
        </section>

        <section className="stats-column">
          <StatisticsPanel
            view={view}
            raw={{
              totalStations: view.stations.size,
              totalConnections: Math.floor(view.edges.length / 2),
              totalLines: view.lineOrder.length,
            }}
            result={summaries?.active ?? null}
          />
        </section>
      </main>
    </div>
  )
}

export default App