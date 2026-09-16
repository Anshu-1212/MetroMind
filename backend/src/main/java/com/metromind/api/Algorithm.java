package com.metromind.api;

/**
 * The routing algorithms exposed by the REST API.
 *
 * <p>Each value selects an existing backend router — these names are the API's
 * public contract and must not diverge from the algorithms in
 * {@code com.metromind.routing}. The API never reimplements an algorithm; the
 * service underneath simply dispatches to the matching router.</p>
 *
 * <p>JSON accepts these exact, case-sensitive names: {@code BFS},
 * {@code DIJKSTRA}, {@code ASTAR}. An unknown value is rejected with
 * {@code 400 UNSUPPORTED_ALGORITHM}.</p>
 */
public enum Algorithm {

    /** Minimum station hops — {@link com.metromind.routing.BFSRouter}. */
    BFS,

    /** Minimum weighted cost — {@link com.metromind.routing.DijkstraRouter},
     *  objective chosen via {@link com.metromind.routing.RouteMetric}. */
    DIJKSTRA,

    /** Minimum rail distance with a Haversine heuristic —
     *  {@link com.metromind.routing.AStarRouter}. */
    ASTAR
}