package com.metromind.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An in-memory weighted graph representing a metro network.
 *
 * <p>The graph uses an adjacency-list representation: a map from station ID
 * (vertex) to the list of outgoing {@link GraphEdge}s. Metro movement is
 * bidirectional, so every connection produces one edge in each direction.</p>
 *
 * <p>The graph is read-only after construction. The internal adjacency map is
 * never exposed; callers receive unmodifiable views.</p>
 *
 * <p>Space complexity is O(V + E), where V is the number of stations (vertices)
 * and E is the number of directed edges.</p>
 */
public final class MetroGraph {

    private final Map<String, List<GraphEdge>> adjacencyList;
    private final int edgeCount;

    /**
     * Package-private — graphs are created exclusively by {@link MetroGraphBuilder}.
     */
    MetroGraph(Map<String, List<GraphEdge>> adjacencyList) {
        this.adjacencyList = Collections.unmodifiableMap(
                new LinkedHashMap<>(adjacencyList));
        this.edgeCount = adjacencyList.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * @return the set of all station IDs present as vertices in the graph
     */
    public Set<String> getStationIds() {
        return Collections.unmodifiableSet(adjacencyList.keySet());
    }

    /**
     * @return the number of stations (vertices) in the graph
     */
    public int getStationCount() {
        return adjacencyList.size();
    }

    /**
     * @return the number of directed edges in the graph
     */
    public int getEdgeCount() {
        return edgeCount;
    }

    /**
     * @param stationId the station ID to check
     * @return {@code true} if the station exists as a vertex in the graph
     */
    public boolean containsStation(String stationId) {
        return adjacencyList.containsKey(stationId);
    }

    /**
     * Returns the outgoing edges from the given station.
     *
     * <p>For an unknown station ID, an empty list is returned rather than an
     * exception, so future routing algorithms can iterate neighbours without
     * defensive exception handling for valid stations. Callers can use
     * {@link #containsStation(String)} to distinguish an isolated vertex from
     * a non-existent one.</p>
     *
     * @param stationId the source station ID
     * @return an unmodifiable list of outgoing {@link GraphEdge}s
     */
    public List<GraphEdge> getNeighbors(String stationId) {
        List<GraphEdge> edges = adjacencyList.get(stationId);
        return edges == null ? List.of() : edges;
    }

    /**
     * Alias for {@link #getNeighbors(String)}.
     *
     * @param stationId the source station ID
     * @return an unmodifiable list of outgoing {@link GraphEdge}s
     */
    public List<GraphEdge> getOutgoingEdges(String stationId) {
        return getNeighbors(stationId);
    }
}