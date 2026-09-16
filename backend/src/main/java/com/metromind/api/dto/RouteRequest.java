package com.metromind.api.dto;

import com.metromind.api.Algorithm;
import com.metromind.routing.RouteMetric;

/**
 * Request body for {@code POST /api/routes}.
 *
 * <p>Fields:</p>
 * <ul>
 *   <li>{@code sourceId}, {@code destinationId} — existing station IDs from
 *       {@code data/metro-network.json} (e.g. {@code RAJIV_CHOWK}).</li>
 *   <li>{@code algorithm} — {@link Algorithm}: {@code BFS}, {@code DIJKSTRA}, or
 *       {@code ASTAR}.</li>
 *   <li>{@code metric} — {@link RouteMetric}: {@code DISTANCE} or
 *       {@code TRAVEL_TIME}. Required only when {@code algorithm} is
 *       {@code DIJKSTRA}; ignored otherwise. The metric values map directly onto
 *       the existing backend {@link RouteMetric} — no metric is invented here.</li>
 * </ul>
 *
 * <p>Validation happens in the service layer and produces specific 4xx errors:
 * missing/blank station IDs or a missing algorithm yield
 * {@code 400 INVALID_REQUEST}; unknown stations yield {@code 404 STATION_NOT_FOUND};
 * a missing metric on a DIJKSTRA request yields {@code 400 INVALID_REQUEST}.</p>
 */
public record RouteRequest(
        String sourceId,
        String destinationId,
        Algorithm algorithm,
        RouteMetric metric) {
}