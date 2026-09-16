package com.metromind.api;

import com.metromind.api.dto.RouteRequest;
import com.metromind.api.dto.RouteResponse;
import com.metromind.graph.GraphEdge;
import com.metromind.graph.MetroGraph;
import com.metromind.routing.AStarRouter;
import com.metromind.routing.BFSRouter;
import com.metromind.routing.DijkstraRouter;
import com.metromind.routing.RouteMetric;
import com.metromind.routing.RouteResult;
import org.springframework.http.HttpStatus;

import java.util.Comparator;
import java.util.List;

/**
 * The routing bridge between HTTP and the existing Java algorithms.
 *
 * <p>Owns request validation, algorithm selection, execution of the <b>real</b>
 * routers in {@code com.metromind.routing}, and conversion of a
 * {@link RouteResult} into the {@link RouteResponse} JSON contract. It contains
 * no routing logic of its own and never duplicates an algorithm — it is a thin,
 * deterministic adapter.</p>
 *
 * <p>Error mapping:</p>
 * <ul>
 *   <li>missing/blank {@code sourceId}, {@code destinationId}, or a missing
 *       {@code algorithm} → {@code 400 INVALID_REQUEST};</li>
 *   <li>missing {@code metric} when {@code algorithm} is DIJKSTRA →
 *       {@code 400 INVALID_REQUEST};</li>
 *   <li>a station ID that does not exist in the network →
 *       {@code 404 STATION_NOT_FOUND} (before any router runs).</li>
 * </ul>
 *
 * <p>Unknown algorithm/metric <em>values</em> are rejected earlier by JSON
 * deserialization (see {@link ApiExceptionHandler}) because the request DTO is
 * enum-typed.</p>
 *
 * <p>When both stations are valid but disconnected, the selected router returns a
 * not-found {@link RouteResult} and the service answers {@code 200} with
 * {@code found: false}. (The current 42-station dataset is a single connected
 * component, so this path is exercised by tests over a synthetic graph.)</p>
 */
public final class RouteService {

    /** Tie-break so totals are deterministic even for hypothetical parallel edges. */
    private static final Comparator<GraphEdge> EDGE_ORDER =
            Comparator.comparingDouble(GraphEdge::getDistanceKm)
                    .thenComparingDouble(GraphEdge::getTravelTimeMinutes)
                    .thenComparing(GraphEdge::getLine);

    private final MetroGraph graph;
    private final BFSRouter bfsRouter;
    private final DijkstraRouter dijkstraRouter;
    private final AStarRouter aStarRouter;

    public RouteService(MetroGraph graph, BFSRouter bfsRouter,
                        DijkstraRouter dijkstraRouter, AStarRouter aStarRouter) {
        if (graph == null) {
            throw new NullPointerException("graph must not be null");
        }
        if (bfsRouter == null) {
            throw new NullPointerException("bfsRouter must not be null");
        }
        if (dijkstraRouter == null) {
            throw new NullPointerException("dijkstraRouter must not be null");
        }
        if (aStarRouter == null) {
            throw new NullPointerException("aStarRouter must not be null");
        }
        this.graph = graph;
        this.bfsRouter = bfsRouter;
        this.dijkstraRouter = dijkstraRouter;
        this.aStarRouter = aStarRouter;
    }

    /**
     * Parses, validates, and executes a route query against the real dataset and
     * the real Java algorithms.
     *
     * @param request the validated-by-shape request; must not be null
     * @return the deterministic {@link RouteResponse}
     * @throws RouteApiException for invalid requests ({@code 400} / {@code 404})
     */
    public RouteResponse route(RouteRequest request) {
        if (request == null) {
            throw badRequest("Request body is required");
        }

        Algorithm algorithm = requireAlgorithm(request.algorithm());
        String sourceId = requireStationId(request.sourceId(), "sourceId");
        String destinationId = requireStationId(request.destinationId(), "destinationId");

        requireKnownStation(sourceId);
        requireKnownStation(destinationId);

        RouteMetric metric = requireMetricFor(algorithm, request.metric());

        RouteResult result = execute(algorithm, metric, sourceId, destinationId);
        return toResponse(algorithm, metric, sourceId, destinationId, result);
    }

    /** Performs validation checks that do not need a body-value switch. */
    private static String requireStationId(String id, String field) {
        if (id == null || id.isBlank()) {
            throw badRequest(field + " is required");
        }
        return id;
    }

    private static Algorithm requireAlgorithm(Algorithm algorithm) {
        if (algorithm == null) {
            throw badRequest("algorithm is required");
        }
        return algorithm;
    }

    private void requireKnownStation(String stationId) {
        if (!graph.containsStation(stationId)) {
            throw new RouteApiException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND",
                    "Unknown station: " + stationId);
        }
    }

    /**
     * The metric is meaningful only for Dijkstra (the weighted router). BFS and
     * A* minimise hops / distance regardless of any supplied metric, so the value
     * is ignored for them and a missing metric is only an error for DIJKSTRA.
     */
    private static RouteMetric requireMetricFor(Algorithm algorithm, RouteMetric metric) {
        if (algorithm == Algorithm.DIJKSTRA && metric == null) {
            throw badRequest("metric is required when algorithm is DIJKSTRA");
        }
        return metric;
    }

    /** Dispatches to the existing router for the selected algorithm. */
    private RouteResult execute(Algorithm algorithm, RouteMetric metric,
                                String sourceId, String destinationId) {
        return switch (algorithm) {
            case BFS -> bfsRouter.route(sourceId, destinationId);
            case DIJKSTRA -> dijkstraRouter.route(sourceId, destinationId, metric);
            case ASTAR -> aStarRouter.route(sourceId, destinationId);
        };
    }

    /**
     * Converts the real {@link RouteResult} into the response, deriving the two
     * journey totals by summing the {@link GraphEdge}s traversed along the
     * returned station path. The dataset stores exactly one edge per direction
     * per pair, so the walk is exact and deterministic; {@code EDGE_ORDER} keeps
     * it stable should parallel edges ever appear.
     */
    private RouteResponse toResponse(Algorithm algorithm, RouteMetric metric,
                                     String sourceId, String destinationId,
                                     RouteResult result) {
        if (!result.isFound()) {
            return RouteResponse.notFound(algorithm, metric, sourceId, destinationId);
        }

        List<String> stationIds = result.getStationIds();
        double distance = 0.0;
        double travelTime = 0.0;
        for (int i = 0; i < stationIds.size() - 1; i++) {
            GraphEdge edge = edgeBetween(stationIds.get(i), stationIds.get(i + 1));
            distance += edge.getDistanceKm();
            travelTime += edge.getTravelTimeMinutes();
        }

        return new RouteResponse(
                algorithm, metric, sourceId, destinationId,
                true, stationIds, result.getHopCount(),
                round(distance), round(travelTime), List.of());
    }

    private GraphEdge edgeBetween(String from, String to) {
        return graph.getNeighbors(from).stream()
                .filter(edge -> edge.getDestination().equals(to))
                .min(EDGE_ORDER)
                .orElseThrow(() -> new IllegalStateException(
                        "Returned route contains a missing connection: "
                                + from + " -> " + to));
    }

    /** Rounds to three decimals so sums of one-decimal edges serialize cleanly. */
    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static RouteApiException badRequest(String message) {
        return new RouteApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}