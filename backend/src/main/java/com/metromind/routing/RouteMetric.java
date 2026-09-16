package com.metromind.routing;

/**
 * The routing objective a weighted shortest-path query should optimise.
 *
 * <p>MetroMind deliberately keeps the two weighted objectives independent:
 * distance and travel time are never combined into one arbitrary score. The
 * caller must choose exactly one metric per query.</p>
 *
 * <ul>
 *   <li>{@link #DISTANCE} minimises the sum of {@code GraphEdge.distanceKm}.</li>
 *   <li>{@link #TRAVEL_TIME} minimises the sum of {@code GraphEdge.travelTimeMinutes}.</li>
 * </ul>
 *
 * @see DijkstraRouter
 */
public enum RouteMetric {

    /** Minimise total physical distance: {@code sum(edge.getDistanceKm())}. */
    DISTANCE,

    /** Minimise total travel time: {@code sum(edge.getTravelTimeMinutes())}. */
    TRAVEL_TIME
}