package com.metromind.search;

import com.metromind.data.MetroNetwork;
import com.metromind.data.Station;

import java.util.List;

/**
 * Builds a {@link StationTrie} over the station names of a {@link MetroNetwork}.
 *
 * <p>The integration-friendly link between the raw dataset and the search trie:</p>
 *
 * <pre>
 * MetroNetwork
 *       ↓
 * StationSearchIndex
 *       ↓
 * StationTrie
 * </pre>
 *
 * <p>This is the only component that reads station data: it extracts
 * {@link Station#getName()} from every station and inserts it into the trie. It
 * deliberately does <b>not</b> load {@code metro-network.json} itself — callers
 * hand it an already-deserialised {@link MetroNetwork} (e.g. from
 * {@link com.metromind.data.MetroNetworkLoader}). The trie itself remains
 * independent of Spring and the filesystem.</p>
 *
 * <p>Search and routing stay completely separate. This index produces station
 * <b>names</b>; routing (BFS/Dijkstra/A*) operates on station <b>IDs</b> through
 * {@link com.metromind.graph.MetroGraph}. Nothing here references the routing
 * package.</p>
 */
public final class StationSearchIndex {

    private final StationTrie trie = new StationTrie();

    /**
     * Builds a search index from every station name in the network.
     *
     * <p>Null station entries and null/blank names are skipped defensively so a
     * single malformed record cannot prevent the rest of the index from
     * building. The dataset itself contains no such entries.</p>
     *
     * @param network the network whose station names should be indexed;
     *                must not be null
     * @throws NullPointerException if {@code network} is {@code null}
     */
    public StationSearchIndex(MetroNetwork network) {
        if (network == null) {
            throw new NullPointerException("network must not be null");
        }
        if (network.getStations() != null) {
            for (Station station : network.getStations()) {
                if (station == null) {
                    continue;
                }
                String name = station.getName();
                if (name == null || name.isBlank()) {
                    continue;
                }
                trie.insert(name);
            }
        }
    }

    /**
     * @return the underlying trie, for callers that need direct access
     */
    public StationTrie getTrie() {
        return trie;
    }

    /**
     * Inserts an extra station name (e.g. a synonym not present in the dataset).
     *
     * @param stationName the name to insert
     * @see StationTrie#insert(String)
     */
    public void insert(String stationName) {
        trie.insert(stationName);
    }

    /**
     * @see StationTrie#contains(String)
     */
    public boolean contains(String stationName) {
        return trie.contains(stationName);
    }

    /**
     * @see StationTrie#startsWith(String)
     */
    public boolean startsWith(String prefix) {
        return trie.startsWith(prefix);
    }

    /**
     * @see StationTrie#search(String)
     */
    public List<String> search(String prefix) {
        return trie.search(prefix);
    }

    /**
     * @see StationTrie#suggest(String, int)
     */
    public List<String> suggest(String prefix, int limit) {
        return trie.suggest(prefix, limit);
    }

    /**
     * @return the number of unique station names indexed
     */
    public int size() {
        return trie.size();
    }

    /**
     * @return {@code true} if no station names are indexed
     */
    public boolean isEmpty() {
        return trie.isEmpty();
    }
}