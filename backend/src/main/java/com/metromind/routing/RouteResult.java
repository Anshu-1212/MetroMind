package com.metromind.routing;

import java.util.List;

/**
 * The result of a route-finding query.
 *
 * <p>Encapsulates whether a route was found, the ordered station IDs along the
 * route, and the number of station-to-station hops. The result is immutable;
 * internal algorithm state (queue, visited set, predecessor map) is deliberately
 * not exposed.</p>
 *
 * <p>Contract:</p>
 * <ul>
 *   <li>{@code isFound() == true} — {@code stationIds} is non-empty and
 *       {@code hopCount == stationIds.size() - 1}.</li>
 *   <li>{@code isFound() == false} — {@code stationIds} is empty and
 *       {@code hopCount == 0}.</li>
 *   <li>A found route where source equals destination contains exactly one
 *       station ID and has {@code hopCount == 0}.</li>
 * </ul>
 */
public final class RouteResult {

    private final boolean found;
    private final List<String> stationIds;
    private final int hopCount;

    private RouteResult(boolean found, List<String> stationIds) {
        this.found = found;
        this.stationIds = stationIds;
        this.hopCount = found ? stationIds.size() - 1 : 0;
    }

    /**
     * Creates a result for a successful route.
     *
     * @param stationIds the ordered station IDs from source to destination
     * @return a found {@link RouteResult}
     */
    public static RouteResult found(List<String> stationIds) {
        List<String> copy = List.copyOf(stationIds);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("A found route must contain at least one station");
        }
        return new RouteResult(true, copy);
    }

    /**
     * Creates a result for a query where no route could be found.
     *
     * @return a not-found {@link RouteResult}
     */
    public static RouteResult notFound() {
        return new RouteResult(false, List.of());
    }

    /**
     * @return {@code true} if a route was found
     */
    public boolean isFound() {
        return found;
    }

    /**
     * @return the ordered station IDs from source to destination;
     *         an empty (unmodifiable) list when the route was not found
     */
    public List<String> getStationIds() {
        return stationIds;
    }

    /**
     * @return the number of station-to-station hops; equals {@code stationIds.size() - 1}
     *         when a route was found, 0 otherwise
     */
    public int getHopCount() {
        return hopCount;
    }

    @Override
    public String toString() {
        return found
                ? "RouteResult{found=true, stationIds=" + stationIds + ", hopCount=" + hopCount + "}"
                : "RouteResult{found=false}";
    }
}