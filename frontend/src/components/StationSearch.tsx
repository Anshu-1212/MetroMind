import { useMemo, useRef, useState } from 'react'
import type { StationView } from '../network/graph'

interface StationSearchProps {
  stations: StationView[]
  /** Currently chosen station id, or null when nothing is picked. */
  valueId: string | null
  /** Called when the user picks a station, or clears the selection. */
  onChange: (id: string | null) => void
  placeholder: string
  label: string
}

const MAX_SUGGESTIONS = 6

/**
 * A station search box backed by the real dataset.
 *
 * <p>Matching is a simple case-insensitive substring filter over the dataset's
 * station names — a deliberate, lightweight stand-in: the authoritative
 * station-name search lives in the backend {@code StationTrie} (Phase 7) and is
 * not reachable from the browser yet. This box reimplements <b>no</b> data
 * structure; it linear-filters the same 42 names rather than pretending to be
 * the Trie.</p>
 *
 * <p>Type to filter; click a suggestion (or press Enter) to commit. The box
 * shows the picked station's display name; an ✕ clears the selection.</p>
 */
export default function StationSearch({
  stations,
  valueId,
  onChange,
  placeholder,
  label,
}: StationSearchProps) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  const picked = stations.find((s) => s.id === valueId) ?? null
  const isSearching = query.trim().length > 0 && !picked

  const suggestions = useMemo(() => {
    if (!isSearching) return []
    const q = query.trim().toLocaleLowerCase()
    return stations
      .filter((s) => s.name.toLocaleLowerCase().includes(q))
      .sort((a, b) => a.name.localeCompare(b.name))
      .slice(0, MAX_SUGGESTIONS)
  }, [isSearching, query, stations])

  function commit(id: string) {
    const station = stations.find((s) => s.id === id)
    setQuery(station?.name ?? '')
    onChange(station ? station.id : null)
    setOpen(false)
  }

  return (
    <div className="station-search" ref={rootRef}>
      <label className="station-search-label">{label}</label>
      <div className="station-search-input-wrap">
        <input
          type="text"
          className="station-search-input"
          value={picked ? picked.name : query}
          placeholder={placeholder}
          aria-label={label}
          onChange={(e) => {
            setQuery(e.target.value)
            setOpen(true)
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => {
            // Let the click on a suggestion land before the box closes.
            window.setTimeout(() => setOpen(false), 120)
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && suggestions.length > 0) {
              e.preventDefault()
              commit(suggestions[0].id)
            }
            if (e.key === 'Escape') setOpen(false)
          }}
        />
        {picked && (
          <button
            type="button"
            className="station-search-clear"
            aria-label={`Clear ${label}`}
            onClick={() => {
              setQuery('')
              onChange(null)
            }}
          >
            ✕
          </button>
        )}
      </div>
      {open && isSearching && (
        <ul className="station-search-suggestions" role="listbox">
          {suggestions.length === 0 ? (
            <li className="station-search-empty">No stations match “{query}”</li>
          ) : (
            suggestions.map((s) => (
              <li key={s.id}>
                <button
                  type="button"
                  role="option"
                  className="station-search-option"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => commit(s.id)}
                >
                  <span>{s.name}</span>
                  {s.interchange && <span className="station-search-interchange">↔</span>}
                </button>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  )
}