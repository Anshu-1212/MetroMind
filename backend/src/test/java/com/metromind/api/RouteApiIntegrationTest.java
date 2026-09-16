package com.metromind.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metromind.data.MetroNetwork;
import com.metromind.data.MetroNetworkLoader;
import com.metromind.data.Station;
import com.metromind.graph.MetroGraph;
import com.metromind.graph.MetroGraphBuilder;
import com.metromind.routing.AStarRouter;
import com.metromind.routing.BFSRouter;
import com.metromind.routing.DijkstraRouter;
import com.metromind.routing.GeoPoint;
import com.metromind.routing.RouteMetric;
import com.metromind.routing.RouteResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests: a full Spring context with the <b>real</b> dataset and
 * the <b>real</b> Java routers, exercised over HTTP through MockMvc. Expected
 * route values are cross-checked against freshly-built routers over the same
 * dataset — nothing is mocked or hard-coded.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RouteApiIntegrationTest {

    private static final String SOURCE = "RAJIV_CHOWK";
    private static final String DESTINATION = "NEW_DELHI";
    private static final String CROSS_SOURCE = "DWARKA_SECTOR_21";
    private static final String CROSS_DESTINATION = "HAUZ_KHAS";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    // ------------------------------------------------------------------ routes

    @Test
    void bfsReturnsMinimumHopRouteWithDerivedTotals() throws Exception {
        JsonNode body = postRouteReturning200(SOURCE, DESTINATION, "BFS", null);

        assertEquals("BFS", body.get("algorithm").asText());
        assertTrue(body.get("metric").isNull(), "BFS has no weighted metric");
        assertEquals(SOURCE, body.get("sourceId").asText());
        assertEquals(DESTINATION, body.get("destinationId").asText());
        assertTrue(body.get("found").asBoolean());

        List<String> ids = ids(body);
        assertEquals(SOURCE, ids.getFirst());
        assertEquals(DESTINATION, ids.getLast());
        assertEquals(ids.size() - 1, body.get("hopCount").asInt());
        assertTrue(body.get("totalDistanceKm").asDouble() > 0);
        assertTrue(body.get("totalTravelTimeMinutes").asDouble() > 0);
        assertTrue(body.get("explored").isArray());
    }

    @Test
    void bfsStationPathMatchesTheRealBfsRouter() throws Exception {
        JsonNode body = postRouteReturning200(CROSS_SOURCE, CROSS_DESTINATION, "BFS", null);

        RouteResult expected = new BFSRouter(graph())
                .route(CROSS_SOURCE, CROSS_DESTINATION);

        assertTrue(expected.isFound());
        assertEquals(expected.getStationIds(), ids(body),
                "API BFS path must equal the real BFS path");
        assertEquals(expected.getStationIds().size() - 1, body.get("hopCount").asInt());
    }

    @Test
    void dijkstraByDistanceMatchesTheRealMinimumDistance() throws Exception {
        JsonNode body = postRouteReturning200(CROSS_SOURCE, CROSS_DESTINATION, "DIJKSTRA", "DISTANCE");

        assertEquals("DISTANCE", body.get("metric").asText());
        assertTrue(body.get("found").asBoolean());

        double expected = new DijkstraRouter(graph())
                .route(CROSS_SOURCE, CROSS_DESTINATION, RouteMetric.DISTANCE)
                .getTotalCost();

        assertEquals(expected, body.get("totalDistanceKm").asDouble(), 0.001,
                "API distance total must equal the real Dijkstra total cost");
    }

    @Test
    void dijkstraByTravelTimeMatchesTheRealMinimumTime() throws Exception {
        JsonNode body = postRouteReturning200(CROSS_SOURCE, CROSS_DESTINATION, "DIJKSTRA", "TRAVEL_TIME");

        assertEquals("TRAVEL_TIME", body.get("metric").asText());
        assertTrue(body.get("found").asBoolean());
        assertEquals(ids(body).size() - 1, body.get("hopCount").asInt());

        double expected = new DijkstraRouter(graph())
                .route(CROSS_SOURCE, CROSS_DESTINATION, RouteMetric.TRAVEL_TIME)
                .getTotalCost();

        assertEquals(expected, body.get("totalTravelTimeMinutes").asDouble(), 0.001,
                "API travel-time total must equal the real Dijkstra total cost");
    }

    @Test
    void astarMatchesTheRealAStar() throws Exception {
        JsonNode body = postRouteReturning200(CROSS_SOURCE, CROSS_DESTINATION, "ASTAR", null);

        assertEquals("ASTAR", body.get("algorithm").asText());
        assertTrue(body.get("metric").isNull(), "A* has no weighted metric");
        assertTrue(body.get("found").asBoolean());

        double expected = new AStarRouter(graph(), coordinates())
                .route(CROSS_SOURCE, CROSS_DESTINATION)
                .getTotalCost();

        assertEquals(expected, body.get("totalDistanceKm").asDouble(), 0.001,
                "API A* distance must equal the real A* total cost");
    }

    @Test
    void sameStationIsAZeroHopZeroDistanceRoute() throws Exception {
        JsonNode body = postRouteReturning200(SOURCE, SOURCE, "BFS", null);

        assertTrue(body.get("found").asBoolean());
        assertEquals(0, body.get("hopCount").asInt());
        assertEquals(List.of(SOURCE), ids(body));
        assertEquals(0.0, body.get("totalDistanceKm").asDouble());
        assertEquals(0.0, body.get("totalTravelTimeMinutes").asDouble());
    }

    // ------------------------------------------------------------------ errors

    @Test
    void unknownSourceStationIs404() throws Exception {
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("NOT_A_STATION", DESTINATION, "BFS", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("STATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Unknown station: NOT_A_STATION"));
    }

    @Test
    void unknownDestinationStationIs404() throws Exception {
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(SOURCE, "MISSING", "BFS", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("STATION_NOT_FOUND"));
    }

    @Test
    void unsupportedAlgorithmIs400() throws Exception {
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(SOURCE, DESTINATION, "FLOYD", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_ALGORITHM"));
    }

    @Test
    void unsupportedMetricIs400() throws Exception {
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(SOURCE, DESTINATION, "DIJKSTRA", "HOPS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_METRIC"));
    }

    @Test
    void missingSourceIdIs400() throws Exception {
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationId\":\"NEW_DELHI\",\"algorithm\":\"BFS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("sourceId is required"));
    }

    @Test
    void missingDestinationIdIs400() throws Exception {
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"RAJIV_CHOWK\",\"algorithm\":\"BFS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("destinationId is required"));
    }

    @Test
    void dijkstraWithoutMetricIs400() throws Exception {
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(SOURCE, DESTINATION, "DIJKSTRA", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("metric is required when algorithm is DIJKSTRA"));
    }

    @Test
    void malformedJsonBodyIs400() throws Exception {
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void getOnTheRoutesEndpointIs405() throws Exception {
        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    // ------------------------------------------------------------------ health, CORS

    @Test
    void healthEndpointStillWorks() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void preflightAllowsTheConfiguredViteDevOrigin() throws Exception {
        mockMvc.perform(options("/api/routes")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void unapprovedOriginGetsNoCorsAccess() throws Exception {
        mockMvc.perform(options("/api/routes")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode postRouteReturning200(String source, String destination,
                                           String algorithm, String metric) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(source, destination, algorithm, metric)))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static String json(String source, String destination,
                               String algorithm, String metric) {
        return """
                {
                  "sourceId": "%s",
                  "destinationId": "%s",
                  "algorithm": %s,
                  "metric": %s
                }
                """.formatted(source, destination, quoted(algorithm), quoted(metric));
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** The station IDs in order, matched against the dataset's exact spellings. */
    private static List<String> ids(JsonNode body) {
        List<String> ids = new ArrayList<>();
        for (JsonNode node : body.get("stationIds")) {
            ids.add(node.asText());
        }
        return ids;
    }

    private static MetroNetwork network() throws Exception {
        return MetroNetworkLoader.loadDefault();
    }

    private static MetroGraph graph() throws Exception {
        return MetroGraphBuilder.from(network());
    }

    private static Map<String, GeoPoint> coordinates() throws Exception {
        Map<String, GeoPoint> map = new HashMap<>();
        for (Station station : network().getStations()) {
            map.put(station.getId(), new GeoPoint(station.getLatitude(), station.getLongitude()));
        }
        return map;
    }
}