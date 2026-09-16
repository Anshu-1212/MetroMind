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
 * Unit tests for {@link DijkstraRouter} using small, hand-created weighted graphs.
 */
class DijkstraRouterTest {

    private static final double DELTA = 1e-9;

    // ──────────────────────────────────────────────
    // Test helpers
    // ──────────────────────────────────────────────

    private static Station station(String id) {
        return new Station(id, "Station " + id, 28.6, 77.2, List.of("YELLOW"), false);
    }

    private static Connection edge(String from, String to, double distanceKm, int minutes) {
        return new Connection(from, to, "YELLOW", distanceKm, minutes);
    }

    private static Connection edge(String from, String to, String line,
                                   double distanceKm, int minutes) {
        return new Connection(from, to, line, distanceKm, minutes);
    }

    private static MetroGraph graph(List<Station> stations, List<Connection> connections) {
        MetroNetwork nw = new MetroNetwork();
        nw.setStations(stations);
        nw.setLines(List.of(new MetroLine("YELLOW", "Yellow", "#FFCC00", List.of())));
        nw.setConnections(connections);
        return MetroGraphBuilder.from(nw);
    }

    private static DijkstraRouter router(List<Station> stations, List<Connection> connections) {
        return new DijkstraRouter(graph(stations, connections));
    }

    private static List<String> ids(String... list) {
        return List.of(list);
    }

    // ──────────────────────────────────────────────
    // 1. Direct weighted route
    // ──────────────────────────────────────────────

