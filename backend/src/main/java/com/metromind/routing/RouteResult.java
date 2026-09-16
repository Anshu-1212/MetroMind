package com.metromind.routing;

import java.util.List;

/**
 * The result of a route-finding query.
 *
 * <p>Encapsulates whether a route was found, the ordered station IDs along the
 * route, the number of station-to-station hops, and — for weighted routing —
 * the total cost of the route under the selected metric. The result is
 * immutable; internal algorithm state (queue, visited set, predecessor map) is
 * deliberately not exposed.</p>
 *
 * <p>Contract:</p>
 * <ul>
 *   <li>{@code isFound() == true} — {@code stationIds} is non-empty and
 *       {@code hopCount == stationIds.size() - 1}.</li>
 *   <li>{@code isFound() == false} — {@code stationIds} is empty and
 *       {@code hopCount == 0}.</li>
 *   <li>A found route where source equals destination contains exactly one
 *       station ID and has {@code hopCount == 0}.</li>
 *   <li>{@link #getTotalCost()} is the cost of the route <em>in the units of
 *       the metric that produced it</em> (kilometres for
 *       {@link RouteMetric#DISTANCE}, minutes for
 *       {@link RouteMetric#TRAVEL_TIME}). It is {@code Double.NaN} when the
 *       result carries no single scalar cost — that is, results from the
 *       unweighted {@link BFSRouter} and {@link #notFound()} results.</li>
 * </ul>
 */
public final class RouteResult {

    private final boolean found;
    private final List<String> stationIds;
    private final int hopCount;
    private final double totalCost;

    private RouteResult(boolean found, List<String> stationIds, double totalCost) {
        this.found = found;
        this.stationIds = stationIds;
        this.hopCount = found ? stationIds.size() - 1 : 0;
        this.totalCost = totalCost;
    }

    /**
     * Creates a result for a successful unweighted route (used by
     * {@link BFSRouter}). The cost is left undefined ({@code Double.NaN})
     * because an unweighted route has no single scalar cost.
     *
     * @param stationIds the ordered station IDs from source to destination
     * @return a found {@link RouteResult}
     */
    public static RouteResult found(List<String> stationIds) {
        List<String> copy = List.copyOf(stationIds);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("A found route must contain at least one station");
        }
        return new RouteResult(true, copy, Double.NaN);
    }

    /**
     * Creates a result for a successful weighted route.
     *
     * @param stationIds the ordered station IDs from source to destination
     * @param totalCost  the route cost in the units of the selected
     *                   {@link RouteMetric} (kilometres or minutes)
     * @return a found {@link RouteResult}
     * @throws IllegalArgumentException if {@code stationIds} is empty or
     *                                  {@code totalCost} is negative or NaN
     */
    public static RouteResult found(List<String> stationIds, double totalCost) {
        List<String> copy = List.copyOf(stationIds);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("A found route must contain at least one station");
        }
        if (totalCost < 0 || Double.isNaN(totalCost)) {
            throw new IllegalArgumentException(
                    "A found route's total cost must be non-negative: " + totalCost);
        }
        return new RouteResult(true, copy, totalCost);
    }

    /**
     * Creates a result for a query where no route could be found.
     *
     * @return a not-found {@link RouteResult}
     */
    public static RouteResult notFound() {
        return new RouteResult(false, List.of(), Double.NaN);
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

    /**
     * @return the route cost in the units of the metric that produced it —
     *         kilometres for {@link RouteMetric#DISTANCE}, minutes for
     *         {@link RouteMetric#TRAVEL_TIME}. Zero for a source-to-same-station
     *         route. Returns {@code Double.NaN} when this result has no single
     *         scalar cost, i.e. results produced by the unweighted
     *         {@link BFSRouter} and all {@link #notFound()} results.
     */
    public double getTotalCost() {
        return totalCost;
    }

    @Override
    public String toString() {
        return found
                ? "RouteResult{found=true, stationIds=" + stationIds
                + ", hopCount=" + hopCount + ", totalCost=" + totalCost + "}"
                : "RouteResult{found=false}";
    }
}