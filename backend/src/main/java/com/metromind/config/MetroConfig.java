package com.metromind.config;

import com.metromind.api.RouteService;
import com.metromind.data.MetroNetwork;
import com.metromind.data.MetroNetworkLoader;
import com.metromind.data.Station;
import com.metromind.graph.MetroGraph;
import com.metromind.graph.MetroGraphBuilder;
import com.metromind.routing.AStarRouter;
import com.metromind.routing.BFSRouter;
import com.metromind.routing.DijkstraRouter;
import com.metromind.routing.GeoPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the application's domain beans from the shared dataset.
 *
 * <p>The bean graph mirrors the existing libraries exactly — no second graph,
 * no duplicate dataset, no reimplemented algorithm:</p>
 *
 * <pre>
 * MetroNetwork (data/metro-network.json, via MetroNetworkLoader)
 *      ↓ MetroGraphBuilder
 * MetroGraph ──► BFSRouter, DijkstraRouter
 *      (＋ coordinates) ──► AStarRouter
 *      ──► RouteService
 * </pre>
 *
 * <p>The dataset itself is loaded once at startup and kept in memory; the same
 * instances serve every request. The file is packaged into the application JAR
 * (see the POM resource entry) so the equivalent of the loader's classpath
 * fallback always resolves it after deployment.</p>
 */
@Configuration
public class MetroConfig {

    /**
     * The validated network, loaded from the single source of truth.
     *
     * @throws IllegalStateException if the dataset cannot be located or parsed
     */
    @Bean
    public MetroNetwork metroNetwork() {
        try {
            return MetroNetworkLoader.loadDefault();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load metro-network.json", e);
        }
    }

    /** The weighted, read-only graph used by every router. */
    @Bean
    public MetroGraph metroGraph(MetroNetwork network) {
        return MetroGraphBuilder.from(network);
    }

    /** Station coordinates — required by the A* Haversine heuristic. */
    @Bean
    public Map<String, GeoPoint> stationCoordinates(MetroNetwork network) {
        Map<String, GeoPoint> coordinates = new HashMap<>();
        for (Station station : network.getStations()) {
            coordinates.put(station.getId(),
                    new GeoPoint(station.getLatitude(), station.getLongitude()));
        }
        return Map.copyOf(coordinates);
    }

    @Bean
    public BFSRouter bfsRouter(MetroGraph graph) {
        return new BFSRouter(graph);
    }

    @Bean
    public DijkstraRouter dijkstraRouter(MetroGraph graph) {
        return new DijkstraRouter(graph);
    }

    @Bean
    public AStarRouter aStarRouter(MetroGraph graph, Map<String, GeoPoint> coordinates) {
        return new AStarRouter(graph, coordinates);
    }

    @Bean
    public RouteService routeService(MetroGraph graph, BFSRouter bfsRouter,
                                     DijkstraRouter dijkstraRouter, AStarRouter aStarRouter) {
        return new RouteService(graph, bfsRouter, dijkstraRouter, aStarRouter);
    }
}