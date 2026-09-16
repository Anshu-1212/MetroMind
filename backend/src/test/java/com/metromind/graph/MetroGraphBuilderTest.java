package com.metromind.graph;

import com.metromind.data.Connection;
import com.metromind.data.MetroLine;
import com.metromind.data.MetroNetwork;
import com.metromind.data.Station;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetroGraph} and {@link MetroGraphBuilder}
 * using small, hand-created networks.
 */
class MetroGraphBuilderTest {

    // ──────────────────────────────────────────────
    // Test helpers
    // ──────────────────────────────────────────────

    private static Station station(String id) {
        return new Station(id, id, 28.6, 77.2, List.of("YELLOW"), false);
    }

    private static Connection connection(String from, String to, String line,
                                         double km, int minutes) {
        return new Connection(from, to, line, km, minutes);
    }

    private static MetroNetwork network(List<Station> stations,
                                        List<MetroLine> lines,
                                        List<Connection> connections) {
        MetroNetwork nw = new MetroNetwork();
        nw.setStations(stations);
        nw.setLines(lines);
        nw.setConnections(connections);
        return nw;
    }

    private static MetroLine line(String id) {
        return new MetroLine(id, id + " Line", "#000000", List.of());
    }

    // ──────────────────────────────────────────────
    // 1. Empty network → empty graph
    // ──────────────────────────────────────────────

    @Test
    void emptyNetworkCreatesEmptyGraph() {
        MetroGraph graph = MetroGraphBuilder.from(network(List.of(), List.of(), List.of()));
        assertEquals(0, graph.getStationCount());
        assertEquals(0, graph.getEdgeCount());
        assertTrue(graph.getStationIds().isEmpty());
    }

    // ──────────────────────────────────────────────
    // 2. Single station → one vertex, zero edges
    // ──────────────────────────────────────────────

    @Test
    void singleStationCreatesOneVertexAndZeroEdges() {
        MetroGraph graph = MetroGraphBuilder.from(network(
                List.of(station("A")), List.of(line("YELLOW")), List.of()));
        assertEquals(1, graph.getStationCount());
        assertEquals(0, graph.getEdgeCount());
        assertEquals(0, graph.getNeighbors("A").size());
        assertTrue(graph.containsStation("A"));
    }

    // ──────────────────────────────────────────────
    // 3. Two stations, one connection → both directions
    // ──────────────────────────────────────────────

    @Test
    void twoStationsOneConnectionCreateBothDirections() {
        MetroNetwork nw = network(
                List.of(station("A"), station("B")),
                List.of(line("BLUE")),
                List.of(connection("A", "B", "BLUE", 1.2, 2)));

        MetroGraph graph = MetroGraphBuilder.from(nw);

        assertEquals(2, graph.getStationCount());
        assertEquals(2, graph.getEdgeCount());

        assertEquals(1, graph.getNeighbors("A").size());
        assertEquals("B", graph.getNeighbors("A").get(0).getDestination());

        assertEquals(1, graph.getNeighbors("B").size());
        assertEquals("A", graph.getNeighbors("B").get(0).getDestination());
    }

    // ──────────────────────────────────────────────
    // 4. Multiple connections represented correctly
    // ──────────────────────────────────────────────

    @Test
    void multipleConnectionsAreRepresented() {
        MetroNetwork nw = network(
                List.of(station("A"), station("B"), station("C")),
                List.of(line("BLUE")),
                List.of(
                        connection("A", "B", "BLUE", 1.0, 2),
                        connection("B", "C", "BLUE", 1.1, 2)));

        MetroGraph graph = MetroGraphBuilder.from(nw);

        assertEquals(3, graph.getStationCount());
        assertEquals(4, graph.getEdgeCount());
        assertEquals(1, graph.getNeighbors("A").size());
        assertEquals(2, graph.getNeighbors("B").size());
        assertEquals(1, graph.getNeighbors("C").size());
    }

    // ──────────────────────────────────────────────
    // 5. Every station becomes a vertex
    // ──────────────────────────────────────────────

    @Test
    void everyStationBecomesAVertex() {
        List<Station> stations = List.of(station("A"), station("B"), station("C"), station("D"));
        MetroNetwork nw = network(stations, List.of(line("BLUE")),
                List.of(connection("A", "B", "BLUE", 1.0, 2)));

        MetroGraph graph = MetroGraphBuilder.from(nw);

        assertEquals(4, graph.getStationCount());
        for (Station s : stations) {
            assertTrue(graph.containsStation(s.getId()), "Missing vertex: " + s.getId());
        }
    }

    // ──────────────────────────────────────────────
    // 6. Neighbour lookup works
    // ──────────────────────────────────────────────

