package com.metromind.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the Delhi Metro starter dataset for structural and referential integrity.
 *
 * <p>Checks that every station, line, and connection in {@code metro-network.json}
 * is internally consistent — no dangling references, valid coordinates,
 * positive distances, and correct interchange flags.</p>
 */
class MetroNetworkValidationTest {

    private static MetroNetwork network;

    @BeforeAll
    static void loadDataset() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        File file = new File("../../../data/metro-network.json");
        if (!file.exists()) {
            // Try from backend/ working directory (mvn test)
            file = new File("../data/metro-network.json");
        }
        if (!file.exists()) {
            // Try absolute-ish path relative to project root
            String projectRoot = System.getProperty("user.dir");
            file = new File(projectRoot, "data/metro-network.json");
        }
        network = mapper.readValue(file, MetroNetwork.class);
    }

    // ──────────────────────────────────────────────
    // Station validation
    // ──────────────────────────────────────────────

    @Test
    void stationsExist() {
        assertNotNull(network.getStations(), "Stations list must not be null");
        assertFalse(network.getStations().isEmpty(), "Dataset must contain at least one station");
    }

    @Test
    void everyStationHasUniqueId() {
        List<String> ids = network.getStations().stream()
                .map(Station::getId)
                .toList();
        Set<String> uniqueIds = new HashSet<>(ids);
        assertEquals(ids.size(), uniqueIds.size(),
                "Duplicate station IDs found: " + ids.stream()
                        .filter(id -> Collections.frequency(ids, id) > 1)
                        .distinct().toList());
    }

    @Test
    void everyStationHasName() {
        for (Station s : network.getStations()) {
            assertNotNull(s.getId(), "Station ID must not be null");
            assertFalse(s.getId().isBlank(), "Station ID must not be blank: " + s);
            assertNotNull(s.getName(), "Station name must not be null for " + s.getId());
            assertFalse(s.getName().isBlank(), "Station name must not be blank for " + s.getId());
        }
    }

    @Test
    void latitudeIsValid() {
        for (Station s : network.getStations()) {
            assertTrue(s.getLatitude() >= -90 && s.getLatitude() <= 90,
                    "Invalid latitude " + s.getLatitude() + " for station " + s.getId());
        }
    }

    @Test
    void longitudeIsValid() {
        for (Station s : network.getStations()) {
            assertTrue(s.getLongitude() >= -180 && s.getLongitude() <= 180,
                    "Invalid longitude " + s.getLongitude() + " for station " + s.getId());
        }
    }

    @Test
    void latitudeIsWithinDelhiRange() {
        for (Station s : network.getStations()) {
            assertTrue(s.getLatitude() >= 28.4 && s.getLatitude() <= 28.9,
                    "Latitude " + s.getLatitude() + " for " + s.getId()
                            + " is outside the Delhi region (expected 28.4–28.9)");
        }
    }

    @Test
    void longitudeIsWithinDelhiRange() {
        for (Station s : network.getStations()) {
            assertTrue(s.getLongitude() >= 76.8 && s.getLongitude() <= 77.5,
                    "Longitude " + s.getLongitude() + " for " + s.getId()
                            + " is outside the Delhi region (expected 76.8–77.5)");
        }
    }

    // ──────────────────────────────────────────────
    // Line validation
    // ──────────────────────────────────────────────

    @Test
    void linesExist() {
        assertNotNull(network.getLines(), "Lines list must not be null");
        assertFalse(network.getLines().isEmpty(), "Dataset must contain at least one line");
    }

    @Test
    void everyLineHasUniqueId() {
        List<String> ids = network.getLines().stream()
                .map(MetroLine::getId)
                .toList();
        assertEquals(ids.size(), new HashSet<>(ids).size(),
                "Duplicate line IDs found");
    }

    @Test
    void everyLineReferencesExistingStations() {
        Set<String> stationIds = network.getStations().stream()
                .map(Station::getId)
                .collect(Collectors.toSet());

        for (MetroLine line : network.getLines()) {
            for (String stationId : line.getStations()) {
                assertTrue(stationIds.contains(stationId),
                        "Line " + line.getId() + " references unknown station: " + stationId);
            }
        }
    }

    @Test
    void everyLineHasAtLeastTwoStations() {
        for (MetroLine line : network.getLines()) {
            assertTrue(line.getStations().size() >= 2,
                    "Line " + line.getId() + " must have at least 2 stations, found "
                            + line.getStations().size());
        }
    }

    @Test
    void lineStationOrderIsValid() {
        for (MetroLine line : network.getLines()) {
            List<String> stations = line.getStations();
            // Check no duplicate station in the same line
            Set<String> unique = new HashSet<>(stations);
            assertEquals(stations.size(), unique.size(),
                    "Line " + line.getId() + " contains duplicate station references");
        }
    }

    @Test
    void everyLineHasNameAndColor() {
        for (MetroLine line : network.getLines()) {
            assertNotNull(line.getName(), "Line " + line.getId() + " must have a name");
            assertFalse(line.getName().isBlank(),
                    "Line " + line.getId() + " must have a non-blank name");
            assertNotNull(line.getColor(), "Line " + line.getId() + " must have a color");
        }
    }

    // ──────────────────────────────────────────────
    // Connection validation
    // ──────────────────────────────────────────────

    @Test
    void connectionsExist() {
        assertNotNull(network.getConnections(), "Connections list must not be null");
        assertFalse(network.getConnections().isEmpty(), "Dataset must contain connections");
    }

    @Test
    void everyConnectionReferencesExistingStations() {
        Set<String> stationIds = network.getStations().stream()
                .map(Station::getId)
                .collect(Collectors.toSet());

        for (Connection c : network.getConnections()) {
            assertTrue(stationIds.contains(c.getFrom()),
                    "Connection references unknown source station: " + c.getFrom());
            assertTrue(stationIds.contains(c.getTo()),
                    "Connection references unknown destination station: " + c.getTo());
        }
    }

    @Test
    void everyConnectionReferencesExistingLine() {
        Set<String> lineIds = network.getLines().stream()
                .map(MetroLine::getId)
                .collect(Collectors.toSet());

        for (Connection c : network.getConnections()) {
            assertTrue(lineIds.contains(c.getLine()),
                    "Connection references unknown line: " + c.getLine());
        }
    }

    @Test
    void distanceIsPositive() {
        for (Connection c : network.getConnections()) {
            assertTrue(c.getDistanceKm() > 0,
                    "Distance must be positive for connection "
                            + c.getFrom() + " -> " + c.getTo()
                            + " (got " + c.getDistanceKm() + ")");
        }
    }

    @Test
    void travelTimeIsPositive() {
        for (Connection c : network.getConnections()) {
            assertTrue(c.getTravelTimeMinutes() > 0,
                    "Travel time must be positive for connection "
                            + c.getFrom() + " -> " + c.getTo()
                            + " (got " + c.getTravelTimeMinutes() + ")");
        }
    }

    @Test
    void connectionIsBetweenConsecutiveStationsOnLine() {
        for (Connection c : network.getConnections()) {
            MetroLine line = network.getLines().stream()
                    .filter(l -> l.getId().equals(c.getLine()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(line, "Connection's line must exist: " + c.getLine());

            List<String> stations = line.getStations();
            int fromIdx = stations.indexOf(c.getFrom());
            int toIdx = stations.indexOf(c.getTo());

            assertTrue(fromIdx >= 0,
                    "Source station " + c.getFrom() + " not found in line " + line.getId());
            assertTrue(toIdx >= 0,
                    "Destination station " + c.getTo() + " not found in line " + line.getId());

            assertEquals(1, Math.abs(fromIdx - toIdx),
                    "Connection " + c.getFrom() + " -> " + c.getTo()
                            + " must be between consecutive stations on line " + line.getId()
                            + " (indices " + fromIdx + " and " + toIdx + ")");
        }
    }

    // ──────────────────────────────────────────────
    // Interchange consistency
    // ──────────────────────────────────────────────

    @Test
    void interchangeFlagIsConsistentWithLineMembership() {
        for (Station s : network.getStations()) {
            boolean expectedInterchange = s.getLines().size() > 1;
            assertEquals(expectedInterchange, s.isInterchange(),
                    "Interchange flag for " + s.getId()
                            + " is inconsistent: lines=" + s.getLines()
                            + ", interchange=" + s.isInterchange());
        }
    }

    @Test
    void stationLinesExistInLinesList() {
        Set<String> lineIds = network.getLines().stream()
                .map(MetroLine::getId)
                .collect(Collectors.toSet());

        for (Station s : network.getStations()) {
            for (String lineId : s.getLines()) {
                assertTrue(lineIds.contains(lineId),
                        "Station " + s.getId() + " references unknown line: " + lineId);
            }
        }
    }

    // ──────────────────────────────────────────────
    // Cross-consistency: connections match line sequences
    // ──────────────────────────────────────────────

    @Test
    void connectionPairsExistInLineSequence() {
        for (Connection c : network.getConnections()) {
            MetroLine line = network.getLines().stream()
                    .filter(l -> l.getId().equals(c.getLine()))
                    .findFirst()
                    .orElseThrow();

            List<String> stations = line.getStations();
            int fromIdx = stations.indexOf(c.getFrom());
            int toIdx = stations.indexOf(c.getTo());

            // The connection should be between indices i and i+1 or i-1
            assertTrue(Math.abs(fromIdx - toIdx) == 1,
                    "Connection " + c.getFrom() + " -> " + c.getTo()
                            + " is not between adjacent stations on line " + c.getLine());
        }
    }

    @Test
    void networkMetadataIsConsistent() {
        assertNotNull(network.getNetwork(), "Network info must not be null");
        assertEquals(network.getStations().size(), network.getNetwork().getTotalStations(),
                "Total stations in metadata does not match station list size");
        assertEquals(network.getLines().size(), network.getNetwork().getTotalLines(),
                "Total lines in metadata does not match line list size");
        assertEquals(network.getConnections().size(), network.getNetwork().getTotalConnections(),
                "Total connections in metadata does not match connection list size");
    }

    // ──────────────────────────────────────────────
    // Valid complete dataset
    // ──────────────────────────────────────────────

    @Test
    void datasetIsValidAndComplete() {
        // Summary assertions to confirm the dataset is in a good state
        assertTrue(network.getStations().size() >= 10,
                "Dataset should have at least 10 stations");
        assertTrue(network.getLines().size() >= 2,
                "Dataset should have at least 2 lines");
        assertTrue(network.getConnections().size() >= 10,
                "Dataset should have at least 10 connections");

        long interchangeCount = network.getStations().stream()
                .filter(Station::isInterchange)
                .count();
        assertTrue(interchangeCount >= 1,
                "Dataset should have at least 1 interchange station");
    }
}
