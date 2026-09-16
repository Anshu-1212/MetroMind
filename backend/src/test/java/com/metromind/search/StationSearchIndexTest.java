package com.metromind.search;

import com.metromind.data.MetroNetwork;
import com.metromind.data.MetroNetworkLoader;
import com.metromind.data.Station;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: builds a {@link StationSearchIndex} from the real Phase 2
 * Delhi Metro dataset ({@code data/metro-network.json}) and verifies that actual
 * station names can be searched and completed.
 */
class StationSearchIndexTest {

    @Test
    void indexesEveryStationNameFromTheDataset() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        StationSearchIndex index = new StationSearchIndex(network);

        // Exactly one trie entry per station: all names are unique and usable.
        assertEquals(network.getStations().size(), index.size());
        for (Station station : network.getStations()) {
            assertTrue(index.contains(station.getName()),
                    "Dataset station must be searchable: " + station.getName());
        }
    }

    @Test
    void realStationNamesAreSearchable() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        StationSearchIndex index = new StationSearchIndex(network);

        for (String name : List.of("Rajiv Chowk", "New Delhi", "Kashmere Gate",
                "Hauz Khas", "Dwarka Sector 21")) {
            assertTrue(index.contains(name), name + " must be in the index");
        }
    }

    @Test
    void searchIsCaseInsensitiveOnRealData() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        StationSearchIndex index = new StationSearchIndex(network);

        assertTrue(index.contains("RAJIV CHOWK"));
        assertTrue(index.contains("new delhi"));
        assertTrue(index.contains("  Hauz Khas  "));
        assertEquals(List.of("New Delhi"), index.search("NEW DELHI"));
    }

    @Test
    void prefixLookupAndAutocompleteOnRealData() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        StationSearchIndex index = new StationSearchIndex(network);

        assertTrue(index.startsWith("Kash"));
        assertTrue(index.startsWith("HAUZ"));
        assertEquals(List.of("Kashmere Gate"), index.search("Kashmere"));
        assertEquals(List.of("Hauz Khas"), index.search("Hauz"));
    }

    @Test
    void dwarkaAutocompleteIsDeterministicAndSorted() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        StationSearchIndex index = new StationSearchIndex(network);

        assertEquals(List.of(
                "Dwarka Mor",
                "Dwarka Sector 10",
                "Dwarka Sector 11",
                "Dwarka Sector 12",
                "Dwarka Sector 14",
                "Dwarka Sector 21",
                "Dwarka Sector 8",
                "Dwarka Sector 9"), index.search("Dwarka"));
    }

    @Test
    void suggestLimitsRealDatasetResults() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        StationSearchIndex index = new StationSearchIndex(network);

        // Several stations start with "D" (Dwarka..., Dilli Haat).
        assertTrue(index.search("D").size() > 3);
        assertEquals(3, index.suggest("D", 3).size());
        assertEquals(0, index.suggest("D", 0).size());
    }

    @Test
    void unknownAndBlankPrefixesReturnEmptyOnRealIndex() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        StationSearchIndex index = new StationSearchIndex(network);

        assertFalse(index.contains("Nirvana"));
        assertFalse(index.startsWith("Zzz"));
        assertTrue(index.search("zzz").isEmpty());
        assertTrue(index.search("  ").isEmpty());
        assertTrue(index.suggest("  ", 5).isEmpty());
    }

    @Test
    void indexReturnsNamesNotStationIds() throws Exception {
        MetroNetwork network = MetroNetworkLoader.loadDefault();
        StationSearchIndex index = new StationSearchIndex(network);

        // Search produces the human-readable name ("New Delhi"), never the
        // station ID ("NEW_DELHI") — routing maps IDs to the graph separately.
        assertEquals(List.of("New Delhi"), index.search("New Delhi"));
        assertFalse(index.contains("NEW_DELHI"));
    }

    @Test
    void nullNetworkIsRejected() {
        assertThrows(NullPointerException.class, () -> new StationSearchIndex(null));
    }
}