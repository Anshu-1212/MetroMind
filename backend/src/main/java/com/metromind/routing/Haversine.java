package com.metromind.routing;

/**
 * Great-circle (Haversine) distance in kilometres between two {@link GeoPoint}s.
 *
 * <p>This is the single place where geographic distance math lives; A*
 * (and any future consumer) calls {@link #distanceKm(GeoPoint, GeoPoint)}
 * rather than re-deriving the formula. It implements the standard formula</p>
 *
 * <pre>
 * a = sin²(Δφ/2) + cos φ₁ · cos φ₂ · sin²(Δλ/2)
 * c = 2 · atan2(√a, √(1 − a))
 * d = R · c                (R = Earth mean radius ≈ 6371 km)
 * </pre>
 *
 * <p>The {@code atan2} form is numerically stable, and the squared-sine form is
 * periodic, so the result is correct for points across the antipodal meridian
 * (the shortest arc, not the raw longitude difference, is returned).</p>
 */
public final class Haversine {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Haversine() {
    }

    /**
     * Computes the straight-line distance between two points on a sphere.
     *
     * @param a the first point
     * @param b the second point
     * @return the great-circle distance in kilometres
     * @throws NullPointerException if either point is null
     */
    public static double distanceKm(GeoPoint a, GeoPoint b) {
        if (a == null || b == null) {
            throw new NullPointerException("Haversine requires two non-null points");
        }

        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(b.longitude() - a.longitude());

        double sinLat = Math.sin(deltaLat / 2.0);
        double sinLon = Math.sin(deltaLon / 2.0);
        double aSquared = sinLat * sinLat
                + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        double centralAngle = 2.0 * Math.atan2(
                Math.sqrt(aSquared), Math.sqrt(1.0 - aSquared));

        return EARTH_RADIUS_KM * centralAngle;
    }
}