package com.metromind.api.dto;

/**
 * A small, consistent error body returned for every failed API request.
 *
 * <p>{@code error} is a stable, machine-readable code; {@code message} is a
 * short human-readable explanation. No stack traces or internal details are ever
 * included in a response body.</p>
 *
 * <p>Codes currently produced by the API:</p>
 * <ul>
 *   <li>{@code INVALID_REQUEST} — malformed body, a required field missing, or
 *       the metric missing on a DIJKSTRA request (HTTP 400).</li>
 *   <li>{@code UNSUPPORTED_ALGORITHM} — unknown {@code algorithm} value (HTTP 400).</li>
 *   <li>{@code UNSUPPORTED_METRIC} — unknown {@code metric} value (HTTP 400).</li>
 *   <li>{@code STATION_NOT_FOUND} — unknown {@code sourceId} or {@code destinationId}
 *       (HTTP 404).</li>
 *   <li>{@code INTERNAL_ERROR} — unexpected server failure (HTTP 500).</li>
 * </ul>
 */
public record ApiError(String error, String message) {
}