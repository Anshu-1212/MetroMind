package com.metromind.routing;

import com.metromind.data.MetroNetwork;
import com.metromind.data.MetroNetworkLoader;
import com.metromind.data.Station;
import com.metromind.graph.GraphEdge;
import com.metromind.graph.MetroGraph;
import com.metromind.graph.MetroGraphBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: runs {@link AStarRouter} against the real Phase 2 Delhi
 * Metro dataset ({@code data/metro-network.json}).
 *
 * <p>The 42-station Yellow+Blue network is a single connected component (they
 * intersect at Rajiv Chowk), so A* finds a minimum-distance route for every
 * station pair. The A* total distance is cross-checked against a direct sum of
 * the graph edges actually traversed, and against
 * {@link DijkstraRouter} for the same query (both must agree).</p>
 */
class RealDatasetAStarTest {

    private static final double DELTA = 1e-9;

    @Test
    void astarFindsMinimumDistanceRouteAcrossInterchange() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        AStarRouter router = new AStarRouter(
                MetroGraphBuilder.from(network), coordinatesOf(network));

        RouteResult result = router.route("DWARKA_SECTOR_21", "HAUZ_KHAS");

        assertTrue(result.isFound(), "Route across the Rajiv Chowk interchange must be found");
        assertRouteCostMatches(result, network);
        assertTrue(result.getTotalCost() > 0,
                "Total distance between two distinct stations must be positive");
    }

    @Test
    void astarAndDijkstraAgreeOnMinimumDistance() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        MetroGraph graph = MetroGraphBuilder.from(network);
        AStarRouter aStar = new AStarRouter(graph, coordinatesOf(network));
        DijkstraRouter dijkstra = new DijkstraRouter(graph);

        RouteResult a = aStar.route("DWARKA_SECTOR_21", "HAUZ_KHAS");
        RouteResult d = dijkstra.route("DWARKA_SECTOR_21", "HAUZ_KHAS", RouteMetric.DISTANCE);

        assertTrue(a.isFound());
        assertTrue(d.isFound());
        assertEquals(d.getTotalCost(), a.getTotalCost(), DELTA,
                "A* and Dijkstra must find the same minimum distance");
        assertEquals(d.getStationIds().get(0), a.getStationIds().get(0));
        assertEquals(d.getStationIds().get(d.getStationIds().size() - 1),
                a.getStationIds().get(a.getStationIds().size() - 1));
    }

    @Test
    void astarSourceEqualsDestinationZeroCost() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        AStarRouter router = new AStarRouter(
                MetroGraphBuilder.from(network), coordinatesOf(network));

        RouteResult result = router.route("NEW_DELHI", "NEW_DELHI");

        assertTrue(result.isFound());
        assertEquals(List.of("NEW_DELHI"), result.getStationIds());
        assertEquals(0, result.getHopCount());
        assertEquals(0.0, result.getTotalCost(), DELTA);
    }

    @Test
    void astarUnknownStationIsNotFound() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        AStarRouter router = new AStarRouter(
                MetroGraphBuilder.from(network), coordinatesOf(network));

        RouteResult result = router.route("NOT_A_STATION", "RAJIV_CHOWK");

        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
    }

    @Test
    void datasetEdgesDominateHaversineDistances() throws Exception {
        // The admissibility precondition: every rail edge is at least as long as
        // the straight line between its endpoints. The Phase 2 dataset rounds
        // distances to one decimal (0.1 km), which can under-report a true value
        // by at most 0.05 km, so a small rounding slack is allowed. A violation
        // beyond that slack would break the heuristic's lower-bound guarantee.
        final double roundingSlack = 0.051;

        MetroNetwork network = MetroNetworkLoader.loadDefault();
        MetroGraph graph = MetroGraphBuilder.from(network);
        Map<String, GeoPoint> coords = coordinatesOf(network);

        for (String source : graph.getStationIds()) {
            for (GraphEdge edge : graph.getNeighbors(source)) {
                double straightLine = Haversine.distanceKm(coords.get(source),
                        coords.get(edge.getDestination()));
                assertTrue(edge.getDistanceKm() + roundingSlack >= straightLine,
                        "Edge " + source + " -> " + edge.getDestination()
                                + " (" + edge.getDistanceKm() + " km) is more than the "
                                + "0.05 km rounding slack below the straight-line distance "
                                + straightLine + " km");
            }
        }
    }

    private static Map<String, GeoPoint> coordinatesOf(MetroNetwork network) {
        Map<String, GeoPoint> coords = new HashMap<>();
        for (Station s : network.getStations()) {
            coords.put(s.getId(), new GeoPoint(s.getLatitude(), s.getLongitude()));
        }
        return coords;
    }

    private static void assertRouteCostMatches(RouteResult result, MetroNetwork network) {
        MetroGraph graph = MetroGraphBuilder.from(network);
        List<String> route = result.getStationIds();

        assertEquals(route.size(), route.stream().distinct().count(),
                "Route must not contain repeated station IDs");
        assertEquals(route.size() - 1, result.getHopCount(),
                "Hop count must equal station count minus one");

        double expected = 0.0;
        for (int i = 0; i < route.size() - 1; i++) {
            String current = route.get(i);
            String next = route.get(i + 1);
            GraphEdge chosen = graph.getNeighbors(current).stream()
                    .filter(e -> e.getDestination().equals(next))
                    .min(java.util.Comparator.comparingDouble(GraphEdge::getDistanceKm))
                    .orElseThrow(() -> new AssertionError(
                            "No graph edge between " + current + " and " + next));
            expected += chosen.getDistanceKm();
        }

        assertEquals(expected, result.getTotalCost(), DELTA,
                "totalCost must equal the sum of edge distances along the route");
    }
}