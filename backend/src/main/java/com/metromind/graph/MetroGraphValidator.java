package com.metromind.graph;

import com.metromind.data.MetroNetwork;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural validation for a {@link MetroGraph} built from a {@link MetroNetwork}.
 *
 * <p>Checks graph-level invariants without performing any route finding:
 * vertex/edge counts, referential integrity, positive weights, bidirectional
 * symmetry, preservation of legitimate parallel edges, and absence of accidental
 * duplicates or self-loops.</p>
 */
public final class MetroGraphValidator {

    private MetroGraphValidator() {
    }

    /**
     * Validates graph invariants against the source network data.
     *
     * @param network the source {@link MetroNetwork}
     * @param graph   the {@link MetroGraph} built from it
     * @return a list of violation messages; empty if the graph is valid
     */
    public static List<String> validate(MetroNetwork network, MetroGraph graph) {
        List<String> violations = new ArrayList<>();

        // Station (vertex) preservation.
        Set<String> stationIds = new HashSet<>(network.getStations().stream()
                .map(s -> s.getId())
                .toList());
        if (graph.getStationCount() != stationIds.size()) {
            violations.add("Station count mismatch: graph=" + graph.getStationCount()
                    + ", network=" + stationIds.size());
        }
        for (String stationId : stationIds) {
            if (!graph.containsStation(stationId)) {
                violations.add("Missing graph vertex for station: " + stationId);
            }
        }

        Set<String> lineIds = new HashSet<>(network.getLines().stream()
                .map(l -> l.getId())
                .toList());

        // Edge-level checks.
        long totalEdges = 0;
        for (String source : graph.getStationIds()) {
            List<GraphEdge> edges = graph.getOutgoingEdges(source);
            totalEdges += edges.size();

            for (GraphEdge edge : edges) {
                if (!graph.containsStation(edge.getDestination())) {
                    violations.add("Edge " + source + " -> " + edge.getDestination()
                            + " points at a non-existent station");
                }
                if (source.equals(edge.getDestination())) {
                    violations.add("Self-loop edge found at station " + source);
                }
                if (!lineIds.contains(edge.getLine())) {
                    violations.add("Edge " + source + " -> " + edge.getDestination()
                            + " references unknown line: " + edge.getLine());
                }
                if (edge.getDistanceKm() <= 0) {
                    violations.add("Non-positive distance on edge " + source + " -> "
                            + edge.getDestination());
                }
                if (edge.getTravelTimeMinutes() <= 0) {
                    violations.add("Non-positive travel time on edge " + source + " -> "
                            + edge.getDestination());
                }
            }
        }
        if (totalEdges != graph.getEdgeCount()) {
            violations.add("Edge count mismatch: recorded=" + graph.getEdgeCount()
                    + ", actual=" + totalEdges);
        }

        // Bidirectional symmetry: every directed edge must have a reverse edge
        // with the same line and weights.
        for (String source : graph.getStationIds()) {
            for (GraphEdge edge : graph.getOutgoingEdges(source)) {
                boolean hasReverse = graph.getOutgoingEdges(edge.getDestination()).stream()
                        .anyMatch(r -> r.getDestination().equals(source)
                                && r.getLine().equals(edge.getLine())
                                && r.getDistanceKm() == edge.getDistanceKm()
                                && r.getTravelTimeMinutes() == edge.getTravelTimeMinutes());
                if (!hasReverse) {
                    violations.add("Missing reverse edge for " + source + " -> "
                            + edge.getDestination() + " on line " + edge.getLine());
                }
            }
        }

        // No accidental duplicate edges (exact same destination/line/weights twice).
        long expectedConnections = network.getConnections().size();
        long expectedDirectedEdges = expectedConnections * 2;
        if (graph.getEdgeCount() != expectedDirectedEdges) {
            violations.add("Directed edge count should be 2x connections (" + expectedDirectedEdges
                    + "), found " + graph.getEdgeCount() + " — check for duplicate or missing edges");
        }

        return violations;
    }
}