package com.metromind.routing;

import com.metromind.data.Connection;
import com.metromind.data.MetroLine;
import com.metromind.data.MetroNetwork;
import com.metromind.data.Station;
import com.metromind.graph.MetroGraph;
import com.metromind.graph.MetroGraphBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AStarRouter}.
 *
 * <p>Most test graphs use a tight cluster of stations (lat 28.6000–28.6001,
 * lon 77.2000–77.2002) whose mutual Haversine distances are all below ~0.03 km,
 * with every edge weight ≥ 1 km. That guarantees the heuristic is admissible
 * everywhere, so the assertions on <em>optimal distance</em> hold. The
 * {@code heuristicChangesExplorationPriority} test uses a deliberately stretched
 * equatorial layout so the heuristic actively reorders the search.</p>
 */
class AStarRouterTest {

    private static final double DELTA = 1e-9;

    // ──────────────────────────────────────────────
    // Test helpers
    // ──────────────────────────────────────────────

    private static Station station(String id, double lat, double lon) {
        return new Station(id, "Station " + id, lat, lon, List.of("YELLOW"), false);
    }

    private static Map<String, GeoPoint> coordinateMap(List<Station> stations) {
        Map<String, GeoPoint> map = new HashMap<>();
        for (Station s : stations) {
            map.put(s.getId(), new GeoPoint(s.getLatitude(), s.getLongitude()));
        }
        return map;
    }

    private static Connection edge(String from, String to, double distanceKm) {
        return new Connection(from, to, "YELLOW", distanceKm, 1);
    }

    private static Connection edge(String from, String to, String line, double distanceKm) {
        return new Connection(from, to, line, distanceKm, 1);
    }

    private static MetroGraph graph(List<Station> stations, List<Connection> connections) {
        MetroNetwork nw = new MetroNetwork();
        nw.setStations(stations);
        nw.setLines(List.of(new MetroLine("YELLOW", "Yellow", "#FFCC00", List.of())));
        nw.setConnections(connections);
        return MetroGraphBuilder.from(nw);
    }

    private static AStarRouter router(List<Station> stations, List<Connection> connections) {
        return new AStarRouter(graph(stations, connections), coordinateMap(stations));
    }

    private static List<String> ids(String... list) {
        return List.of(list);
    }

    private static void assertAdjacent(MetroGraph graph, List<String> route) {
        for (int i = 0; i < route.size() - 1; i++) {
            String current = route.get(i);
            String next = route.get(i + 1);
            boolean connected = graph.getNeighbors(current).stream()
                    .anyMatch(e -> e.getDestination().equals(next));
            assertTrue(connected, "No graph edge between " + current + " and " + next);
        }
    }

    /** Station coordinates used by the compact-region test graphs. */
    private static final double LAT0 = 28.6000;
    private static final double LON0 = 77.2000;

    // ──────────────────────────────────────────────
    // 1. Direct route
    // ──────────────────────────────────────────────

