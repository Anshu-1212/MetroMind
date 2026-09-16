package com.metromind.routing;

import com.metromind.graph.GraphEdge;
import com.metromind.graph.MetroGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A* shortest-path router for {@link MetroGraph}.
 *
 * <p>Finds the minimum-<b>distance</b> route (sum of {@code GraphEdge.distanceKm})
 * between two stations, guided by a geographic heuristic. It is the weighted
 * successor to {@link DijkstraRouter}: both minimise the same distance, but A*
 * orders the priority queue by {@code f(n) = g(n) + h(n)} so nodes that are
 * geographically closer to the destination are examined first.</p>
 *
 * <p><b>Formula.</b> For each station {@code n} (the nodes of the search):</p>
 * <ul>
 *   <li>{@code g(n)} — the actual accumulated rail distance from the source to
 *       {@code n} ({@code sum(edge.distanceKm)} along the chosen path).</li>
 *   <li>{@code h(n)} — the heuristic: the straight-line {@link Haversine}
 *       distance from {@code n} to the destination, in kilometres.</li>
 *   <li>{@code f(n) = g(n) + h(n)} — the priority used by the queue; the node
 *       with the smallest {@code f} is expanded next.</li>
 * </ul>
 *
 * <p><b>Why Haversine is admissible.</b> Actual rail distance between two
 * stations is never shorter than the straight line between them, so
 * {@code h(n) ≤} remaining rail distance. A* therefore never overestimates the
 * remaining cost, which keeps its result optimal. This argument is about
 * <em>distance only</em>; it says nothing about travel time, so this router
 * deliberately optimises distance and offers no travel-time objective.</p>
 *
 * <p><b>Heuristic consistency.</b> Provided every edge's {@code distanceKm} is at
 * least the Haversine distance between its endpoints, Haversine, being a metric,
 * also satisfies the triangle inequality, so the heuristic is consistent and nodes
 * never need re-opening. The Phase 2 Delhi dataset stores distances rounded to
 * 0.1 km, so this holds to within that rounding (verified within a 0.05 km slack
 * by {@code RealDatasetAStarTest}); the result is still confirmed optimal against
 * {@link DijkstraRouter} on the real network. The implementation also guards
 * against stale queue entries defensively (exactly as {@link DijkstraRouter}
 * does), so it stays correct even if a better {@code g} is discovered late.</p>
 *
 * <p><b>Stale entries.</b> When a shorter path to a station is found, the station
 * is re-inserted with the improved {@code g} and the old entry is left in the
 * heap. When an outdated entry is polled, its recorded {@code g} no longer equals
 * the current best in the {@code g}-score map, so it is skipped. A station is
 * never permanently "closed" just because an outdated entry was popped.</p>
 *
 * <p><b>Distance objective only.</b> The API is deliberately
 * {@code route(source, destination)} with no {@link RouteMetric} parameter:
 * A* is a minimum-distance algorithm, so there is no way to accidentally ask it
 * to optimise travel time with a distance heuristic.</p>
 *
 * <p><b>Unknown stations / nulls.</b> Following BFS and Dijkstra: {@code null}
 * arguments are programming errors and raise {@link IllegalArgumentException};
 * an unknown station ID is treated as unreachable and returns
 * {@link RouteResult#notFound()}. The router needs a coordinate for every
 * station in the graph, so the constructor rejects a missing/duplicate-invalid
 * coordinate map outright rather than failing mid-search.</p>
 *
 * <h3>Time / space complexity</h3>
 * <ul>
 *   <li>Worst-case time: O((V + E) log V) with a binary-heap priority queue,
 *       the same worst case as Dijkstra. In practice the heuristic prunes the
 *       search, but a fixed speedup over Dijkstra must not be assumed —
 *       performance depends on graph structure, heuristic quality, and query.</li>
 *   <li>Space: O(V + E) — {@link MetroGraph} stores the adjacency lists; the
 *       g-score map, predecessor map, and priority queue each hold at most
 *       O(V) entries.</li>
 * </ul>
 */
public final class AStarRouter {

    /** A priority-queue entry: station, actual cost so far, and f = g + h. */
    private record QueueEntry(String stationId, double gScore, double fScore) {
    }

    private final MetroGraph graph;
    private final Map<String, GeoPoint> coordinates;

    /** Number of stations that were actually explored in the most recent route query. */
    private int lastExploredStationCount;

    /**
     * @param graph       the graph to search; must not be null
     * @param coordinates immutable station coordinates, one entry per station ID
     *                    that exists in {@code graph}; must not be null and must
     *                    cover every graph station with a non-null {@link GeoPoint}
     * @throws NullPointerException     if {@code graph} or {@code coordinates} is null
     * @throws IllegalArgumentException if any graph station lacks coordinates, a
     *                                  coordinate value is null, or a coordinate
     *                                  key is null
     */
    public AStarRouter(MetroGraph graph, Map<String, GeoPoint> coordinates) {
        if (graph == null) {
            throw new NullPointerException("graph must not be null");
        }
        if (coordinates == null) {
            throw new NullPointerException("coordinates must not be null");
        }

        // Defensive copy with explicit validation (Map.copyOf would throw an
        // opaque NPE on a bad value; we prefer clear messages).
        Map<String, GeoPoint> copy = new LinkedHashMap<>();
        for (Map.Entry<String, GeoPoint> entry : coordinates.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("Coordinate key must not be null");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Null coordinates for station: " + entry.getKey());
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        // The heuristic must be computable for every station in the graph.
        for (String stationId : graph.getStationIds()) {
            if (!copy.containsKey(stationId)) {
                throw new IllegalArgumentException("Missing coordinates for station: " + stationId);
            }
        }

        this.graph = graph;
        this.coordinates = Map.copyOf(copy);
    }

    /**
     * Finds the minimum-distance route from {@code source} to {@code destination}
     * using A* with the Haversine heuristic.
     *
     * @param source      the station ID to start from
     * @param destination the station ID to reach
     * @return a {@link RouteResult} whose {@link RouteResult#getTotalCost()} is
     *         the total distance in kilometres, or
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

        // Moving to the station you are already at is a zero-hop, zero-cost route.
        if (source.equals(destination)) {
            return RouteResult.found(List.of(source), 0.0);
        }

        lastExploredStationCount = 0;

        Map<String, Double> gScore = new HashMap<>();
        Map<String, String> predecessor = new HashMap<>();
        PriorityQueue<QueueEntry> open = new PriorityQueue<>(
                Comparator.comparingDouble(QueueEntry::fScore)
                        .thenComparingDouble(QueueEntry::gScore));

        gScore.put(source, 0.0);
        open.add(new QueueEntry(source, 0.0, heuristic(source, destination)));

        while (!open.isEmpty()) {
            QueueEntry entry = open.poll();
            String current = entry.stationId();

            // Stale entry: a later, shorter path to this station is already known.
            if (entry.gScore() != gScore.get(current)) {
                continue;
            }
            lastExploredStationCount++;

            // With an admissible heuristic, the first (smallest-f) pop of the
            // destination carries its optimal g.
            if (current.equals(destination)) {
                return reconstruct(source, destination, predecessor, gScore.get(destination));
            }

            for (GraphEdge edge : graph.getNeighbors(current)) {
                String neighbor = edge.getDestination();
                double tentativeG = entry.gScore() + edge.getDistanceKm();

                if (tentativeG < gScore.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    gScore.put(neighbor, tentativeG);
                    predecessor.put(neighbor, current);
                    open.add(new QueueEntry(neighbor, tentativeG,
                            tentativeG + heuristic(neighbor, destination)));
                }
            }
        }

        // Not found when the destination was never reached (disconnected).
        return RouteResult.notFound();
    }

    /**
     * The A* heuristic, {@code h(n) = haversine(n, destination)} in kilometres.
     *
     * <p>Exposed for the heuristic unit tests and for callers that want to
     * inspect the estimate directly.</p>
     *
     * @param stationId     the current station ID
     * @param destinationId the destination station ID
     * @return the straight-line distance in kilometres
     * @throws IllegalArgumentException if either station is unknown to this router
     */
    public double heuristic(String stationId, String destinationId) {
        GeoPoint from = coordinates.get(stationId);
        GeoPoint to = coordinates.get(destinationId);
        if (from == null) {
            throw new IllegalArgumentException("No coordinates for station: " + stationId);
        }
        if (to == null) {
            throw new IllegalArgumentException("No coordinates for station: " + destinationId);
        }
        return Haversine.distanceKm(from, to);
    }

    /**
     * Reconstructs the route by walking the predecessor map backwards from the
     * destination, then reversing, exactly like BFS and Dijkstra.
     *
     * @param source       the route source
     * @param destination  the route destination
     * @param predecessor  maps each reached station to the station before it
     * @param totalCost    the minimal summed distance under the selected metric
     * @return the ordered route with its total distance attached
     */
    private static RouteResult reconstruct(String source, String destination,
                                            Map<String, String> predecessor, double totalCost) {
        List<String> path = new ArrayList<>();
        String node = destination;

        while (node != null) {
            path.add(node);
            node = predecessor.get(node);
        }

        Collections.reverse(path);
        return RouteResult.found(path, totalCost);
    }

    /**
     * Number of stations explored (finalised, non-stale pops) during the most
     * recent {@link #route(String, String)} call. Useful for demonstrating how
     * the heuristic prunes the search.
     *
     * @return the explored-station count of the last query
     */
    int getLastExploredStationCount() {
        return lastExploredStationCount;
    }
}