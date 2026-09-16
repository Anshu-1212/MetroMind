package com.metromind.routing;

import com.metromind.graph.GraphEdge;
import com.metromind.graph.MetroGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Dijkstra's shortest-path router for {@link MetroGraph}.
 *
 * <p>Finds the route minimising the <b>sum of selected non-negative edge
 * weights</b>, where the weight is chosen explicitly by the caller via
 * {@link RouteMetric}: {@link RouteMetric#DISTANCE} sums
 * {@code GraphEdge.distanceKm}, {@link RouteMetric#TRAVEL_TIME} sums
 * {@code GraphEdge.travelTimeMinutes}. The two objectives are never combined.
 * Unlike {@link BFSRouter}, this minimises a weighted cost, not the number of
 * hops.</p>
 *
 * <p><b>Non-negative weights.</b> Dijkstra is correct only for non-negative edge
 * weights. {@link GraphEdge}'s constructor already rejects non-positive
 * distance and travel time, so every graph built by
 * {@link com.metromind.graph.MetroGraphBuilder} satisfies this assumption by
 * construction. This class relies on that existing validation.</p>
 *
 * <p><b>Stale priority-queue entries.</b> When a shorter distance to a station
 * is discovered, the station is re-inserted into the queue and the old (larger)
 * entry is left behind. When such an outdated entry is {@link PriorityQueue#poll()}
 * -ed, its recorded distance no longer equals the current best distance in the
 * {@code distances} map, so it is skipped. A station is therefore never marked
 * "visited" by a stale entry, guaranteeing the first valid poll of a station is
 * its true shortest distance.</p>
 *
 * <p><b>Parallel edges.</b> The graph may hold several {@link GraphEdge}s
 * between the same two stations on different lines with different weights.
 * Dijkstra relaxes every edge object individually and keeps the cheaper one per
 * metric, so legitimate parallel edges are never collapsed.</p>
 *
 * <p><b>Unknown stations.</b> A {@code null} source, destination, or metric is a
 * programming error and raises {@link IllegalArgumentException}. A non-null
 * station ID that does not exist is treated as unreachable and returns
 * {@link RouteResult#notFound()}, matching {@link BFSRouter}.</p>
 *
 * <p><b>Source equals destination.</b> Returns a found route of one station with
 * zero hops and a total cost of {@code 0} for either metric.</p>
 *
 * <h3>Time / space complexity</h3>
 * <ul>
 *   <li>Time: O((V + E) log V) for the standard binary-heap / Java
 *       {@link PriorityQueue} implementation. Every vertex can be added several
 *       times but each poll is O(log V), and every edge is relaxed when its
 *       source vertex is finalised.</li>
 *   <li>Space: O(V + E). {@link MetroGraph} itself stores the adjacency lists
 *       (O(V + E)); the distance map, predecessor map, and priority queue each
 *       hold at most O(V) entries.</li>
 * </ul>
 */
public final class DijkstraRouter {

    /** A priority-queue entry: a station and the tentative distance it was queued with. */
    private record QueueEntry(String stationId, double distance) {
    }

    private final MetroGraph graph;

    /**
     * @param graph the graph to search; must not be null
     * @throws NullPointerException if {@code graph} is null
     */
    public DijkstraRouter(MetroGraph graph) {
        if (graph == null) {
            throw new NullPointerException("graph must not be null");
        }
        this.graph = graph;
    }

    /**
     * Finds the shortest route from {@code source} to {@code destination} under
     * the given {@link RouteMetric}.
     *
     * <p>Example usage:</p>
     * <pre>
     * router.route("DWARKA_SECTOR_21", "HAUZ_KHAS", RouteMetric.DISTANCE);     // fewest kilometres
     * router.route("DWARKA_SECTOR_21", "HAUZ_KHAS", RouteMetric.TRAVEL_TIME);  // fewest minutes
     * </pre>
     *
     * @param source      the station ID to start from
     * @param destination the station ID to reach
     * @param metric      the routing objective; must not be null
     * @return a {@link RouteResult} whose {@link RouteResult#getTotalCost()} is
     *         expressed in the units of {@code metric} (kilometres for
     *         {@link RouteMetric#DISTANCE}, minutes for
     *         {@link RouteMetric#TRAVEL_TIME}), or
     *         {@link RouteResult#notFound()} when the destination is unreachable
     *         or either station does not exist
     * @throws IllegalArgumentException if {@code source}, {@code destination}, or
     *                                  {@code metric} is {@code null}
     */
    public RouteResult route(String source, String destination, RouteMetric metric) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        if (metric == null) {
            throw new IllegalArgumentException("metric must not be null");
        }

        // Unknown stations are treated like any unreachable station.
        if (!graph.containsStation(source) || !graph.containsStation(destination)) {
            return RouteResult.notFound();
        }

        // Moving from a station to itself is a zero-hop, zero-cost route.
        if (source.equals(destination)) {
            return RouteResult.found(List.of(source), 0.0);
        }

        Map<String, Double> distances = new HashMap<>();
        Map<String, String> predecessors = new HashMap<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(
                Comparator.comparingDouble(QueueEntry::distance));

        // Start: distance[source] = 0, everything else implicitly at infinity.
        distances.put(source, 0.0);
        queue.add(new QueueEntry(source, 0.0));

        while (!queue.isEmpty()) {
            QueueEntry entry = queue.poll();
            String current = entry.stationId();
            double currentDistance = entry.distance();

            // Stale entry: an earlier, longer tentative distance for this station.
            // The current best lives in `distances`; anything else is outdated.
            if (currentDistance != distances.get(current)) {
                continue;
            }

            // First (cheapest) poll of the destination is its true shortest cost.
            if (current.equals(destination)) {
                break;
            }

            for (GraphEdge edge : graph.getNeighbors(current)) {
                String neighbor = edge.getDestination();
                double weight = selectedWeight(edge, metric);
                double newDistance = currentDistance + weight;

                // Relaxation: keep only a strictly better distance.
                if (newDistance < distances.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    distances.put(neighbor, newDistance);
                    predecessors.put(neighbor, current);
                    queue.add(new QueueEntry(neighbor, newDistance));
                }
            }
        }

        // Not found when the destination was never reached (disconnected).
        if (!distances.containsKey(destination)) {
            return RouteResult.notFound();
        }

        return reconstruct(source, destination, predecessors, distances.get(destination));
    }

    /**
     * Returns the weight of the given edge under the selected routing objective.
     *
     * @param edge   the edge to weigh
     * @param metric the routing objective
     * @return the edge distance in kilometres, or the edge travel time in minutes
     */
    private static double selectedWeight(GraphEdge edge, RouteMetric metric) {
        return switch (metric) {
            case DISTANCE -> edge.getDistanceKm();
            case TRAVEL_TIME -> edge.getTravelTimeMinutes();
        };
    }

    /**
     * Reconstructs the route by walking the predecessor map backwards, then
     * reversing, exactly like BFS.
     *
     * <p>The source station has no predecessor entry, so the walk stops there.
     * The result is the ordered station list source → destination.</p>
     *
     * @param source      the route source
     * @param destination the route destination
     * @param predecessors maps each reached station to the station before it
     * @param totalCost   the minimal summed cost under the selected metric
     * @return the ordered route with its total cost attached
     */
    private static RouteResult reconstruct(String source, String destination,
                                            Map<String, String> predecessors, double totalCost) {
        List<String> path = new ArrayList<>();
        String node = destination;

        while (node != null) {
            path.add(node);
            node = predecessors.get(node);
        }

        Collections.reverse(path);
        return RouteResult.found(path, totalCost);
    }
}