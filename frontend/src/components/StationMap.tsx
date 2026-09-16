import { useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent, WheelEvent as ReactWheelEvent } from 'react'
import type { NetworkView } from '../network/graph'

interface StationMapProps {
  view: NetworkView
  /** Station currently hovered-selected on the map. */
  selectedId: string | null
  sourceId: string | null
  destinationId: string | null
  /** Station ids on the active route, used to highlight the path. */
  routeIds: string[]
  onSelect: (id: string) => void
}

/** Zoom/pan view state. */
interface ViewTransform {
  tx: number
  ty: number
  scale: number
}

const MIN_SCALE = 0.5
const MAX_SCALE = 6

/**
 * Interactive SVG metro map.
 *
 * <p>Renders the {@link NetworkView} derived from the real dataset: stations as
 * nodes, connections as edges coloured by their line. Supports wheel/drag zoom
 * and pan, click-to-select, and highlights a provided route along the network.
 * Pure and presentational — all geometry comes from {@code view}; nothing here
 * touches the raw JSON or the backend.</p>
 */
export default function StationMap({
  view,
  selectedId,
  sourceId,
  destinationId,
  routeIds,
  onSelect,
}: StationMapProps) {
  const [transform, setTransform] = useState<ViewTransform>({ tx: 0, ty: 0, scale: 1 })
  const drag = useRef<{ startX: number; startY: number; tx: number; ty: number } | null>(null)

  const routeSet = new Set(routeIds)

  function zoomBy(factor: number) {
    setTransform((t) => {
      const scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, t.scale * factor))
      return { ...t, scale }
    })
  }

  function onWheel(event: ReactWheelEvent<SVGSVGElement>) {
    const factor = event.deltaY > 0 ? 1 / 1.15 : 1.15
    zoomBy(factor)
  }

  function onPointerDown(event: ReactPointerEvent<SVGSVGElement>) {
    if (event.target instanceof SVGCircleElement) return
    drag.current = {
      startX: event.clientX,
      startY: event.clientY,
      tx: transform.tx,
      ty: transform.ty,
    }
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  function onPointerMove(event: ReactPointerEvent<SVGSVGElement>) {
    if (!drag.current) return
    setTransform((t) => ({
      ...t,
      tx: drag.current!.tx + (event.clientX - drag.current!.startX),
      ty: drag.current!.ty + (event.clientY - drag.current!.startY),
    }))
  }

  function onPointerUp() {
    drag.current = null
  }

  function resetView() {
    setTransform({ tx: 0, ty: 0, scale: 1 })
  }

  return (
    <div className="map-shell">
      <svg
        className="map-svg"
        viewBox="0 0 1000 800"
        role="img"
        aria-label="Metro network map"
        onWheel={onWheel}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerLeave={onPointerUp}
      >
        <g
          transform={`translate(${transform.tx} ${transform.ty}) scale(${transform.scale})`}
          style={{ transformOrigin: '0 0' }}
        >
          {/* Edges, coloured by line. */}
          {view.edges.map((edge, i) => {
            const a = view.stations.get(edge.from)
            const b = view.stations.get(edge.to)
            if (!a || !b) return null
            const line = view.lines.get(edge.line)
            return (
              <line
                key={`${edge.from}-${edge.to}-${i}`}
                x1={a.x}
                y1={a.y}
                x2={b.x}
                y2={b.y}
                stroke={line?.color ?? '#9aa1ad'}
                strokeWidth={4}
                strokeLinecap="round"
              />
            )
          })}

          {/* Route highlight overlay. */}
          {routeIds.length > 1 &&
            routeIds.slice(0, -1).map((from, i) => {
              const to = routeIds[i + 1]
              const a = view.stations.get(from)
              const b = view.stations.get(to)
              if (!a || !b) return null
              return (
                <line
                  key={`${from}-${to}`}
                  x1={a.x}
                  y1={a.y}
                  x2={b.x}
                  y2={b.y}
                  stroke="#b45309"
                  strokeWidth={10}
                  strokeLinecap="round"
                  opacity={0.35}
                />
              )
            })}

          {/* Stations as nodes. */}
          {Array.from(view.stations.values()).map((station) => {
            const isSource = station.id === sourceId
            const isDestination = station.id === destinationId
            const isSelected = station.id === selectedId
            const inRoute = routeSet.has(station.id)
            const r = station.interchange ? 9 : 6
            const color = isSelected
              ? '#b45309'
              : isSource
                ? '#16a34a'
                : isDestination
                  ? '#2563eb'
                  : station.interchange
                    ? '#6d28d9'
                    : '#0f172a'
            return (
              <g
                key={station.id}
                className={`map-station${inRoute ? ' in-route' : ''}`}
                onClick={() => onSelect(station.id)}
                style={{ cursor: 'pointer' }}
              >
                <circle
                  cx={station.x}
                  cy={station.y}
                  r={inRoute ? r + 6 : r + 4}
                  fill="rgba(255,255,255,0)"
                  stroke="none"
                />
                <circle
                  cx={station.x}
                  cy={station.y}
                  r={r}
                  fill={color}
                  stroke="#ffffff"
                  strokeWidth={2}
                />
                <text
                  x={station.x}
                  y={station.y - (r + 4)}
                  textAnchor="middle"
                  className="map-station-label"
                  style={{
                    fontWeight: station.interchange ? 700 : 500,
                    fill: color,
                    fontSize: station.interchange ? 13 : 11,
                  }}
                >
                  {station.name}
                </text>
              </g>
            )
          })}
        </g>
      </svg>
      <div className="map-legend">
        {view.lineOrder.map((id) => {
          const line = view.lines.get(id)
          if (!line) return null
          return (
            <span key={id} className="map-legend-item">
              <span className="map-legend-swatch" style={{ background: line.color }} />
              {line.name}
            </span>
          )
        })}
      </div>
      <div className="map-controls">
        <button type="button" onClick={() => zoomBy(1.4)} aria-label="Zoom in">
          +
        </button>
        <button type="button" onClick={() => zoomBy(1 / 1.4)} aria-label="Zoom out">
          −
        </button>
        <button type="button" onClick={resetView}>
          Reset view
        </button>
      </div>
    </div>
  )
}