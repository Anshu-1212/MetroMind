package com.metromind.graph;

import com.metromind.data.MetroNetwork;
import com.metromind.data.MetroNetworkLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: transforms the real Phase 2 Delhi Metro dataset
 * ({@code data/metro-network.json}) into a {@link MetroGraph}.
 *
 * <p>Expected counts are derived from the dataset itself rather than hard-coded
 * elsewhere, so the test stays valid if the dataset grows in a later phase.</p>
 */
class RealDatasetGraphTest {

    @Test
    void realDatasetBuildsIntoValidGraph() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();

        MetroGraph graph = MetroGraphBuilder.from(network);

        int stationCount = network.getStations().size();
        int connectionCount = network.getConnections().size();

        // Vertex count must match the dataset's station count.
        assertEquals(stationCount, graph.getStationCount(),
                "Vertex count must equal dataset station count");

        // The dataset has no reverse-pair records and no duplicate records,
        // so the bidirectional expansion yields exactly 2x connections.
        assertEquals(connectionCount * 2, graph.getEdgeCount(),
                "Directed edge count must equal 2x connections (bidirectional)");

        // Every station is a vertex.
        for (String id : graph.getStationIds()) {
            assertTrue(network.getStations().stream()
                    .anyMatch(s -> s.getId().equals(id)),
                    "Graph vertex has no matching station: " + id);
        }

        // Full structural validation passes.
        List<String> violations = MetroGraphValidator.validate(network, graph);
        assertTrue(violations.isEmpty(), "Graph validation failed: " + violations);
    }

    @Test
    void realDatasetContainsInterchangeVertex() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        MetroGraph graph = MetroGraphBuilder.from(network);

        // Rajiv Chowk serves both the Yellow and Blue lines in the dataset.
        assertTrue(graph.containsStation("RAJIV_CHOWK"),
                "Interchange station RAJIV_CHOWK must be a graph vertex");

        // Its outgoing edges must belong to both lines (line metadata preserved).
        // Rajiv Chowk has 2 neighbours on the Yellow Line and 2 on the Blue Line.
        var neighbours = graph.getOutgoingEdges("RAJIV_CHOWK");
        assertEquals(4, neighbours.size());

        var lines = neighbours.stream().map(GraphEdge::getLine).toList();
        assertEquals(2, lines.stream().filter("YELLOW"::equals).count(),
                "Expected 2 outgoing Yellow Line edges at Rajiv Chowk");
        assertEquals(2, lines.stream().filter("BLUE"::equals).count(),
                "Expected 2 outgoing Blue Line edges at Rajiv Chowk");

        // Destinations span both lines.
        var destinations = neighbours.stream().map(GraphEdge::getDestination).toList();
        assertEquals(4, destinations.stream().distinct().count(),
                "Rajiv Chowk connects to 4 distinct neighbours in the dataset");
    }
}