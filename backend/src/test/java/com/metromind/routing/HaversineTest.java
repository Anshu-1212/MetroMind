package com.metromind.routing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Haversine} geodesic distance.
 */
class HaversineTest {

    private static final double DELTA = 1e-9;

    @Test
    void identicalPointsAreZero() {
        assertEquals(0.0, Haversine.distanceKm(new GeoPoint(28.6, 77.2),
                new GeoPoint(28.6, 77.2)), DELTA);
        assertEquals(0.0, Haversine.distanceKm(new GeoPoint(0, 0),
                new GeoPoint(0, 0)), DELTA);
    }

    @Test
    void degreeOfLongitudeAtEquatorIsAbout111Km() {
        // One degree of longitude at the equator ≈ 111.19 km (R = 6371 km).
        double d = Haversine.distanceKm(new GeoPoint(0, 0), new GeoPoint(0, 1));
        assertEquals(111.19, d, 0.5);
    }

    @Test
    void degreeOfLatitudeIsAbout111Km() {
        double d = Haversine.distanceKm(new GeoPoint(0, 0), new GeoPoint(1, 0));
        assertEquals(111.19, d, 0.5);
    }

    @Test
    void distanceIsSymmetric() {
        GeoPoint a = new GeoPoint(28.6139, 77.2090);   // Rajiv Chowk
        GeoPoint b = new GeoPoint(28.5672, 77.2272);   // Huda City Centre-ish
        assertEquals(Haversine.distanceKm(a, b),
                Haversine.distanceKm(b, a), DELTA);
    }

    @Test
    void knownCityPairWithinTolerance() {
        // London → Paris ≈ 343–344 km.
        double d = Haversine.distanceKm(
                new GeoPoint(51.5074, -0.1278),
                new GeoPoint(48.8566, 2.3522));
        assertEquals(344.0, d, 6.0);
    }

    @Test
    void shortestArcAcrossTheAntimeridian() {
        // (0, 179.9) and (0, -179.9) are only 0.2° apart east-west:
        // the formula must return the short (~22 km) arc, not a ~40 000 km detour.
        double d = Haversine.distanceKm(new GeoPoint(0, 179.9), new GeoPoint(0, -179.9));
        assertEquals(0.2 * 111.19, d, 0.5);
    }

    @Test
    void rejectsNullPoints() {
        GeoPoint p = new GeoPoint(28.6, 77.2);
        assertThrows(NullPointerException.class, () -> Haversine.distanceKm(null, p));
        assertThrows(NullPointerException.class, () -> Haversine.distanceKm(p, null));
    }

    @Test
    void geoPointRejectsOutOfRangeCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(91, 0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(-91, 0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(28.6, 181));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(28.6, -181));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.NaN, 0));
    }
}