package com.metromind.graph;

import com.metromind.data.Connection;
import com.metromind.data.MetroNetwork;
import com.metromind.data.Station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link MetroGraph} from a {@link MetroNetwork}.
 *
 * <p>The transformation:</p>
 *
 * <pre>
 * MetroNetwork
 *      ↓
 * MetroGraphBuilder
 *      ↓
 * MetroGraph (adjacency list)
 * </pre>
 *
 * <p>Every station becomes a vertex. Every raw {@link Connection} becomes a
 * directed {@link GraphEdge} in the source station's adjacency list, plus an
 * equal reverse edge, because metro movement is bidirectional.</p>
 *
 * <p><b>Duplicate-edge policy:</b> exact duplicate edges (same destination,
 * line, distance and travel time) produced by repeated source records are
 * deduplicated. Legitimate parallel edges between the same two stations on
 * <em>different</em> lines are preserved, because future routing needs the line
 * information.</p>
 *
 * <p><b>Self-loop policy:</b> a connection whose source and destination are the
 * same station is rejected with an {@link IllegalArgumentException}; such a
 * record represents no travel and would corrupt adjacency lists.</p>
 */
public final class MetroGraphBuilder {

    private MetroGraphBuilder() {
    }

    /**
     * Builds a {@link MetroGraph} from a validated {@link MetroNetwork}.
     *
     * @param network the validated metro network data
     * @return a read-only weighted graph
     * @throws NullPointerException          if {@code network} is null
     * @throws IllegalArgumentException if a connection is a self-loop
     */
    public static MetroGraph from(MetroNetwork network) {
        if (network == null) {
            throw new NullPointerException("network must not be null");
        }

        Map<String, Set<GraphEdge>> adjacency = new LinkedHashMap<>();

        // 1. Add every station as a vertex.
        for (Station station : network.getStations()) {
            adjacency.computeIfAbsent(station.getId(), k -> new LinkedHashSet<>());
        }

        // 2. Process every connection bidirectionally.
        for (Connection connection : network.getConnections()) {
            if (connection.getFrom().equals(connection.getTo())) {
                throw new IllegalArgumentException(
                        "Self-loop connection rejected: " + connection.getFrom()
                                + " -> " + connection.getTo() + " on line " + connection.getLine());
            }

            GraphEdge forward = new GraphEdge(
                    connection.getTo(),
                    connection.getLine(),
                    connection.getDistanceKm(),
                    connection.getTravelTimeMinutes());

            GraphEdge reverse = new GraphEdge(
                    connection.getFrom(),
                    connection.getLine(),
                    connection.getDistanceKm(),
                    connection.getTravelTimeMinutes());

            adjacency.computeIfAbsent(connection.getFrom(), k -> new LinkedHashSet<>()).add(forward);
            adjacency.computeIfAbsent(connection.getTo(), k -> new LinkedHashSet<>()).add(reverse);
        }

        // 3. Freeze each adjacency list into an unmodifiable list.
        Map<String, List<GraphEdge>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Set<GraphEdge>> entry : adjacency.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return new MetroGraph(frozen);
    }
}