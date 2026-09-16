package com.metromind.routing;

import com.metromind.data.Connection;
import com.metromind.data.MetroLine;
import com.metromind.data.MetroNetwork;
import com.metromind.data.Station;
import com.metromind.graph.MetroGraph;
import com.metromind.graph.MetroGraphBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BFSRouter} using small, hand-created graphs.
 */
class BFSRouterTest {

    // ──────────────────────────────────────────────
    // Test helpers
    // ──────────────────────────────────────────────

    private static Station station(String id) {
        return new Station(id, "Station " + id, 28.6, 77.2, List.of("YELLOW"), false);
    }

    private static Connection edge(String from, String to) {
        return new Connection(from, to, "YELLOW", 1.0, 2);
    }

    private static Connection edge(String from, String to, String line) {
        return new Connection(from, to, line, 1.0, 2);
    }

    private static MetroGraph graph(List<Station> stations, List<Connection> connections) {
        MetroNetwork nw = new MetroNetwork();
        nw.setStations(stations);
        nw.setLines(List.of(new MetroLine("YELLOW", "Yellow", "#FFCC00", List.of())));
        nw.setConnections(connections);
        return MetroGraphBuilder.from(nw);
    }

    private static BFSRouter router(List<Station> stations, List<Connection> connections) {
        return new BFSRouter(graph(stations, connections));
    }

    private static List<String> ids(String... list) {
        return List.of(list);
    }

    // ──────────────────────────────────────────────
    // 1. Direct route
    // ──────────────────────────────────────────────

