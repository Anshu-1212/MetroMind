package com.metromind.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Top-level model representing the entire metro network JSON file.
 *
 * <p>Contains the network metadata, all stations, all lines, and all connections.
 * This is the root object deserialised from {@code metro-network.json}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetroNetwork {

    private NetworkInfo network;
    private List<Station> stations;
    private List<MetroLine> lines;
    private List<Connection> connections;

    public MetroNetwork() {
    }

    public NetworkInfo getNetwork() {
        return network;
    }

    public void setNetwork(NetworkInfo network) {
        this.network = network;
    }

    public List<Station> getStations() {
        return stations;
    }

    public void setStations(List<Station> stations) {
        this.stations = stations;
    }

    public List<MetroLine> getLines() {
        return lines;
    }

    public void setLines(List<MetroLine> lines) {
        this.lines = lines;
    }

    public List<Connection> getConnections() {
        return connections;
    }

    public void setConnections(List<Connection> connections) {
        this.connections = connections;
    }

    /**
     * Metadata about the network as a whole.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NetworkInfo {
        private String name;
        private String version;
        private String description;
        private int totalStations;
        private int totalLines;
        private int totalConnections;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getTotalStations() {
            return totalStations;
        }

        public void setTotalStations(int totalStations) {
            this.totalStations = totalStations;
        }

        public int getTotalLines() {
            return totalLines;
        }

        public void setTotalLines(int totalLines) {
            this.totalLines = totalLines;
        }

        public int getTotalConnections() {
            return totalConnections;
        }

        public void setTotalConnections(int totalConnections) {
            this.totalConnections = totalConnections;
        }
    }
}