    @Test
    void neighbourLookupWorks() {
        MetroNetwork nw = network(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(line("BLUE"), line("YELLOW")),
                List.of(
                        connection("A", "B", "BLUE", 1.0, 2),
                        connection("A", "C", "YELLOW", 2.0, 3)));

        MetroGraph graph = MetroGraphBuilder.from(nw);

        var neighboursA = graph.getNeighbors("A");
        assertEquals(2, neighboursA.size());
        assertEquals(Set.of("B", "C"), neighboursA.stream()
                .map(GraphEdge::getDestination).collect(Collectors.toSet()));
    }

    // ──────────────────────────────────────────────
    // 7. Unknown station lookup
    // ──────────────────────────────────────────────

    @Test
    void unknownStationLookupReturnsEmptyList() {
        MetroGraph graph = MetroGraphBuilder.from(network(
                List.of(station("A")), List.of(line("BLUE")), List.of()));

        assertFalse(graph.containsStation("ZZZ"));
        assertEquals(0, graph.getNeighbors("ZZZ").size());
    }

    // ──────────────────────────────────────────────
    // 8–10. Weights and line are preserved
    // ──────────────────────────────────────────────

    @Test
    void distanceIsPreserved() {
        MetroGraph graph = MetroGraphBuilder.from(network(
                List.of(station("A"), station("B")),
                List.of(line("BLUE")),
                List.of(connection("A", "B", "BLUE", 3.7, 5))));

        assertEquals(3.7, graph.getNeighbors("A").get(0).getDistanceKm(), 0.0001);
        assertEquals(3.7, graph.getNeighbors("B").get(0).getDistanceKm(), 0.0001);
    }

    @Test
    void travelTimeIsPreserved() {
        MetroGraph graph = MetroGraphBuilder.from(network(
                List.of(station("A"), station("B")),
                List.of(line("BLUE")),
                List.of(connection("A", "B", "BLUE", 1.2, 5))));

        assertEquals(5, graph.getNeighbors("A").get(0).getTravelTimeMinutes());
        assertEquals(5, graph.getNeighbors("B").get(0).getTravelTimeMinutes());
    }

    @Test
    void lineIdIsPreserved() {
        MetroGraph graph = MetroGraphBuilder.from(network(
                List.of(station("A"), station("B")),
                List.of(line("GREEN")),
                List.of(connection("A", "B", "GREEN", 1.2, 2))));

        assertEquals("GREEN", graph.getNeighbors("A").get(0).getLine());
        assertEquals("GREEN", graph.getNeighbors("B").get(0).getLine());
    }

    // ──────────────────────────────────────────────
    // 11. Reverse edges created for every connection
    // ──────────────────────────────────────────────

    @Test
    void reverseEdgesAreCreated() {
        MetroNetwork nw = network(
                List.of(station("A"), station("B"), station("C")),
                List.of(line("BLUE")),
                List.of(
                        connection("A", "B", "BLUE", 1.0, 2),
                        connection("B", "C", "BLUE", 1.1, 3)));

        MetroGraph graph = MetroGraphBuilder.from(nw);

        assertTrue(graph.getNeighbors("A").stream()
                .anyMatch(e -> e.getDestination().equals("B") && e.getLine().equals("BLUE")));
        assertTrue(graph.getNeighbors("B").stream()
                .anyMatch(e -> e.getDestination().equals("A") && e.getLine().equals("BLUE")));
        assertTrue(graph.getNeighbors("B").stream()
                .anyMatch(e -> e.getDestination().equals("C") && e.getLine().equals("BLUE")));
        assertTrue(graph.getNeighbors("C").stream()
                .anyMatch(e -> e.getDestination().equals("B") && e.getLine().equals("BLUE")));
    }

    // ──────────────────────────────────────────────
    // 12. Legitimate parallel edges on different lines preserved
    // ──────────────────────────────────────────────

    @Test
    void parallelEdgesOnDifferentLinesArePreserved() {
        // A and B share movement on two distinct lines — both edges must remain.
        MetroNetwork nw = network(
                List.of(station("A"), station("B")),
                List.of(line("BLUE"), line("YELLOW")),
                List.of(
                        connection("A", "B", "BLUE", 1.2, 2),
                        connection("A", "B", "YELLOW", 1.5, 3)));

        MetroGraph graph = MetroGraphBuilder.from(nw);

        assertEquals(4, graph.getEdgeCount(), "Two distinct line edges bidirectionally = 4");
        var neighboursA = graph.getNeighbors("A");
        assertEquals(2, neighboursA.size());
        assertTrue(neighboursA.stream().anyMatch(e -> e.getLine().equals("BLUE")));
        assertTrue(neighboursA.stream().anyMatch(e -> e.getLine().equals("YELLOW")));
    }