    @Test
    void directRoute() {
        BFSRouter router = router(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B")));

        RouteResult result = router.route("A", "B");

        assertTrue(result.isFound());
        assertEquals(ids("A", "B"), result.getStationIds());
        assertEquals(1, result.getHopCount());
    }

    // ──────────────────────────────────────────────
    // 2. Multi-hop route
    // ──────────────────────────────────────────────

    @Test
    void multiHopRoute() {
        BFSRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B"), edge("B", "C"), edge("C", "D")));

        RouteResult result = router.route("A", "D");

        assertTrue(result.isFound());
        assertEquals(ids("A", "B", "C", "D"), result.getStationIds());
        assertEquals(3, result.getHopCount());
    }

    // ──────────────────────────────────────────────
    // 3. Source equals destination
    // ──────────────────────────────────────────────

    @Test
    void sourceEqualsDestinationReturnsSingleStation() {
        BFSRouter router = router(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B")));

        RouteResult result = router.route("A", "A");

        assertTrue(result.isFound());
        assertEquals(ids("A"), result.getStationIds());
        assertEquals(0, result.getHopCount());
    }

    // ──────────────────────────────────────────────
    // 4. Unreachable destination (disconnected components)
    // ──────────────────────────────────────────────

    @Test
    void unreachableDestinationIsNotFound() {
        // Component 1: A—B. Component 2: C—D. No edge between them.
        BFSRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B"), edge("C", "D")));

        RouteResult result = router.route("A", "D");

        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
        assertEquals(0, result.getHopCount());
    }

    // ──────────────────────────────────────────────
    // 5–7. Unknown stations
    // ──────────────────────────────────────────────

    @Test
    void unknownSourceIsNotFound() {
        BFSRouter router = router(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B")));

        RouteResult result = router.route("ZZZ", "B");

        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
    }

    @Test
    void unknownDestinationIsNotFound() {
        BFSRouter router = router(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B")));

        RouteResult result = router.route("A", "ZZZ");

        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
    }

    @Test
    void bothStationsUnknownIsNotFound() {
        BFSRouter router = router(
                List.of(station("A")),
                List.of());

        RouteResult result = router.route("QQQ", "ZZZ");

        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
    }

    @Test
    void nullSourceOrDestinationThrows() {
        BFSRouter router = router(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B")));

        assertThrows(IllegalArgumentException.class, () -> router.route(null, "B"));
        assertThrows(IllegalArgumentException.class, () -> router.route("A", null));
        assertThrows(NullPointerException.class, () -> new BFSRouter(null));
    }

    // ──────────────────────────────────────────────
    // 8. Multiple possible routes
    // ──────────────────────────────────────────────

    @Test
    void multipleEqualRoutesReturnAValidMinimumHopRoute() {
        // A—B—D and A—C—D are both 2 hops.
        BFSRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B"), edge("A", "C"),
                        edge("B", "D"), edge("C", "D")));

        RouteResult result = router.route("A", "D");

        assertTrue(result.isFound());
        assertEquals(2, result.getHopCount());
        assertEquals("A", result.getStationIds().get(0));
        assertEquals("D", result.getStationIds().get(result.getStationIds().size() - 1));
    }

    // ──────────────────────────────────────────────
    // 9. Shorter route is preferred over longer
    // ──────────────────────────────────────────────

    @Test
    void shorterRouteIsPreferred() {
        // A—B—C—D is 3 hops; A—E—D is 2 hops. BFS must return 2 hops.
        BFSRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D"), station("E")),
                List.of(edge("A", "B"), edge("A", "E"), edge("B", "C"),
                        edge("C", "D"), edge("E", "D")));

        RouteResult result = router.route("A", "D");

        assertTrue(result.isFound());
        assertEquals(2, result.getHopCount());
        assertEquals("A", result.getStationIds().get(0));
        assertEquals("D", result.getStationIds().get(2));
    }

    // ──────────────────────────────────────────────
    // 10. Parallel edges — discovered only once
    // ──────────────────────────────────────────────

    @Test
    void parallelLineEdgesDoNotCauseMultipleVisits() {
        // A—B exists on both BLUE and YELLOW.
        BFSRouter router = router(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B", "BLUE"), edge("A", "B", "YELLOW")));

        RouteResult result = router.route("A", "B");

        assertTrue(result.isFound());
        assertEquals(ids("A", "B"), result.getStationIds());
        assertEquals(1, result.getHopCount());

        // Verify the graph does carry both parallel edges (reachability is what BFS dedups).
        MetroGraph graph = graph(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B", "BLUE"), edge("A", "B", "YELLOW")));
        assertEquals(2, graph.getNeighbors("A").size());
    }

    // ──────────────────────────────────────────────
    // 11. Cycle terminates correctly
    // ──────────────────────────────────────────────

    @Test
    void cycleTerminatesCorrectly() {
        // The triangle A—B—C—A is a cycle. Because edges are bidirectional,
        // A is directly adjacent to C, so the minimum A→D route is A→C→D (2 hops).
        // The cycle must not cause BFS to loop or re-visit stations.
        BFSRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B"), edge("B", "C"), edge("C", "A"),
                        edge("C", "D")));

        RouteResult result = router.route("A", "D");

        assertTrue(result.isFound());
        assertEquals(2, result.getHopCount());
        assertEquals(ids("A", "C", "D"), result.getStationIds());
    }

    @Test
    void cycleWithNoShortcutStillYieldsMinimumHops() {
        // A—B—C—D plus a back-edge C—B. There is no A—C shortcut, so the
        // minimum A→D route is A→B→C→D (3 hops), and the cycle C—B—C must
        // not confuse BFS.
        BFSRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B"), edge("B", "C"), edge("C", "B"),
                        edge("C", "D")));

        RouteResult result = router.route("A", "D");

        assertTrue(result.isFound());
        assertEquals(3, result.getHopCount());
        assertEquals(ids("A", "B", "C", "D"), result.getStationIds());
    }

    // ──────────────────────────────────────────────
    // 13. Larger deterministic multi-level graph
    // ──────────────────────────────────────────────

