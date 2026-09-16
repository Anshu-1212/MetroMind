package com.metromind.api.dto;

import com.metromind.api.Algorithm;
import com.metromind.routing.RouteMetric;

import java.util.List;

/**
 * Response body for {@code POST /api/routes}.
 *
 * <p>Every field is derived from the actual {@link com.metromind.routing.RouteResult}
 * returned by the selected backend router — nothing is fabricated. All values are
 * deterministic: the same request always produces the same response.</p>
 *
 * <ul>
 *   <li>{@code algorithm} — echoes the requested {@link Algorithm}.</li>
 *   <li>{@code metric} — the routing objective in the units used by the
 *       algorithm: {@code DISTANCE} or {@code TRAVEL_TIME} for a DIJKSTRA query,
 *       {@code null} for BFS and A* (which have no weighted objective).</li>
 *   <li>{@code sourceId} / {@code destinationId} — echoed from the request.</li>
 *   <li>{@code found} — {@code true} when a route exists.</li>
 *   <li>{@code stationIds} — ordered stations from source to destination; empty
 *       when {@code found} is {@code false}.</li>
 *   <li>{@code hopCount} — number of station-to-station hops.</li>
 *   <li>{@code totalDistanceKm} / {@code totalTravelTimeMinutes} — derived by
 *       summing each {@code GraphEdge} traversed along the returned route. Both
 *       are always present (including for BFS, whose route carries no single
 *       scalar cost on its own).</li>
 *   <li>{@code explored} — the stations inspected during the search. The existing
 *       Java routers do not expose their exploration order (A* exposes only an
 *       internal count), so this is always an empty list here. It is kept as a
 *       stable contract field for the future frontend integration.</li>
 * </ul>
 */
public record RouteResponse(
        Algorithm algorithm,
        RouteMetric metric,
        String sourceId,
        String destinationId,
        boolean found,
        List<String> stationIds,
        int hopCount,
        double totalDistanceKm,
        double totalTravelTimeMinutes,
        List<String> explored) {

    /**
     * Response for a valid request where no route exists (disconnected stations).
     * Totals and the station list are zero/empty; the request echo is preserved.
     */
    public static RouteResponse notFound(Algorithm algorithm, RouteMetric metric,
                                          String sourceId, String destinationId) {
        return new RouteResponse(algorithm, metric, sourceId, destinationId,
                false, List.of(), 0, 0.0, 0.0, List.of());
    }
}