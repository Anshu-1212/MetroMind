/**
 * Loads the shared metro dataset and exposes it as a ready-to-render
 * {@link NetworkView}.
 *
 * <p><strong>Single source of truth.</strong> This module imports
 * {@code data/metro-network.json} directly (Vite bundles the file into the JS
 * at build time), so there is exactly one copy of the network data in the
 * repository — the same file the Phase 2 backend loads. The frontend never
 * keeps a duplicate; it consumes the identical dataset.</p>
 *
 * <p>The JSON is imported as an ambient module via {@code resolveJsonModule} and
 * validated up front before a {@link NetworkView} is derived from it. If loading
 * or validation ever fails, a single {@link Error} with a clear message is
 * thrown rather than a half-populated map being rendered.</p>
 */

import type { NetworkFile } from './types'
import { buildNetworkView, type NetworkView } from './graph'

// The dataset is the authoritative document. Vite resolves and bundles it at
// build time; the URL is relative to this module (frontend/src/network/).
import metroNetworkFile from '../../../data/metro-network.json'

/**
 * The parsed, validated network document.
 *
 * <p>Exposed for callers that want to read raw records (e.g. tests); the map
 * components use {@link #loadNetwork} instead.</p>
 */
export const rawNetworkFile: NetworkFile = metroNetworkFile as unknown as NetworkFile

/**
 * Load the network once and derive its {@link NetworkView}.
 *
 * <p>This is the entry point the App calls (typically in a {@code useMemo}).
 * It validates the document before deriving; on malformed data it throws a
 * {@link TypeError} describing the problem.</p>
 */
export function loadNetwork(): NetworkView {
  return buildNetworkView(rawNetworkFile)
}