    @Test
    void largerGraphExploresLevelByLevel() {
        //      A
        //     /|\
        //    B C D
        //   /  |
        //  E   F—G
        //  |
        //  H
        BFSRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D"),
                        station("E"), station("F"), station("G"), station("H")),
                List.of(edge("A", "B"), edge("A", "C"), edge("A", "D"),
                        edge("B", "E"), edge("C", "F"), edge("F", "G"),
                        edge("E", "H")));

        RouteResult toH = router.route("A", "H");
        assertTrue(toH.isFound());
        assertEquals(3, toH.getHopCount());
        assertEquals(ids("A", "B", "E", "H"), toH.getStationIds());

        RouteResult toG = router.route("A", "G");
        assertTrue(toG.isFound());
        assertEquals(3, toG.getHopCount());
        assertEquals(ids("A", "C", "F", "G"), toG.getStationIds());

        // D is a direct neighbour of A.
        RouteResult toD = router.route("A", "D");
        assertTrue(toD.isFound());
        assertEquals(1, toD.getHopCount());
    }

    // ──────────────────────────────────────────────
    // 15. Route-hostile BFS on a graph with no self-loops
    // ──────────────────────────────────────────────

    @Test
    void bfsWorksOnGraphWithoutSelfLoops() {
        // Graph builder rejects self-loops, so BFS simply must handle clean graphs.
        BFSRouter router = router(
                List.of(station("P"), station("Q"), station("R")),
                List.of(edge("P", "Q"), edge("Q", "R")));

        RouteResult result = router.route("P", "R");
        assertTrue(result.isFound());
        assertEquals(ids("P", "Q", "R"), result.getStationIds());
        assertEquals(2, result.getHopCount());
    }

    // ──────────────────────────────────────────────
    // Result validation: endpoints, adjacency, no repeats
    // ──────────────────────────────────────────────

    @Test
    void routeIsValidatedAgainstTheGraph() {
        //      A
        //     / \
        //    B   C
        //   / \
        //  D   E
        //       \
        //        F
        List<Station> stations = List.of(
                station("A"), station("B"), station("C"),
                station("D"), station("E"), station("F"));
        List<Connection> connections = List.of(
                edge("A", "B"), edge("A", "C"),
                edge("B", "D"), edge("B", "E"),
                edge("E", "F"));

        MetroGraph graph = graph(stations, connections);
        BFSRouter router = new BFSRouter(graph);

        RouteResult result = router.route("A", "F");

        assertTrue(result.isFound());
        List<String> route = result.getStationIds();

        // No repeated station IDs.
        assertEquals(route.size(), route.stream().distinct().count(),
                "BFS shortest-hop route must not repeat stations");

        // Endpoints.
        assertEquals("A", route.get(0));
        assertEquals("F", route.get(route.size() - 1));

        // Every consecutive pair is directly connected by a graph edge.
        for (int i = 0; i < route.size() - 1; i++) {
            String current = route.get(i);
            String next = route.get(i + 1);
            boolean connected = graph.getNeighbors(current).stream()
                    .anyMatch(e -> e.getDestination().equals(next));
            assertTrue(connected,
                    "No graph edge between " + current + " and " + next);
        }

        // Hop count invariant.
        assertEquals(route.size() - 1, result.getHopCount());
    }

    // ──────────────────────────────────────────────
    // RouteResult immutability
    // ──────────────────────────────────────────────

    @Test
    void routeResultIsImmutable() {
        BFSRouter router = router(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B")));

        RouteResult result = router.route("A", "B");

        assertThrows(UnsupportedOperationException.class,
                () -> result.getStationIds().add("INTRUDER"));

        // Internal state unchanged.
        assertEquals(ids("A", "B"), result.getStationIds());
        assertEquals(1, result.getHopCount());
    }

    @Test
    void routeResultRejectsEmptyFoundRoute() {
        assertThrows(IllegalArgumentException.class, () -> RouteResult.found(List.of()));
    }

    @Test
    void notFoundResultHasEmptyStationList() {
        RouteResult notFound = RouteResult.notFound();
        assertFalse(notFound.isFound());
        assertTrue(notFound.getStationIds().isEmpty());
        assertEquals(0, notFound.getHopCount());
    }
}