    @Test
    void directWeightedRoute() {
        DijkstraRouter router = router(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B", 3.5, 7)));

        RouteResult distance = router.route("A", "B", RouteMetric.DISTANCE);
        assertTrue(distance.isFound());
        assertEquals(ids("A", "B"), distance.getStationIds());
        assertEquals(1, distance.getHopCount());
        assertEquals(3.5, distance.getTotalCost(), DELTA);

        RouteResult time = router.route("A", "B", RouteMetric.TRAVEL_TIME);
        assertTrue(time.isFound());
        assertEquals(7.0, time.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 2. Multi-hop route
    // ──────────────────────────────────────────────

    @Test
    void multiHopRoute() {
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B", 1.0, 2), edge("B", "C", 2.0, 3), edge("C", "D", 3.0, 4)));

        RouteResult distance = router.route("A", "D", RouteMetric.DISTANCE);
        assertTrue(distance.isFound());
        assertEquals(ids("A", "B", "C", "D"), distance.getStationIds());
        assertEquals(3, distance.getHopCount());
        assertEquals(6.0, distance.getTotalCost(), DELTA);

        RouteResult time = router.route("A", "D", RouteMetric.TRAVEL_TIME);
        assertEquals(9.0, time.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 3. Minimum-distance route
    // ──────────────────────────────────────────────

    @Test
    void minimumDistanceRoute() {
        // A→B→C = 2.0 + 3.0 = 5.0 km; A→D→C = 1.0 + 6.0 = 7.0 km.
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B", 2.0, 1), edge("B", "C", 3.0, 1),
                        edge("A", "D", 1.0, 1), edge("D", "C", 6.0, 1)));

        RouteResult result = router.route("A", "C", RouteMetric.DISTANCE);
        assertTrue(result.isFound());
        assertEquals(ids("A", "B", "C"), result.getStationIds());
        assertEquals(5.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 4. Minimum-travel-time route
    // ──────────────────────────────────────────────

    @Test
    void minimumTravelTimeRoute() {
        // A→B→C = 8 + 8 = 16 min; A→D→C = 4 + 4 = 8 min.
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B", 1.0, 8), edge("B", "C", 1.0, 8),
                        edge("A", "D", 1.0, 4), edge("D", "C", 1.0, 4)));

        RouteResult result = router.route("A", "C", RouteMetric.TRAVEL_TIME);
        assertTrue(result.isFound());
        assertEquals(ids("A", "D", "C"), result.getStationIds());
        assertEquals(8.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 5. Source equals destination
    // ──────────────────────────────────────────────

    @Test
    void sourceEqualsDestinationZeroCostForBothMetrics() {
        DijkstraRouter router = router(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B", 1.0, 2)));

        for (RouteMetric metric : RouteMetric.values()) {
            RouteResult result = router.route("A", "A", metric);
            assertTrue(result.isFound());
            assertEquals(ids("A"), result.getStationIds());
            assertEquals(0, result.getHopCount());
            assertEquals(0.0, result.getTotalCost(), DELTA,
                    "source == destination must cost 0 for " + metric);
        }
    }

    // ──────────────────────────────────────────────
    // 6–8. Unknown stations
    // ──────────────────────────────────────────────

    @Test
    void unknownSourceIsNotFound() {
        DijkstraRouter router = router(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B", 1.0, 2)));

        RouteResult result = router.route("ZZZ", "B", RouteMetric.DISTANCE);
        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
        assertTrue(Double.isNaN(result.getTotalCost()));
    }

    @Test
    void unknownDestinationIsNotFound() {
        DijkstraRouter router = router(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B", 1.0, 2)));

        RouteResult result = router.route("A", "ZZZ", RouteMetric.TRAVEL_TIME);
        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
    }

    @Test
    void bothStationsUnknownIsNotFound() {
        DijkstraRouter router = router(
                List.of(station("A")),
                List.of());

        RouteResult result = router.route("QQQ", "ZZZ", RouteMetric.DISTANCE);
        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
    }

    // ──────────────────────────────────────────────
    // 9. Null arguments
    // ──────────────────────────────────────────────

    @Test
    void nullArgumentsThrow() {
        DijkstraRouter router = router(
                List.of(station("A"), station("B")),
                List.of(edge("A", "B", 1.0, 2)));

        assertThrows(IllegalArgumentException.class,
                () -> router.route(null, "B", RouteMetric.DISTANCE));
        assertThrows(IllegalArgumentException.class,
                () -> router.route("A", null, RouteMetric.DISTANCE));
        assertThrows(IllegalArgumentException.class,
                () -> router.route("A", "B", null));
        assertThrows(NullPointerException.class, () -> new DijkstraRouter(null));
    }

    // ──────────────────────────────────────────────
    // 10. Unreachable destination (disconnected components)
    // ──────────────────────────────────────────────

    @Test
    void unreachableDestinationIsNotFound() {
        // Component 1: A—B. Component 2: C—D. No edge between them.
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B", 1.0, 1), edge("C", "D", 1.0, 1)));

        for (RouteMetric metric : RouteMetric.values()) {
            RouteResult result = router.route("A", "D", metric);
            assertFalse(result.isFound());
            assertTrue(result.getStationIds().isEmpty());
            assertEquals(0, result.getHopCount());
            assertTrue(Double.isNaN(result.getTotalCost()));
        }
    }

    // ──────────────────────────────────────────────
    // 11. Multiple equal-cost routes
    // ──────────────────────────────────────────────

    @Test
    void multipleEqualRoutesReturnMinimumCost() {
        // A—B—D and A—C—D both cost 2.0 km. Either route is valid; the cost is fixed.
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B", 1.0, 1), edge("B", "D", 1.0, 1),
                        edge("A", "C", 1.0, 1), edge("C", "D", 1.0, 1)));

        RouteResult result = router.route("A", "D", RouteMetric.DISTANCE);

        assertTrue(result.isFound());
        assertEquals(2.0, result.getTotalCost(), DELTA);
        assertEquals("A", result.getStationIds().get(0));
        assertEquals("D", result.getStationIds().get(result.getStationIds().size() - 1));
    }

    // ──────────────────────────────────────────────
    // 12. Dijkstra chooses distance, not hops
    // ──────────────────────────────────────────────

    @Test
    void longerHopRouteWithLowerDistanceIsChosen() {
        // A—B—C is 2 hops and 2 km; A—C is 1 hop and 100 km.
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C")),
                List.of(edge("A", "B", 1.0, 1), edge("B", "C", 1.0, 1),
                        edge("A", "C", 100.0, 1)));

        RouteResult result = router.route("A", "C", RouteMetric.DISTANCE);

        assertTrue(result.isFound());
        assertEquals(ids("A", "B", "C"), result.getStationIds(),
                "2-hop 2 km route must beat 1-hop 100 km route");
        assertEquals(2, result.getHopCount());
        assertEquals(2.0, result.getTotalCost(), DELTA);
    }

    @Test
    void fewerHopsButHigherDistanceIsRejected() {
        // The 1-hop A—C route (10 km) is fewer hops but more distance than A—D—C (3+3 km).
        DijkstraRouter router = router(
                List.of(station("A"), station("C"), station("D")),
                List.of(edge("A", "C", 10.0, 1),
                        edge("A", "D", 3.0, 1), edge("D", "C", 3.0, 1)));

        RouteResult result = router.route("A", "C", RouteMetric.DISTANCE);

        assertTrue(result.isFound());
        assertEquals(ids("A", "D", "C"), result.getStationIds(),
                "Fewer hops must not win when the distance is greater");
        assertEquals(6.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 14–15. Distance vs travel-time disagreement
    // ──────────────────────────────────────────────

    @Test
    void fasterRouteThatIsPhysicallyLongerIsChosen() {
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B", 1.0, 20), edge("B", "D", 1.0, 20),
                        edge("A", "C", 5.0, 5), edge("C", "D", 5.0, 5)));

        RouteResult result = router.route("A", "D", RouteMetric.TRAVEL_TIME);

        assertTrue(result.isFound());
        assertEquals(ids("A", "C", "D"), result.getStationIds(),
                "10 min route must beat 40 min route even though it is 10 km vs 2 km");
        assertEquals(10.0, result.getTotalCost(), DELTA);
    }

    @Test
    void shorterDistanceRouteThatTakesMoreTimeIsChosen() {
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B", 1.0, 20), edge("B", "D", 1.0, 20),
                        edge("A", "C", 5.0, 5), edge("C", "D", 5.0, 5)));

        RouteResult result = router.route("A", "D", RouteMetric.DISTANCE);

        assertTrue(result.isFound());
        assertEquals(ids("A", "B", "D"), result.getStationIds(),
                "2 km route must beat 10 km route even though it takes 40 min");
        assertEquals(2.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 16. Critical weighted test: Dijkstra vs BFS
    // ──────────────────────────────────────────────

    @Test
    void dijkstraFindsDistanceOptimalRouteWhereBfsOnlyCountsHops() {
        // A→B→D is 1 + 10 = 11 km (2 hops); A→C→D is 4 + 4 = 8 km (2 hops).
        // BFS sees two equal 2-hop routes; Dijkstra must pick the 8 km one.
        List<Station> stations = List.of(station("A"), station("B"), station("C"), station("D"));
        List<Connection> connections = List.of(
                edge("A", "B", 1.0, 1), edge("B", "D", 10.0, 1),
                edge("A", "C", 4.0, 1), edge("C", "D", 4.0, 1));

        DijkstraRouter dijkstra = router(stations, connections);
        BFSRouter bfs = new BFSRouter(graph(stations, connections));

        RouteResult d = dijkstra.route("A", "D", RouteMetric.DISTANCE);
        assertTrue(d.isFound());
        assertEquals(ids("A", "C", "D"), d.getStationIds());
        assertEquals(8.0, d.getTotalCost(), DELTA);

        // BFS only guarantees minimum hops; both candidate routes are 2 hops.
        RouteResult b = bfs.route("A", "D");
        assertTrue(b.isFound());
        assertEquals(2, b.getHopCount());
    }

    // ──────────────────────────────────────────────
    // 17. The metric controls Dijkstra
    // ──────────────────────────────────────────────

    @Test
    void metricControlsWhichRouteIsOptimal() {
        // A→B→D: distance 1+1=2, time 20+20=40.
        // A→C→D: distance 5+5=10, time 5+5=10.
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B", 1.0, 20), edge("B", "D", 1.0, 20),
                        edge("A", "C", 5.0, 5), edge("C", "D", 5.0, 5)));

        RouteResult distance = router.route("A", "D", RouteMetric.DISTANCE);
        RouteResult time = router.route("A", "D", RouteMetric.TRAVEL_TIME);

        assertEquals(ids("A", "B", "D"), distance.getStationIds());
        assertEquals(2.0, distance.getTotalCost(), DELTA);

        assertEquals(ids("A", "C", "D"), time.getStationIds());
        assertEquals(10.0, time.getTotalCost(), DELTA);

        assertNotEquals(distance.getStationIds(), time.getStationIds(),
                "The two metrics must select different routes on this graph");
    }

    // ──────────────────────────────────────────────
    // 18. Parallel edges with different weights
    // ──────────────────────────────────────────────

    @Test
    void parallelEdgesWithDifferentWeightsAreNotCollapsed() {
        // A→B via BLUE (5 km, 10 min) and via YELLOW (2 km, 20 min).
        List<Station> stations = List.of(station("A"), station("B"));
        List<Connection> connections = List.of(
                edge("A", "B", "BLUE", 5.0, 10),
                edge("A", "B", "YELLOW", 2.0, 20));

        DijkstraRouter router = router(stations, connections);
        MetroGraph graph = graph(stations, connections);

        // Both parallel edges must still exist in the graph.
        assertEquals(2, graph.getNeighbors("A").size(),
                "Legitimate parallel edges must be preserved");

        RouteResult distance = router.route("A", "B", RouteMetric.DISTANCE);
        assertTrue(distance.isFound());
        assertEquals(2.0, distance.getTotalCost(), DELTA,
                "DISTANCE must select the 2 km parallel edge");

        RouteResult time = router.route("A", "B", RouteMetric.TRAVEL_TIME);
        assertEquals(10.0, time.getTotalCost(), DELTA,
                "TRAVEL_TIME must select the 10 min parallel edge");
    }

    // ──────────────────────────────────────────────
    // 19. Cyclic graph
    // ──────────────────────────────────────────────

    @Test
    void cyclicGraphTerminatesWithCorrectRoute() {
        // Triangle A—B—C—A with positive weights. Dijkstra must terminate,
        // not loop, and reconstruct a valid predecessor chain.
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C")),
                List.of(edge("A", "B", 1.0, 1), edge("B", "C", 2.0, 1),
                        edge("C", "A", 10.0, 1)));

        RouteResult result = router.route("A", "C", RouteMetric.DISTANCE);

        assertTrue(result.isFound());
        assertEquals(ids("A", "B", "C"), result.getStationIds(),
                "Back-edge C→A must not tempt Dijkstra into a longer loop");
        assertEquals(3.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 20–21. Stale / repeated priority-queue entries
    // ──────────────────────────────────────────────

    @Test
    void stalePriorityQueueEntriesAreSkipped() {
        // B is discovered at 10 (A→B) then improved to 2 (A→C→B). The stale
        // (B, 10) queue entry must not be treated as the final distance.
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C")),
                List.of(edge("A", "B", 10.0, 1), edge("A", "C", 1.0, 1),
                        edge("C", "B", 1.0, 1)));

        RouteResult result = router.route("A", "B", RouteMetric.DISTANCE);

        assertTrue(result.isFound());
        assertEquals(ids("A", "C", "B"), result.getStationIds());
        assertEquals(2.0, result.getTotalCost(), DELTA,
                "The improved 2 km route must beat the initial 10 km one");
    }

    @Test
    void repeatedImprovementsLeaveMultipleStaleEntries() {
        // X: direct 10 (A→X), via B 6 (A→B→X), via C 4 (A→C→X). The queue holds
        // (X,10), (X,6) and (X,4); the two older entries must be skipped.
        DijkstraRouter router = router(
                List.of(station("A"), station("X"), station("B"), station("C")),
                List.of(edge("A", "X", 10.0, 1),
                        edge("A", "B", 1.0, 1), edge("B", "X", 5.0, 1),
                        edge("A", "C", 1.0, 1), edge("C", "X", 3.0, 1)));

        RouteResult result = router.route("A", "X", RouteMetric.DISTANCE);

        assertTrue(result.isFound());
        assertEquals(ids("A", "C", "X"), result.getStationIds());
        assertEquals(4.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 22. Path reconstruction and hop invariant
    // ──────────────────────────────────────────────

    @Test
    void pathReconstructionIsOrderedAndConsistent() {
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C"), station("D")),
                List.of(edge("A", "B", 1.0, 1), edge("B", "C", 2.0, 1),
                        edge("C", "D", 3.0, 1)));

        RouteResult result = router.route("A", "D", RouteMetric.DISTANCE);

        assertTrue(result.isFound());
        List<String> route = result.getStationIds();
        assertEquals(ids("A", "B", "C", "D"), route);
        assertEquals("A", route.get(0));
        assertEquals("D", route.get(route.size() - 1));
        assertEquals(route.size() - 1, result.getHopCount());
        assertEquals(6.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 23–24. Correct total cost accumulation
    // ──────────────────────────────────────────────

    @Test
    void correctTotalDistanceAccumulatedWithoutPrecisionLoss() {
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C")),
                List.of(edge("A", "B", 1.5, 20), edge("B", "C", 2.5, 35)));

        RouteResult result = router.route("A", "C", RouteMetric.DISTANCE);

        assertTrue(result.isFound());
        assertEquals(4.0, result.getTotalCost(), DELTA);
    }

    @Test
    void correctTotalTravelTimeAccumulated() {
        DijkstraRouter router = router(
                List.of(station("A"), station("B"), station("C")),
                List.of(edge("A", "B", 1.0, 20), edge("B", "C", 2.0, 35)));

        RouteResult result = router.route("A", "C", RouteMetric.TRAVEL_TIME);

        assertTrue(result.isFound());
        assertEquals(55.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 25. RouteResult cost semantics
    // ──────────────────────────────────────────────

    @Test
    void bfsResultsAndNotFoundHaveNoScalarCost() {
        // BFS results are unweighted: getTotalCost() must be NaN, not a fake number.
        List<Station> stations = List.of(station("A"), station("B"));
        List<Connection> connections = List.of(edge("A", "B", 1.0, 2));
        RouteResult bfs = new BFSRouter(graph(stations, connections)).route("A", "B");
        assertTrue(bfs.isFound());
        assertTrue(Double.isNaN(bfs.getTotalCost()));

        DijkstraRouter dijkstra = router(stations, connections);
        RouteResult notFound = dijkstra.route("A", "ZZZ", RouteMetric.DISTANCE);
        assertFalse(notFound.isFound());
        assertTrue(Double.isNaN(notFound.getTotalCost()));
    }

    @Test
    void foundRouteRejectsNonFiniteCosts() {
        assertThrows(IllegalArgumentException.class, () -> RouteResult.found(ids("A"), -1.0));
        assertThrows(IllegalArgumentException.class, () -> RouteResult.found(ids("A"), Double.NaN));
    }
}