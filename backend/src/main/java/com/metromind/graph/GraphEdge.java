package com.metromind.graph;

import java.util.Objects;

/**
 * A directed edge in the metro graph.
 *
 * <p>Represents a single weighted jump between two adjacent stations on one metro line.
 * The source station is implied by the adjacency list holding this edge; each edge
 * stores its destination explicitly.</p>
 *
 * <p>Edges are immutable. Equality is defined over {@code (destination, line,
 * distanceKm, travelTimeMinutes)} so that two edges between the same stations on
 * different lines are distinct (parallel edges must be preserved for future
 * routing that tracks the passenger's current line).</p>
 */
public final class GraphEdge {

    private final String destination;
    private final String line;
    private final double distanceKm;
    private final int travelTimeMinutes;

    /**
     * Constructs a directed graph edge.
     *
     * @param destination       the destination station ID
     * @param line              the metro line ID this edge belongs to
     * @param distanceKm        the edge distance in kilometres
     * @param travelTimeMinutes the estimated travel time in minutes
     * @throws IllegalArgumentException if destination or line is blank, or if
     *                                  distance/travel time are not positive
     */
    public GraphEdge(String destination, String line, double distanceKm, int travelTimeMinutes) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("GraphEdge destination must not be blank");
        }
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("GraphEdge line must not be blank");
        }
        if (distanceKm <= 0) {
            throw new IllegalArgumentException("GraphEdge distanceKm must be positive: " + distanceKm);
        }
        if (travelTimeMinutes <= 0) {
            throw new IllegalArgumentException("GraphEdge travelTimeMinutes must be positive: " + travelTimeMinutes);
        }
        this.destination = destination;
        this.line = line;
        this.distanceKm = distanceKm;
        this.travelTimeMinutes = travelTimeMinutes;
    }

    /**
     * @return the destination station ID
     */
    public String getDestination() {
        return destination;
    }

    /**
     * @return the metro line ID this edge belongs to
     */
    public String getLine() {
        return line;
    }

    /**
     * @return the edge distance in kilometres
     */
    public double getDistanceKm() {
        return distanceKm;
    }

    /**
     * @return the estimated travel time in minutes
     */
    public int getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GraphEdge other)) {
            return false;
        }
        return distanceKm == other.distanceKm
                && travelTimeMinutes == other.travelTimeMinutes
                && destination.equals(other.destination)
                && line.equals(other.line);
    }

    @Override
    public int hashCode() {
        return Objects.hash(destination, line, distanceKm, travelTimeMinutes);
    }

    @Override
    public String toString() {
        return "GraphEdge{dest='" + destination + "', line='" + line
                + "', distanceKm=" + distanceKm + ", travelTimeMinutes=" + travelTimeMinutes + "}";
    }
}