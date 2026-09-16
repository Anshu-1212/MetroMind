package com.metromind.routing;

import com.metromind.graph.GraphEdge;
import com.metromind.graph.MetroGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Breadth-First Search router for {@link MetroGraph}.
 *
 * <p>Finds a route between two stations using the minimum number of
 * station-to-station hops (edges) in an unweighted graph.</p>
 *
 * <p><b>Weights are ignored.</b> BFS never reads {@code distanceKm},
 * {@code travelTimeMinutes}, or {@code line}. Each station-to-station transition
 * counts as exactly one hop, which is what BFS minimises — not physical distance
 * or travel time. For shortest-distance or fastest routing, a weighted algorithm
 * (future phase) is required.</p>
 *
 * <p><b>Parallel edges.</b> When two stations are connected by more than one
 * line edge, moving between them is still one station-level hop. The visited
 * structure is keyed by station ID, so a station is discovered only once and
 * parallel line edges never cause multiple visits.</p>
 *
 * <p><b>Unknown stations.</b> Passing a {@code null} source or destination is a
 * programming error and raises {@link IllegalArgumentException}. A non-null
 * station ID that does not exist in the graph is treated as unreachable and
 * returns {@link RouteResult#notFound()}, matching the behaviour for stations
 * in disconnected components.</p>
 *
 * <p><b>Source equals destination.</b> Returns a found route containing exactly
 * one station with zero hops.</p>
 *
 * <h3>Time / space complexity</h3>
 * <ul>
 *   <li>Time: O(V + E) — every vertex is enqueued at most once and every edge
 *       is scanned when its vertex is dequeued.</li>
 *   <li>Space: O(V) — the queue, visited set, and predecessor map each hold at
 *       most one entry per vertex.</li>
 * </ul>
 */
public final class BFSRouter {

    private final MetroGraph graph;

    /**
     * @param graph the graph to search; must not be null
     * @throws NullPointerException if {@code graph} is null
     */
    public BFSRouter(MetroGraph graph) {
        if (graph == null) {
            throw new NullPointerException("graph must not be null");
        }
        this.graph = graph;
    }

    /**
     * Finds a minimum-hop route from {@code source} to {@code destination}.
     *
     * @param source      the station ID to start from
     * @param destination the station ID to reach
     * @return a {@link RouteResult} with the ordered route, or
     *         {@link RouteResult#notFound()} when the destination is unreachable
     *         or either station does not exist
     * @throws IllegalArgumentException if {@code source} or {@code destination}
     *                                  is {@code null}
     */
    public RouteResult route(String source, String destination) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }

        // Unknown stations are treated like any unreachable station.
        if (!graph.containsStation(source) || !graph.containsStation(destination)) {
            return RouteResult.notFound();
        }

        // Moving to the station you are already at is a zero-hop route.
        if (source.equals(destination)) {
            return RouteResult.found(List.of(source));
        }

        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        Map<String, String> predecessor = new HashMap<>();

        queue.add(source);
        visited.add(source);

        while (!queue.isEmpty()) {
            String current = queue.remove();

            if (current.equals(destination)) {
                return reconstruct(source, destination, predecessor);
            }

            for (GraphEdge edge : graph.getNeighbors(current)) {
                String next = edge.getDestination();
                if (!visited.contains(next)) {
                    // Mark visited on discovery, not removal, so a node is
                    // never enqueued twice.
                    visited.add(next);
                    predecessor.put(next, current);
                    queue.add(next);
                }
            }
        }

        return RouteResult.notFound();
    }

    /**
     * Reconstructs the route by walking the predecessor map backwards.
     *
     * @param source      the route source
     * @param destination the route destination
     * @param predecessor maps each reached station to the station before it
     * @return the ordered route from source to destination
     */
    private RouteResult reconstruct(String source, String destination,
                                    Map<String, String> predecessor) {
        List<String> path = new ArrayList<>();
        String node = destination;

        while (node != null) {
            path.add(node);
            node = predecessor.get(node);
        }

        // The source station has no predecessor entry, so the walk stops there;
        // the list is built destination-first and reversed to source-first.
        Collections.reverse(path);
        return RouteResult.found(path);
    }
}