    @Test
    void directRoute() {
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001));
        AStarRouter router = router(stations, List.of(edge("A", "B", 8.0)));

        RouteResult result = router.route("A", "B");

        assertTrue(result.isFound());
        assertEquals(ids("A", "B"), result.getStationIds());
        assertEquals(1, result.getHopCount());
        assertEquals(8.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 2. Multi-hop route
    // ──────────────────────────────────────────────

    @Test
    void multiHopRoute() {
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001),
                station("C", LAT0, LON0 + 0.0002));
        AStarRouter router = router(stations,
                List.of(edge("A", "B", 8.0), edge("B", "C", 8.0)));

        RouteResult result = router.route("A", "C");

        assertTrue(result.isFound());
        assertEquals(ids("A", "B", "C"), result.getStationIds());
        assertEquals(2, result.getHopCount());
        assertEquals(16.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 3. Minimum-distance route
    // ──────────────────────────────────────────────

    @Test
    void minimumDistanceRoute() {
        // A→B→D = 10 + 10 = 20 km; A→C→D = 15 + 15 = 30 km.
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001),
                station("C", LAT0 + 0.0001, LON0),
                station("D", LAT0 + 0.0001, LON0 + 0.0002));
        AStarRouter router = router(stations, List.of(
                edge("A", "B", 10.0), edge("B", "D", 10.0),
                edge("A", "C", 15.0), edge("C", "D", 15.0)));

        RouteResult result = router.route("A", "D");

        assertTrue(result.isFound());
        assertEquals(ids("A", "B", "D"), result.getStationIds());
        assertEquals(20.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 4. Path reconstruction and hop invariant
    // ──────────────────────────────────────────────

    @Test
    void routeReconstructionIsOrderedAndConsistent() {
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001),
                station("C", LAT0 + 0.0001, LON0 + 0.0001),
                station("D", LAT0 + 0.0001, LON0 + 0.0002));
        List<Connection> connections = List.of(
                edge("A", "B", 8.0), edge("B", "C", 8.0), edge("C", "D", 8.0));
        MetroGraph graph = graph(stations, connections);
        AStarRouter router = new AStarRouter(graph, coordinateMap(stations));

        RouteResult result = router.route("A", "D");

        assertTrue(result.isFound());
        List<String> route = result.getStationIds();
        assertEquals(ids("A", "B", "C", "D"), route);
        assertEquals("A", route.get(0));
        assertEquals("D", route.get(route.size() - 1));
        assertEquals(route.size() - 1, result.getHopCount());
        assertEquals(route.size(), route.stream().distinct().count(),
                "Route must not repeat stations");
        assertAdjacent(graph, route);
        assertEquals(24.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 5. Source equals destination
    // ──────────────────────────────────────────────

    @Test
    void sourceEqualsDestinationZeroCost() {
        AStarRouter router = router(
                List.of(station("A", LAT0, LON0), station("B", LAT0, LON0 + 0.0001)),
                List.of(edge("A", "B", 8.0)));

        RouteResult result = router.route("A", "A");

        assertTrue(result.isFound());
        assertEquals(ids("A"), result.getStationIds());
        assertEquals(0, result.getHopCount());
        assertEquals(0.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 6–8. Unknown stations
    // ──────────────────────────────────────────────

    @Test
    void unknownSourceIsNotFound() {
        AStarRouter router = router(
                List.of(station("A", LAT0, LON0), station("B", LAT0, LON0 + 0.0001)),
                List.of(edge("A", "B", 8.0)));

        RouteResult result = router.route("ZZZ", "B");

        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
        assertTrue(Double.isNaN(result.getTotalCost()));
    }

    @Test
    void unknownDestinationIsNotFound() {
        AStarRouter router = router(
                List.of(station("A", LAT0, LON0), station("B", LAT0, LON0 + 0.0001)),
                List.of(edge("A", "B", 8.0)));

        RouteResult result = router.route("A", "ZZZ");

        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
    }

    @Test
    void bothStationsUnknownIsNotFound() {
        AStarRouter router = router(List.of(station("A", LAT0, LON0)), List.of());

        RouteResult result = router.route("QQQ", "ZZZ");

        assertFalse(result.isFound());
    }

    // ──────────────────────────────────────────────
    // 9. Unreachable destination
    // ──────────────────────────────────────────────

    @Test
    void unreachableDestinationIsNotFound() {
        // Component 1: A—B. Component 2: C—D. No edge between them.
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001),
                station("C", LAT0 + 0.0002, LON0),
                station("D", LAT0 + 0.0002, LON0 + 0.0001));
        AStarRouter router = router(stations,
                List.of(edge("A", "B", 8.0), edge("C", "D", 8.0)));

        RouteResult result = router.route("A", "D");

        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
        assertEquals(0, result.getHopCount());
        assertTrue(Double.isNaN(result.getTotalCost()));
    }

    // ──────────────────────────────────────────────
    // 10. Null / invalid construction
    // ──────────────────────────────────────────────

    @Test
    void nullArgumentsThrow() {
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001));
        List<Connection> connections = List.of(edge("A", "B", 8.0));
        AStarRouter router = router(stations, connections);

        assertThrows(IllegalArgumentException.class, () -> router.route(null, "B"));
        assertThrows(IllegalArgumentException.class, () -> router.route("A", null));
        assertThrows(NullPointerException.class,
                () -> new AStarRouter(null, coordinateMap(stations)));
        assertThrows(NullPointerException.class,
                () -> new AStarRouter(graph(stations, connections), null));
    }

    @Test
    void constructorRequiresCoordinatesForEveryStation() {
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001));
        MetroGraph graph = graph(stations, List.of(edge("A", "B", 8.0)));

        // Missing station B.
        Map<String, GeoPoint> partial = Map.of("A", new GeoPoint(LAT0, LON0));
        assertThrows(IllegalArgumentException.class, () -> new AStarRouter(graph, partial));

        // A station mapped to a null point.
        Map<String, GeoPoint> withNull = new HashMap<>();
        withNull.put("A", new GeoPoint(LAT0, LON0));
        withNull.put("B", null);
        assertThrows(IllegalArgumentException.class, () -> new AStarRouter(graph, withNull));
    }

    // ──────────────────────────────────────────────
    // 11. Cyclic graph
    // ──────────────────────────────────────────────

    @Test
    void cyclicGraphTerminates() {
        // Triangle A—B—C—A: A→C direct edge is 25 km, via B is 8 + 8 = 16 km.
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001),
                station("C", LAT0, LON0 + 0.0002));
        AStarRouter router = router(stations, List.of(
                edge("A", "B", 8.0), edge("B", "C", 8.0), edge("C", "A", 25.0)));

        RouteResult result = router.route("A", "C");

        assertTrue(result.isFound());
        assertEquals(ids("A", "B", "C"), result.getStationIds(),
                "The 16 km two-hop route must beat the 25 km back-edge");
        assertEquals(16.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 12. Multiple candidate routes
    // ──────────────────────────────────────────────

    @Test
    void multipleCandidateRoutesReturnOptimalCost() {
        // Both A—B—D and A—C—D cost 20 km; either path is valid, the cost is fixed.
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001),
                station("C", LAT0 + 0.0001, LON0),
                station("D", LAT0 + 0.0001, LON0 + 0.0002));
        AStarRouter router = router(stations, List.of(
                edge("A", "B", 10.0), edge("B", "D", 10.0),
                edge("A", "C", 10.0), edge("C", "D", 10.0)));

        RouteResult result = router.route("A", "D");

        assertTrue(result.isFound());
        assertEquals(20.0, result.getTotalCost(), DELTA);
        assertEquals("A", result.getStationIds().get(0));
        assertEquals("D", result.getStationIds().get(result.getStationIds().size() - 1));
    }

    // ──────────────────────────────────────────────
    // 18–19. A* ignores hop count
    // ──────────────────────────────────────────────

    @Test
    void longerHopRouteWithLowerDistanceIsChosen() {
        // A—B—C is 2 hops and 16 km; A—C is 1 hop and 30 km.
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001),
                station("C", LAT0, LON0 + 0.0002));
        AStarRouter router = router(stations, List.of(
                edge("A", "B", 8.0), edge("B", "C", 8.0), edge("A", "C", 30.0)));

        RouteResult result = router.route("A", "C");

        assertTrue(result.isFound());
        assertEquals(ids("A", "B", "C"), result.getStationIds());
        assertEquals(16.0, result.getTotalCost(), DELTA);
    }

    @Test
    void shorterHopRouteWithHigherDistanceIsRejected() {
        // The 1-hop A—C route (30 km) loses to the 2-hop A—D—C route (8 + 8 km).
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("C", LAT0, LON0 + 0.0002),
                station("D", LAT0, LON0 + 0.0001));
        AStarRouter router = router(stations, List.of(
                edge("A", "C", 30.0), edge("A", "D", 8.0), edge("D", "C", 8.0)));

        RouteResult result = router.route("A", "C");

        assertTrue(result.isFound());
        assertEquals(ids("A", "D", "C"), result.getStationIds());
        assertEquals(16.0, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 16–17. Improved g-score / stale queue handling
    // ──────────────────────────────────────────────

    @Test
    void longerFirstDiscoveryIsImprovedLater() {
        // B is discovered at 50 (A→B) then improved to 9 (A→C→B). The improved
        // g-score must not be ignored because an earlier entry exists.
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("C", LAT0, LON0 + 0.0001),
                station("B", LAT0 + 0.0001, LON0));
        AStarRouter router = router(stations, List.of(
                edge("A", "B", 50.0), edge("A", "C", 8.0), edge("C", "B", 1.0)));

        RouteResult result = router.route("A", "B");

        assertTrue(result.isFound());
        assertEquals(ids("A", "C", "B"), result.getStationIds());
        assertEquals(9.0, result.getTotalCost(), DELTA,
                "The improved 9 km route must beat the initial 50 km one");
    }

    // ──────────────────────────────────────────────
    // 22. Parallel edges
    // ──────────────────────────────────────────────

    @Test
    void parallelEdgesWithDifferentDistancesAreNotCollapsed() {
        // A→B via BLUE (20 km) and via YELLOW (9 km).
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001));
        List<Connection> connections = List.of(
                edge("A", "B", "BLUE", 20.0),
                edge("A", "B", "YELLOW", 9.0));

        AStarRouter router = router(stations, connections);
        MetroGraph g = graph(stations, connections);
        assertEquals(2, g.getNeighbors("A").size(),
                "Legitimate parallel edges must be preserved");

        RouteResult result = router.route("A", "B");

        assertTrue(result.isFound());
        assertEquals(ids("A", "B"), result.getStationIds());
        assertEquals(9.0, result.getTotalCost(), DELTA,
                "A* must select the lower-distance parallel edge");
    }

    // ──────────────────────────────────────────────
    // 12. Heuristic changes exploration priority
    // ──────────────────────────────────────────────

    @Test
    void heuristicChangesExplorationPriority() {
        // Equatorial layout (degrees) so straight-line distances are large and the
        // heuristic genuinely reorders the queue:
        //   S(0,0) east to B(0,0.5) then T(0,1.0); S→T direct edge = 130 km.
        //   Extra neighbours: A far north-east and C far west (both dead ends).
        // g-only ordering (Dijkstra) reaches C and A (cheap edges) well before T;
        // the heuristic f = g + h aims straight at T and never opens C or A.
        GeoPoint s = new GeoPoint(0, 0);
        GeoPoint b = new GeoPoint(0, 0.5);
        GeoPoint t = new GeoPoint(0, 1.0);
        GeoPoint a = new GeoPoint(1.0, 0);
        GeoPoint c = new GeoPoint(-0.5, 0);

        double h = Haversine.distanceKm(s, b);            // ≈ 55.6 km for a half degree
        double seg = h + 2.0;                             // edge = geodesic + 2 km overhead
        double viaB = seg + seg;                          // expected S→B→T cost

        List<Station> stations = List.of(
                station("S", s.latitude(), s.longitude()),
                station("B", b.latitude(), b.longitude()),
                station("T", t.latitude(), t.longitude()),
                station("A", a.latitude(), a.longitude()),
                station("C", c.latitude(), c.longitude()));
        List<Connection> connections = List.of(
                edge("S", "B", seg),
                edge("B", "T", seg),
                edge("S", "T", 130.0),
                edge("S", "A", Haversine.distanceKm(s, a) + 2.0),
                edge("A", "T", Haversine.distanceKm(a, t) + 2.0),
                edge("S", "C", Haversine.distanceKm(s, c) + 2.0));

        AStarRouter router = router(stations, connections);

        RouteResult result = router.route("S", "T");

        assertTrue(result.isFound());
        assertEquals(ids("S", "B", "T"), result.getStationIds(),
                "The 2-edge route through B must beat the direct 130 km edge");
        assertEquals(viaB, result.getTotalCost(), DELTA);

        // A* completes after exploring only S, B and T — A and C were pruned by
        // the heuristic. A g-only (Dijkstra) search would open C (cheap g) and A
        // as well, needing at least 5 stations.
        assertEquals(3, router.getLastExploredStationCount(),
                "Heuristic must prune the dead-end branches");
    }

    // ──────────────────────────────────────────────
    // 13. Correct total distance and endpoints
    // ──────────────────────────────────────────────

    @Test
    void correctTotalDistanceAccumulatedWithoutPrecisionLoss() {
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001),
                station("C", LAT0 + 0.0001, LON0 + 0.0001));
        AStarRouter router = router(stations,
                List.of(edge("A", "B", 8.5), edge("B", "C", 9.25)));

        RouteResult result = router.route("A", "C");

        assertTrue(result.isFound());
        assertEquals(17.75, result.getTotalCost(), DELTA);
    }

    // ──────────────────────────────────────────────
    // 20. Zero-distance handling
    // ──────────────────────────────────────────────

    @Test
    void zeroDistanceHandlingUnderExistingGraphRules() {
        // Two stations sharing one coordinate point: h(A,B) == 0 but the graph
        // edge is still positive, so routing must work and stay optimal.
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0));
        AStarRouter router = router(stations, List.of(edge("A", "B", 8.0)));

        assertEquals(0.0, router.heuristic("A", "B"), DELTA);

        RouteResult result = router.route("A", "B");
        assertTrue(result.isFound());
        assertEquals(ids("A", "B"), result.getStationIds());
        assertEquals(8.0, result.getTotalCost(), DELTA);

        // Source == destination is still a zero-cost, zero-hop route.
        RouteResult same = router.route("A", "A");
        assertTrue(same.isFound());
        assertEquals(0.0, same.getTotalCost(), DELTA);
        assertEquals(0, same.getHopCount());
    }

    // ──────────────────────────────────────────────
    // Heuristic tests
    // ──────────────────────────────────────────────

    @Test
    void heuristicIsZeroForSameStation() {
        AStarRouter router = router(
                List.of(station("A", LAT0, LON0), station("B", LAT0, LON0 + 0.0001)),
                List.of(edge("A", "B", 8.0)));

        assertEquals(0.0, router.heuristic("A", "A"), DELTA);
        assertEquals(0.0, router.heuristic("B", "B"), DELTA);
    }

    @Test
    void heuristicMatchesHaversine() {
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001));
        AStarRouter router = router(stations, List.of(edge("A", "B", 8.0)));

        double expected = Haversine.distanceKm(
                new GeoPoint(LAT0, LON0), new GeoPoint(LAT0, LON0 + 0.0001));
        assertEquals(expected, router.heuristic("A", "B"), DELTA);
    }

    @Test
    void heuristicIsSymmetric() {
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0 + 0.0002, LON0 + 0.0003));
        AStarRouter router = router(stations, List.of(edge("A", "B", 8.0)));

        assertEquals(router.heuristic("A", "B"), router.heuristic("B", "A"), DELTA);
    }

    @Test
    void heuristicRequiresKnownStations() {
        AStarRouter router = router(
                List.of(station("A", LAT0, LON0), station("B", LAT0, LON0 + 0.0001)),
                List.of(edge("A", "B", 8.0)));

        assertThrows(IllegalArgumentException.class, () -> router.heuristic("A", "ZZZ"));
        assertThrows(IllegalArgumentException.class, () -> router.heuristic("ZZZ", "A"));
    }

    // ──────────────────────────────────────────────
    // 20. A* vs Dijkstra
    // ──────────────────────────────────────────────

    @Test
    void astarMatchesDijkstraOnMinimumDistance() {
        // A→B→D = 17 km (unique optimum); alternatives A→C→D = 19, A→C→E→D = 21.
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001),
                station("C", LAT0 + 0.0001, LON0),
                station("D", LAT0 + 0.0001, LON0 + 0.0002),
                station("E", LAT0 + 0.0001, LON0 + 0.0001));
        List<Connection> connections = List.of(
                edge("A", "B", 8.0), edge("B", "D", 9.0),
                edge("A", "C", 12.0), edge("C", "D", 7.0),
                edge("C", "E", 5.0), edge("E", "D", 4.0));

        MetroGraph g = graph(stations, connections);
        AStarRouter aStar = new AStarRouter(g, coordinateMap(stations));
        DijkstraRouter dijkstra = new DijkstraRouter(g);

        RouteResult a = aStar.route("A", "D");
        RouteResult d = dijkstra.route("A", "D", RouteMetric.DISTANCE);

        assertTrue(a.isFound());
        assertTrue(d.isFound());
        assertEquals(d.getTotalCost(), a.getTotalCost(), DELTA,
                "A* and Dijkstra must agree on the minimum distance");
        assertEquals("A", a.getStationIds().get(0));
        assertEquals("D", a.getStationIds().get(a.getStationIds().size() - 1));
        // Unique optimum route on this graph: both must reconstruct it identically.
        assertEquals(d.getStationIds(), a.getStationIds());
    }

    // ──────────────────────────────────────────────
    // 21. A* vs BFS
    // ──────────────────────────────────────────────

    @Test
    void astarFindsMinimumDistanceWhereBfsCountsHops() {
        // A→B→D is 1 + 10 = 11 km (2 hops); A→C→D is 4 + 4 = 8 km (2 hops).
        // BFS sees two equal 2-hop routes; A* must pick the 8 km one.
        List<Station> stations = List.of(
                station("A", LAT0, LON0),
                station("B", LAT0, LON0 + 0.0001),
                station("C", LAT0 + 0.0001, LON0),
                station("D", LAT0 + 0.0001, LON0 + 0.0002));
        List<Connection> connections = List.of(
                edge("A", "B", 1.0), edge("B", "D", 10.0),
                edge("A", "C", 4.0), edge("C", "D", 4.0));

        AStarRouter aStar = router(stations, connections);
        BFSRouter bfs = new BFSRouter(graph(stations, connections));

        RouteResult a = aStar.route("A", "D");
        assertTrue(a.isFound());
        assertEquals(ids("A", "C", "D"), a.getStationIds());
        assertEquals(8.0, a.getTotalCost(), DELTA);

        // BFS only guarantees the hop count; either 2-hop route is acceptable to it.
        RouteResult b = bfs.route("A", "D");
        assertTrue(b.isFound());
        assertEquals(2, b.getHopCount());
    }
}