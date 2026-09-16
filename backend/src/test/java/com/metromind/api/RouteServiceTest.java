package com.metromind.api;

import com.metromind.api.dto.RouteRequest;
import com.metromind.api.dto.RouteResponse;
import com.metromind.data.Connection;
import com.metromind.data.MetroNetwork;
import com.metromind.data.Station;
import com.metromind.graph.MetroGraph;
import com.metromind.graph.MetroGraphBuilder;
import com.metromind.routing.AStarRouter;
import com.metromind.routing.BFSRouter;
import com.metromind.routing.DijkstraRouter;
import com.metromind.routing.GeoPoint;
import com.metromind.routing.RouteMetric;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Service-level tests using a small <b>synthetic</b> network with two
 * disconnected components.
 *
 * <p>The real 42-station dataset is a single connected component, so a
 * {@code found: false} response cannot be produced from it. This fixture lets
 * that genuine branch be exercised with the <b>real</b> routers
 * ({@link BFSRouter}, {@link DijkstraRouter}, {@link AStarRouter}) — no mocks and
 * no stubbed results.</p>
 *
 * <pre>
 *   A ── B          (line L1)          two components:
 *   C ── D          (line L2)          A/B and C/D are mutually unreachable
 * </pre>
 */
class RouteServiceTest {

    @Test
    void disconnectedStationsReturnFoundFalse() {
        RouteResponse response = service().route(
                new RouteRequest("A", "C", Algorithm.BFS, null));

        assertFalse(response.found(), "A and C are in different components");
        assertEquals(Algorithm.BFS, response.algorithm());
        assertEquals("A", response.sourceId());
        assertEquals("C", response.destinationId());
        assertTrue(response.stationIds().isEmpty());
        assertEquals(0, response.hopCount());
        assertEquals(0.0, response.totalDistanceKm());
        assertEquals(0.0, response.totalTravelTimeMinutes());
        assertTrue(response.explored().isEmpty());
    }

    @Test
    void disconnectedStationsReturnFoundFalseForDijkstra() {
        RouteResponse response = service().route(
                new RouteRequest("A", "D", Algorithm.DIJKSTRA, RouteMetric.DISTANCE));

        assertFalse(response.found());
        assertEquals(RouteMetric.DISTANCE, response.metric());
        assertTrue(response.stationIds().isEmpty());
    }

    @Test
    void connectedStationsReturnTheRealRoute() {
        RouteResponse response = service().route(
                new RouteRequest("A", "B", Algorithm.BFS, null));

        assertTrue(response.found());
        assertEquals(List.of("A", "B"), response.stationIds());
        assertEquals(1, response.hopCount());
        assertEquals(1.0, response.totalDistanceKm(), 1e-9);
        assertEquals(2.0, response.totalTravelTimeMinutes(), 1e-9);
    }

    @Test
    void unknownStationIs404() {
        RouteApiException ex = assertThrows(RouteApiException.class, () ->
                service().route(new RouteRequest("A", "Z", Algorithm.BFS, null)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("STATION_NOT_FOUND", ex.getError());
    }

    @Test
    void missingSourceIs400() {
        RouteApiException ex = assertThrows(RouteApiException.class, () ->
                service().route(new RouteRequest(" ", "B", Algorithm.BFS, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_REQUEST", ex.getError());
        assertEquals("sourceId is required", ex.getMessage());
    }

    @Test
    void missingAlgorithmIs400() {
        RouteApiException ex = assertThrows(RouteApiException.class, () ->
                service().route(new RouteRequest("A", "B", null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("algorithm is required", ex.getMessage());
    }

    @Test
    void dijkstraWithoutMetricIs400() {
        RouteApiException ex = assertThrows(RouteApiException.class, () ->
                service().route(new RouteRequest("A", "B", Algorithm.DIJKSTRA, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("metric is required when algorithm is DIJKSTRA", ex.getMessage());
    }

    // ------------------------------------------------------------------ fixture

    /** A validated two-component network: A–B on L1, C–D on L2. */
    private static MetroNetwork fixture() {
        MetroNetwork network = new MetroNetwork();
        network.setStations(List.of(
                new Station("A", "Alpha", 28.60, 77.20, List.of("L1"), false),
                new Station("B", "Bravo", 28.61, 77.21, List.of("L1"), false),
                new Station("C", "Charlie", 28.70, 77.30, List.of("L2"), false),
                new Station("D", "Delta", 28.71, 77.31, List.of("L2"), false)));
        network.setConnections(List.of(
                new Connection("A", "B", "L1", 1.0, 2),
                new Connection("C", "D", "L2", 1.0, 2)));
        return network;
    }

    private static RouteService service() {
        MetroNetwork network = fixture();
        MetroGraph graph = MetroGraphBuilder.from(network);

        Map<String, GeoPoint> coordinates = new HashMap<>();
        for (Station station : network.getStations()) {
            coordinates.put(station.getId(),
                    new GeoPoint(station.getLatitude(), station.getLongitude()));
        }

        return new RouteService(
                graph,
                new BFSRouter(graph),
                new DijkstraRouter(graph),
                new AStarRouter(graph, coordinates));
    }
}