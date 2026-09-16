package com.metromind.routing;

/**
 * An immutable geographic point on Earth, in decimal degrees (WGS-84 style).
 *
 * <p>Used by {@link AStarRouter} to compute the heuristic estimate between two
 * stations. Station coordinates come from the Phase 2 data model (and the Phase 2
 * validation already enforces valid, Delhi-region coordinates); {@code GeoPoint}
 * re-checks the world-wide ranges defensively so no out-of-range coordinate can
 * reach the heuristic math.</p>
 *
 * <p>This type is deliberately tiny: it carries only latitude/longitude, exactly
 * what the geographic heuristic needs, and nothing else.</p>
 *
 * @param latitude  in degrees, within [-90, 90]
 * @param longitude in degrees, within [-180, 180]
 */
public record GeoPoint(double latitude, double longitude) {

    /**
     * Compact constructor performing range validation.
     *
     * @throws IllegalArgumentException if either coordinate is NaN or out of its
     *                                  legal range
     */
    public GeoPoint {
        if (Double.isNaN(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be within [-90, 90]: " + latitude);
        }
        if (Double.isNaN(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be within [-180, 180]: " + longitude);
        }
    }
}