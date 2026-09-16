package com.metromind.routing;

import com.metromind.data.MetroNetwork;
import com.metromind.data.MetroNetworkLoader;
import com.metromind.graph.MetroGraph;
import com.metromind.graph.MetroGraphBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: runs {@link BFSRouter} against the real Phase 2 Delhi Metro
 * dataset ({@code data/metro-network.json}).
 *
 * <p>Dataset note: the 42-station Yellow+Blue network is a single connected
 * component (they intersect at Rajiv Chowk), so every station pair is reachable.
 * The disconnected-components case is covered by the hand-built unit test
 * {@code BFSRouterTest.unreachableDestinationIsNotFound}.</p>
 */
class RealDatasetBfsTest {

    @Test
    void bfsFindsRouteWithinOneLine() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        MetroGraph graph = MetroGraphBuilder.from(network);
        BFSRouter router = new BFSRouter(graph);

        // Two Yellow Line stations.
        RouteResult result = router.route("SAMAYPUR_BADLI", "HAUZ_KHAS");

        assertTrue(result.isFound(), "Route between two Yellow Line stations must be found");
        assertRouteValid(result, graph);
    }

    @Test
    void bfsFindsRouteAcrossInterchange() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        MetroGraph graph = MetroGraphBuilder.from(network);
        BFSRouter router = new BFSRouter(graph);

        // Blue Line station to Yellow Line station — must cross the Rajiv Chowk interchange.
        RouteResult result = router.route("DWARKA_SECTOR_21", "HAUZ_KHAS");

        assertTrue(result.isFound(), "Route across the Rajiv Chowk interchange must be found");
        assertRouteValid(result, graph);

        // Must pass through Rajiv Chowk, the only interchange in the dataset.
        assertTrue(result.getStationIds().contains("RAJIV_CHOWK"),
                "Cross-line route must pass through the interchange station");
    }

    @Test
    void bfsReturnsZeroHopsForSameStation() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        BFSRouter router = new BFSRouter(MetroGraphBuilder.from(network));

        RouteResult result = router.route("NEW_DELHI", "NEW_DELHI");

        assertTrue(result.isFound());
        assertEquals(List.of("NEW_DELHI"), result.getStationIds());
        assertEquals(0, result.getHopCount());
    }

    @Test
    void bfsHandlesUnknownStationInRealDataset() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        BFSRouter router = new BFSRouter(MetroGraphBuilder.from(network));

        RouteResult result = router.route("NOT_A_STATION", "RAJIV_CHOWK");

        assertFalse(result.isFound());
        assertTrue(result.getStationIds().isEmpty());
    }

    /**
     * Asserts the generic route invariants against the source graph: endpoints,
     * consecutive adjacency, hop-count arithmetic, and no repeated stations.
     */
    private static void assertRouteValid(RouteResult result, MetroGraph graph) {
        List<String> route = result.getStationIds();
        assertEquals(route.size(), route.stream().distinct().count(),
                "Route must not contain repeated station IDs");

        assertEquals(route.size() - 1, result.getHopCount(),
                "Hop count must equal station count minus one");

        for (int i = 0; i < route.size() - 1; i++) {
            String current = route.get(i);
            String next = route.get(i + 1);
            boolean connected = graph.getNeighbors(current).stream()
                    .anyMatch(e -> e.getDestination().equals(next));
            assertTrue(connected, "No graph edge between " + current + " and " + next);
        }
    }
}