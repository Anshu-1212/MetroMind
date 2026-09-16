package com.metromind.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StationTrie} covering insertion, exact search,
 * prefix existence, autocomplete, deduplication, limits, determinism, and
 * defensive null/blank handling — without touching the real dataset.
 */
class StationTrieTest {

    @Test
    void emptyTrieHasNoNames() {
        StationTrie trie = new StationTrie();
        assertTrue(trie.isEmpty());
        assertEquals(0, trie.size());
        assertFalse(trie.contains("Rajiv Chowk"));
        assertFalse(trie.startsWith("Raj"));
        assertTrue(trie.search("Raj").isEmpty());
        assertTrue(trie.suggest("Raj", 5).isEmpty());
    }

    @Test
    void insertedStationIsContained() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        assertFalse(trie.isEmpty());
        assertEquals(1, trie.size());
        assertTrue(trie.contains("Rajiv Chowk"));
    }

    @Test
    void exactSearchMatchesInsertedName() {
        StationTrie trie = new StationTrie();
        trie.insert("New Delhi");
        assertEquals(List.of("New Delhi"), trie.search("New Delhi"));
    }

    @Test
    void exactSearchIsCaseInsensitive() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        assertTrue(trie.contains("rajiv chowk"));
        assertTrue(trie.contains("RAJIV CHOWK"));
        assertTrue(trie.contains("rAjIv ChOwK"));
    }

    @Test
    void leadingAndTrailingWhitespaceIsIgnored() {
        StationTrie trie = new StationTrie();
        trie.insert("Hauz Khas");
        assertTrue(trie.contains("  Hauz Khas  "));
        assertEquals(List.of("Hauz Khas"), trie.search("  hauz  "));
        assertTrue(trie.startsWith("  Hauz  "));
    }

    @Test
    void containsReturnsFalseForMissingStation() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        assertFalse(trie.contains("Central Secretariat"));
        assertFalse(trie.contains("Rajiv"));
    }

    @Test
    void containsBlankNameReturnsFalse() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        assertFalse(trie.contains(""));
        assertFalse(trie.contains("   "));
    }

    @Test
    void prefixExists() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        assertTrue(trie.startsWith("Raj"));
        assertTrue(trie.startsWith("Rajiv Chowk"));
    }

    @Test
    void prefixExistsCaseInsensitively() {
        StationTrie trie = new StationTrie();
        trie.insert("Kashmere Gate");
        assertTrue(trie.startsWith("kash"));
        assertTrue(trie.startsWith("KASHMERE"));
    }

    @Test
    void unknownPrefixDoesNotExist() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        assertFalse(trie.startsWith("Xyz"));
        assertFalse(trie.startsWith("Rajiv Chowk Gate"));
    }

    @Test
    void blankPrefixDoesNotExist() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        assertFalse(trie.startsWith(""));
        assertFalse(trie.startsWith("   "));
    }

    @Test
    void searchFindsAllNamesSharingPrefix() {
        StationTrie trie = new StationTrie();
        trie.insert("app");
        trie.insert("apple");
        trie.insert("apricot");
        trie.insert("banana");
        assertEquals(List.of("app", "apple", "apricot"), trie.search("ap"));
    }

    @Test
    void searchIsCaseInsensitiveAndReturnsSameResultAsCasedQuery() {
        StationTrie trie = new StationTrie();
        trie.insert("Central Secretariat");
        trie.insert("Chandni Chowk");
        trie.insert("Chawri Bazar");
        assertEquals(trie.search("C"), trie.search("c"));
        assertEquals(3, trie.search("C").size());
    }

    @Test
    void searchResultsAreDeterministicRegardlessOfInsertionOrder() {
        String[] names = {"Dwarka Mor", "Dwarka Sector 8", "Dwarka Sector 21"};
        // Insert in three different orders; all must yield the same sorted result.
        assertEquals(
                List.of("Dwarka Mor", "Dwarka Sector 21", "Dwarka Sector 8"),
                trieWithOrder(names[0], names[1], names[2]).search("Dwarka"));
        assertEquals(
                List.of("Dwarka Mor", "Dwarka Sector 21", "Dwarka Sector 8"),
                trieWithOrder(names[2], names[0], names[1]).search("Dwarka"));
        assertEquals(
                List.of("Dwarka Mor", "Dwarka Sector 21", "Dwarka Sector 8"),
                trieWithOrder(names[1], names[2], names[0]).search("Dwarka"));
    }

    @Test
    void searchEmptyOrBlankPrefixReturnsEmpty() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        assertTrue(trie.search("").isEmpty());
        assertTrue(trie.search("   ").isEmpty());
    }

    @Test
    void searchUnknownPrefixReturnsEmpty() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        assertTrue(trie.search("zzz").isEmpty());
        assertTrue(trie.search("Rajiv Chowk Gate").isEmpty());
    }

    @Test
    void searchReturnsOriginalInsertedSpelling() {
        StationTrie trie = new StationTrie();
        trie.insert("Dwarka Sector 21");
        assertEquals(List.of("Dwarka Sector 21"), trie.search("dwarka"));
        assertEquals(List.of("Dwarka Sector 21"), trie.search("DWARKA SECTOR 21"));
    }

    @Test
    void searchHandlesNamesWithInternalSpaces() {
        StationTrie trie = new StationTrie();
        trie.insert("New Delhi");
        trie.insert("New Delhi Railway Station");
        assertEquals(List.of("New Delhi", "New Delhi Railway Station"), trie.search("New Delhi"));
    }

    @Test
    void searchHandlesNamesWithPunctuation() {
        StationTrie trie = new StationTrie();
        trie.insert("Dilli Haat - INA");
        trie.insert("Rohini Sector 18, 19");
        assertEquals(List.of("Dilli Haat - INA"), trie.search("dilli haat"));
        assertEquals(List.of("Rohini Sector 18, 19"), trie.search("rohini sector 18"));
    }

    @Test
    void suggestCapsResultCount() {
        StationTrie trie = new StationTrie();
        trie.insert("Dwarka Mor");
        trie.insert("Dwarka Sector 8");
        trie.insert("Dwarka Sector 9");
        trie.insert("Dwarka Sector 10");
        trie.insert("Dwarka Sector 21");
        List<String> limited = trie.suggest("Dwarka", 3);
        assertEquals(3, limited.size());
        assertEquals(5, trie.suggest("Dwarka", 100).size());
    }

    @Test
    void suggestWithLimitLargerThanResultsReturnsAll() {
        StationTrie trie = new StationTrie();
        trie.insert("Hauz Khas");
        assertEquals(List.of("Hauz Khas"), trie.suggest("Hauz", 50));
        assertEquals(List.of("Hauz Khas"), trie.suggest("Hauz", 1));
    }

    @Test
    void suggestWithNonPositiveLimitReturnsEmpty() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        assertTrue(trie.suggest("Rajiv", 0).isEmpty());
        assertTrue(trie.suggest("Rajiv", -3).isEmpty());
    }

    @Test
    void suggestMatchesSearchForUnboundedLimit() {
        StationTrie trie = new StationTrie();
        trie.insert("Kashmere Gate");
        trie.insert("Karol Bagh");
        trie.insert("Kirti Nagar");
        assertEquals(trie.search("K"), trie.suggest("K", 100));
    }

    @Test
    void duplicateInsertCreatesSingleEntry() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        trie.insert("Rajiv Chowk");
        trie.insert("Rajiv Chowk");
        assertEquals(1, trie.size());
        assertEquals(List.of("Rajiv Chowk"), trie.search("Rajiv"));
        assertTrue(trie.contains("Rajiv Chowk"));
    }

    @Test
    void caseVariantDuplicateKeepsFirstSpellingAndNoDuplicates() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        trie.insert("rajiv chowk");
        assertEquals(1, trie.size());
        assertEquals(List.of("Rajiv Chowk"), trie.search("rajiv"));
        assertTrue(trie.startsWith("RAJ"));
    }

    @Test
    void sizeCountsUniqueNormalisedNames() {
        StationTrie trie = new StationTrie();
        trie.insert("Anand Vihar");
        trie.insert("Azadpur");
        trie.insert("ANAND VIHAR");
        trie.insert("Azadpur");
        assertEquals(2, trie.size());
    }

    @Test
    void nullStationNameThrows() {
        StationTrie trie = new StationTrie();
        assertThrows(IllegalArgumentException.class, () -> trie.insert(null));
    }

    @Test
    void insertingBlankNameThrows() {
        StationTrie trie = new StationTrie();
        assertThrows(IllegalArgumentException.class, () -> trie.insert(""));
        assertThrows(IllegalArgumentException.class, () -> trie.insert("   "));
        assertEquals(0, trie.size());
    }

    @Test
    void nullPrefixQueriesThrow() {
        StationTrie trie = new StationTrie();
        trie.insert("Rajiv Chowk");
        assertThrows(IllegalArgumentException.class, () -> trie.contains(null));
        assertThrows(IllegalArgumentException.class, () -> trie.startsWith(null));
        assertThrows(IllegalArgumentException.class, () -> trie.search(null));
        assertThrows(IllegalArgumentException.class, () -> trie.suggest(null, 5));
    }

    private static StationTrie trieWithOrder(String first, String second, String third) {
        StationTrie trie = new StationTrie();
        trie.insert(first);
        trie.insert(second);
        trie.insert(third);
        return trie;
    }
}