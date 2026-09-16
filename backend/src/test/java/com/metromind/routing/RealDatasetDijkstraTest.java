package com.metromind.routing;

import com.metromind.data.MetroNetwork;
import com.metromind.data.MetroNetworkLoader;
import com.metromind.graph.GraphEdge;
import com.metromind.graph.MetroGraph;
import com.metromind.graph.MetroGraphBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: runs {@link DijkstraRouter} against the real Phase 2 Delhi
 * Metro dataset ({@code data/metro-network.json}).
 *
 * <p>The 42-station Yellow+Blue network is a single connected component (they
 * intersect at Rajiv Chowk), so both objectives find routes for every station
 * pair. For each successful route the returned total cost is cross-checked
 * against a direct sum of the graph edges actually traversed.</p>
 */
class RealDatasetDijkstraTest {

    private static final double DELTA = 1e-9;

    @Test
    void dijkstraFindsMinimumDistanceRouteAcrossInterchange() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        MetroGraph graph = MetroGraphBuilder.from(network);
        DijkstraRouter router = new DijkstraRouter(graph);

        RouteResult result = router.route("DWARKA_SECTOR_21", "HAUZ_KHAS", RouteMetric.DISTANCE);

        assertTrue(result.isFound(), "Route across the Rajiv Chowk interchange must be found");
        assertRouteCostMatches(result, graph, "distance",
                GraphEdge::getDistanceKm, DELTA);
        assertNonNegative(result);
    }

    @Test
    void dijkstraFindsMinimumTravelTimeRouteAcrossInterchange() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        MetroGraph graph = MetroGraphBuilder.from(network);
        DijkstraRouter router = new DijkstraRouter(graph);

        RouteResult result = router.route("DWARKA_SECTOR_21", "HAUZ_KHAS", RouteMetric.TRAVEL_TIME);

        assertTrue(result.isFound(), "Route across the Rajiv Chowk interchange must be found");
        assertRouteCostMatches(result, graph, "travel time",
                e -> e.getTravelTimeMinutes(), 0.0);
        assertNonNegative(result);
    }

    @Test
    void dijkstraSourceEqualsDestinationCostsZero() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        DijkstraRouter router = new DijkstraRouter(MetroGraphBuilder.from(network));

        for (RouteMetric metric : RouteMetric.values()) {
            RouteResult result = router.route("NEW_DELHI", "NEW_DELHI", metric);
            assertTrue(result.isFound());
            assertEquals(List.of("NEW_DELHI"), result.getStationIds());
            assertEquals(0, result.getHopCount());
            assertEquals(0.0, result.getTotalCost(), DELTA);
        }
    }

    @Test
    void dijkstraUnknownStationIsNotFound() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        DijkstraRouter router = new DijkstraRouter(MetroGraphBuilder.from(network));

        RouteResult result = router.route("NOT_A_STATION", "RAJIV_CHOWK", RouteMetric.DISTANCE);

        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
    }

    /**
     * Asserts the route invariants and that the returned total cost equals a
     * direct sum of the selected metric over the graph edges actually traversed.
     */
    private static void assertRouteCostMatches(RouteResult result, MetroGraph graph,
                                               String metricLabel,
                                               ToDoubleFunction<GraphEdge> edgeCost,
                                               double tolerance) {
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
                    .min(java.util.Comparator.comparingDouble(edgeCost))
                    .orElseThrow(() -> new AssertionError(
                            "No graph edge between " + current + " and " + next));
            expected += edgeCost.applyAsDouble(chosen);
        }

        assertEquals(expected, result.getTotalCost(), tolerance,
                "totalCost must equal the sum of " + metricLabel + " along the route");
    }

    private static void assertNonNegative(RouteResult result) {
        assertFalse(result.getTotalCost() < 0,
                "total cost must be non-negative");
    }
}