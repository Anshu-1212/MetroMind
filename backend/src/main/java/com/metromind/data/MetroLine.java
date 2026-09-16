package com.metromind.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Represents a single metro line in the network.
 *
 * <p>Each line has a unique ID, a human-readable name, an optional color,
 * and an ordered list of station IDs that define the line's route.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetroLine {

    private String id;
    private String name;
    private String color;
    private List<String> stations;

    public MetroLine() {
    }

    public MetroLine(String id, String name, String color, List<String> stations) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.stations = stations;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public List<String> getStations() {
        return stations;
    }

    public void setStations(List<String> stations) {
        this.stations = stations;
    }

    @Override
    public String toString() {
        return "MetroLine{id='" + id + "', name='" + name
                + "', stations=" + stations.size() + "}";
    }
}
