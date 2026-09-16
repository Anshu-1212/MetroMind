package com.metromind.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A prefix Trie for metro station-name lookup and autocomplete.
 *
 * <p>Each node in the trie corresponds to one character of a normalised station
 * name. A name is indexed by its <em>key</em> — the name trimmed and lower-cased
 * ({@link java.util.Locale#ROOT ROOT} locale) — but the <em>original</em>
 * spelling that was inserted is stored at the terminal node. Suggestions therefore
 * always return exactly the casing and spacing used at insert time.</p>
 *
 * <p>Matching is case-insensitive and insensitive to leading/trailing whitespace
 * on both sides: inserting {@code "Rajiv Chowk"} and querying {@code "RAJIV
 * chowk"} or {@code "  rajiv chowk  "} all refer to the same entry. Internal
 * spaces (e.g. {@code "Dwarka Sector 21"}) are significant.</p>
 *
 * <p>Inserting a name that normalises to the key of an existing entry is an
 * idempotent no-op: the first spelling wins and there is never more than one
 * suggestion per normalised name, so results contain no duplicates.</p>
 *
 * <p>This class is deliberately independent of {@link com.metromind.data.MetroNetwork},
 * Spring, and the filesystem. It only knows about names; loading the dataset and
 * feeding it station names is the job of {@link StationSearchIndex}. It performs
 * no routing — search returns station <em>names</em>, while routing
 * (BFS/Dijkstra/A*) operates on station IDs.</p>
 *
 * <p>Complexity: insert O(L), exact search O(L), prefix lookup O(P), and
 * autocomplete O(P + K) (excluding output traversal details), where L is the
 * station-name length, P the prefix length, and K the number of returned
 * suggestions. A plain {@code HashMap} plus a final deterministic sort keeps the
 * behaviour predictable for the project's 40+ station dataset; no external trie
 * library is used.</p>
 */
public final class StationTrie {

    /** Deterministic, case-insensitive ordering for suggestion results. */
    private static final Comparator<String> NAME_ORDER = String.CASE_INSENSITIVE_ORDER;

    private final TrieNode root = new TrieNode();
    private int size;

    /** A single trie node: outgoing character edges plus, optionally, a terminal name. */
    private static final class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private String name;
    }

    /**
     * Creates an empty station-name trie.
     */
    public StationTrie() {
    }

    /**
     * Inserts a station name into the trie.
     *
     * <p>Names are stored by their trimmed, lower-cased key; the original
     * spelling is kept at the terminal node. Inserting the same name twice — or
     * two spellings of the same name — does not create duplicate suggestions;
     * the first spelling inserted is the one returned.</p>
     *
     * @param stationName the station name to insert; must not be null or blank
     * @throws IllegalArgumentException if {@code stationName} is {@code null}
     *                                  or blank (after trimming)
     */
    public void insert(String stationName) {
        if (stationName == null) {
            throw new IllegalArgumentException("stationName must not be null");
        }
        String key = key(stationName);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("stationName must not be blank");
        }

        TrieNode node = root;
        for (int i = 0; i < key.length(); i++) {
            node = node.children.computeIfAbsent(key.charAt(i), c -> new TrieNode());
        }
        if (node.name == null) {
            node.name = stationName;
            size++;
        }
    }

    /**
     * Checks whether a station name exists in the trie, case-insensitively.
     *
     * @param stationName the name to look up; must not be null
     * @return {@code true} if exactly this name was inserted (ignoring case and
     *         leading/trailing whitespace); {@code false} for blank input
     * @throws IllegalArgumentException if {@code stationName} is {@code null}
     */
    public boolean contains(String stationName) {
        if (stationName == null) {
            throw new IllegalArgumentException("stationName must not be null");
        }
        String key = key(stationName);
        if (key.isEmpty()) {
            return false;
        }
        TrieNode node = find(key);
        return node != null && node.name != null;
    }

    /**
     * Checks whether any inserted station name starts with the given prefix.
     *
     * @param prefix the prefix to test; must not be null
     * @return {@code true} if at least one name begins with {@code prefix}
     *         (ignoring case and leading/trailing whitespace); {@code false}
     *         for blank input
     * @throws IllegalArgumentException if {@code prefix} is {@code null}
     */
    public boolean startsWith(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("prefix must not be null");
        }
        String key = key(prefix);
        if (key.isEmpty()) {
            return false;
        }
        return find(key) != null;
    }

    /**
     * Returns all inserted station names that start with the given prefix, using
     * their original inserted spelling.
     *
     * <p>Results are sorted deterministically by case-insensitive lexicographic
     * order and are free of duplicates. Blank and unknown prefixes yield an empty
     * list.</p>
     *
     * @param prefix the prefix to complete; must not be null
     * @return an unmodifiable, sorted list of matching station names
     * @throws IllegalArgumentException if {@code prefix} is {@code null}
     */
    public List<String> search(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("prefix must not be null");
        }
        String key = key(prefix);
        if (key.isEmpty()) {
            return List.of();
        }
        TrieNode node = find(key);
        if (node == null) {
            return List.of();
        }

        List<String> results = new ArrayList<>();
        collect(node, results);
        results.sort(NAME_ORDER);
        return Collections.unmodifiableList(results);
    }

    /**
     * Returns at most {@code limit} station-name suggestions for a prefix.
     *
     * <p>Identical to {@link #search(String)} but capped. A non-positive limit
     * returns an empty list, as do blank or unknown prefixes.</p>
     *
     * @param prefix the prefix to complete; must not be null
     * @param limit  the maximum number of suggestions; must be positive
     * @return an unmodifiable, sorted list with at most {@code limit} names
     * @throws IllegalArgumentException if {@code prefix} is {@code null}
     */
    public List<String> suggest(String prefix, int limit) {
        if (prefix == null) {
            throw new IllegalArgumentException("prefix must not be null");
        }
        if (limit <= 0) {
            return List.of();
        }
        String key = key(prefix);
        if (key.isEmpty()) {
            return List.of();
        }
        TrieNode node = find(key);
        if (node == null) {
            return List.of();
        }

        List<String> results = new ArrayList<>();
        collect(node, results);
        results.sort(NAME_ORDER);
        if (results.size() > limit) {
            return Collections.unmodifiableList(new ArrayList<>(results.subList(0, limit)));
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * @return the number of unique station names currently in the trie
     */
    public int size() {
        return size;
    }

    /**
     * @return {@code true} if no station name has been inserted yet
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /** Normalised key: trimmed and lower-cased so lookups are case- and edge-space-insensitive. */
    private static String key(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** Walks the trie along a normalised key; returns the node or {@code null} if the path is missing. */
    private TrieNode find(String key) {
        TrieNode node = root;
        for (int i = 0; i < key.length(); i++) {
            node = node.children.get(key.charAt(i));
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    /** Depth-first collection of every terminal name in the subtree rooted at {@code node}. */
    private static void collect(TrieNode node, List<String> out) {
        if (node.name != null) {
            out.add(node.name);
        }
        for (TrieNode child : node.children.values()) {
            collect(child, out);
        }
    }
}