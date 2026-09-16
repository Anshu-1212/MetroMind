package com.metromind.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Represents a metro station in the network.
 *
 * <p>Each station has a unique ID, a human-readable name, geographic coordinates,
 * the metro lines it belongs to, and an interchange flag indicating whether
 * the station serves more than one line.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Station {

    private String id;
    private String name;
    private double latitude;
    private double longitude;
    private List<String> lines;
    private boolean interchange;

    public Station() {
    }

    public Station(String id, String name, double latitude, double longitude,
                   List<String> lines, boolean interchange) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.lines = lines;
        this.interchange = interchange;
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

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public List<String> getLines() {
        return lines;
    }

    public void setLines(List<String> lines) {
        this.lines = lines;
    }

    public boolean isInterchange() {
        return interchange;
    }

    public void setInterchange(boolean interchange) {
        this.interchange = interchange;
    }

    @Override
    public String toString() {
        return "Station{id='" + id + "', name='" + name + "', lines=" + lines
                + ", interchange=" + interchange + "}";
    }
}
