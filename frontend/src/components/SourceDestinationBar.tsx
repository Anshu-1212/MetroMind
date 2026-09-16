import type { StationView } from '../network/graph'
import StationSearch from './StationSearch'

interface SourceDestinationBarProps {
  stations: StationView[]
  sourceId: string | null
  destinationId: string | null
  onSourceChange: (id: string | null) => void
  onDestinationChange: (id: string | null) => void
  onSwap: () => void
  onReset: () => void
}

/**
 * Pick start and destination stations, swap them, or reset both.
 *
 * <p>Selection state lives in the parent (App); this bar only wires the two
 * search boxes to it and exposes swap/reset affordances. Route computation is
 * deliberately <em>not</em> started here — the parent decides when both ends
 * are chosen.</p>
 */
export default function SourceDestinationBar({
  stations,
  sourceId,
  destinationId,
  onSourceChange,
  onDestinationChange,
  onSwap,
  onReset,
}: SourceDestinationBarProps) {
  const bothChosen = sourceId !== null && destinationId !== null

  return (
    <div className="source-destination-bar">
      <StationSearch
        label="From"
        stations={stations}
        valueId={sourceId}
        onChange={onSourceChange}
        placeholder="Departure station…"
      />
      <button
        type="button"
        className="swap-button"
        aria-label="Swap source and destination"
        title="Swap source and destination"
        onClick={onSwap}
        disabled={!bothChosen}
      >
        ⇅
      </button>
      <StationSearch
        label="To"
        stations={stations}
        valueId={destinationId}
        onChange={onDestinationChange}
        placeholder="Arrival station…"
      />
      <button
        type="button"
        className="reset-button"
        onClick={onReset}
        disabled={!bothChosen && sourceId === null}
      >
        Reset
      </button>
    </div>
  )
}