    // ──────────────────────────────────────────────
    // 13. Duplicate raw connection records don't create duplicate edges
    // ──────────────────────────────────────────────

    @Test
    void duplicateRawRecordsDoNotDuplicateEdges() {
        // The same A→B record appears twice in the source data.
        MetroNetwork nw = network(
                List.of(station("A"), station("B")),
                List.of(line("BLUE")),
                List.of(
                        connection("A", "B", "BLUE", 1.2, 2),
                        connection("A", "B", "BLUE", 1.2, 2)));

        MetroGraph graph = MetroGraphBuilder.from(nw);

        assertEquals(2, graph.getEdgeCount(),
                "Duplicate records should produce exactly A→B and B→A, not 4 edges");
        assertEquals(1, graph.getNeighbors("A").size());
    }

    // ──────────────────────────────────────────────
    // 14. Self-loop connections rejected
    // ──────────────────────────────────────────────

    @Test
    void selfLoopConnectionIsRejected() {
        MetroNetwork nw = network(
                List.of(station("A")),
                List.of(line("BLUE")),
                List.of(connection("A", "A", "BLUE", 1.2, 2)));

        assertThrows(IllegalArgumentException.class,
                () -> MetroGraphBuilder.from(nw),
                "Self-loop connection must be rejected during construction");
    }

    // ──────────────────────────────────────────────
    // 15. Graph read-only views cannot corrupt internal state
    // ──────────────────────────────────────────────

    @Test
    void readOnlyViewsCannotCorruptInternalState() {
        MetroNetwork nw = network(
                List.of(station("A"), station("B")),
                List.of(line("BLUE")),
                List.of(connection("A", "B", "BLUE", 1.2, 2)));

        MetroGraph graph = MetroGraphBuilder.from(nw);

        // Attempting to modify a returned neighbour list must throw.
        assertThrows(UnsupportedOperationException.class,
                () -> graph.getNeighbors("A").add(new GraphEdge("X", "BLUE", 1.0, 1)));

        // Attempting to modify the station ID set must throw.
        assertThrows(UnsupportedOperationException.class,
                () -> graph.getStationIds().add("INTRUDER"));

        // Internal state unchanged.
        assertEquals(2, graph.getStationCount());
        assertEquals(2, graph.getEdgeCount());
        assertFalse(graph.containsStation("INTRUDER"));
    }

    // ──────────────────────────────────────────────
    // 16–18. Hand-created network invariant checks
    // ──────────────────────────────────────────────

    @Test
    void networkTransformsIntoGraphWithCorrectCounts() {
        MetroNetwork nw = network(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(line("BLUE"), line("YELLOW")),
                List.of(
                        connection("A", "B", "BLUE", 1.0, 2),
                        connection("B", "C", "BLUE", 1.1, 2),
                        connection("C", "D", "YELLOW", 1.2, 3)));

        MetroGraph graph = MetroGraphBuilder.from(nw);

        assertEquals(4, graph.getStationCount());
        assertEquals(6, graph.getEdgeCount(), "3 connections × 2 = 6 directed edges");
    }

    @Test
    void graphValidatorPassesOnValidNetwork() {
        MetroNetwork nw = network(
                List.of(station("A"), station("B"), station("C")),
                List.of(line("BLUE")),
                List.of(
                        connection("A", "B", "BLUE", 1.0, 2),
                        connection("B", "C", "BLUE", 1.1, 3)));

        MetroGraph graph = MetroGraphBuilder.from(nw);
        assertTrue(MetroGraphValidator.validate(nw, graph).isEmpty());
    }

    // ──────────────────────────────────────────────
    // GraphEdge immutability and construction guards
    // ──────────────────────────────────────────────

    @Test
    void graphEdgeRejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new GraphEdge("", "BLUE", 1.0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new GraphEdge("B", "", 1.0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new GraphEdge("B", "BLUE", 0.0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new GraphEdge("B", "BLUE", 1.0, 0));
    }

    @Test
    void graphEdgeEqualityDistinguishesLines() {
        GraphEdge blue = new GraphEdge("B", "BLUE", 1.2, 2);
        GraphEdge blueCopy = new GraphEdge("B", "BLUE", 1.2, 2);
        GraphEdge yellow = new GraphEdge("B", "YELLOW", 1.2, 2);

        assertEquals(blue, blueCopy);
        assertEquals(blue.hashCode(), blueCopy.hashCode());
        assertNotEquals(blue, yellow, "Edges on different lines are not equal");
    }

    @Test
    void graphBuilderRejectsNullNetwork() {
        assertThrows(NullPointerException.class, () -> MetroGraphBuilder.from(null));
    }
}