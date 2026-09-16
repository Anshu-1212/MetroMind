package com.metromind.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a bidirectional connection between two adjacent stations on a single metro line.
 *
 * <p>Each connection stores the source and destination station IDs, the line it belongs to,
 * the distance in kilometres, and the estimated travel time in minutes.</p>
 *
 * <p>Metro movement is treated as bidirectional — the reverse direction
 * uses the same distance and travel time.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Connection {

    private String from;
    private String to;
    private String line;
    private double distanceKm;
    private int travelTimeMinutes;
    private String note;

    public Connection() {
    }

    public Connection(String from, String to, String line,
                      double distanceKm, int travelTimeMinutes) {
        this.from = from;
        this.to = to;
        this.line = line;
        this.distanceKm = distanceKm;
        this.travelTimeMinutes = travelTimeMinutes;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public int getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public void setTravelTimeMinutes(int travelTimeMinutes) {
        this.travelTimeMinutes = travelTimeMinutes;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public String toString() {
        return "Connection{from='" + from + "', to='" + to + "', line='" + line
                + "', distanceKm=" + distanceKm + ", travelTimeMinutes=" + travelTimeMinutes + "}";
    }
}
