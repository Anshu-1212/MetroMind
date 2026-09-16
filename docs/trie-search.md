# MetroMind — Trie Station Search

This document explains MetroMind's station-name search and autocomplete index.
It describes why a Trie is the right structure, how the trie is organised, how
the search index is built from the real Delhi Metro dataset, the public API, and
how station search relates to the routing algorithms (BFS / Dijkstra / A\*).

## 1. Why MetroMind Needs Station Search

A commuter almost never types the exact canonical name of a station. They type
what they *think* the station is called: `"rajiv"`, `"dwarka"`, `"hauz"`,
`"new delhi"`. Occasionally they type something that is not quite a station name
at all — a misspelling, a fragment, or a phrase they half-remember.

Route planning requires an exact, unambiguous station *identity* (see §2). To
get from that fuzzy input to a correct route, MetroMind needs a search layer that
turns a partial, case-insensitive guess into a small, deterministic set of real
station names. That layer must be:

- **Fast** — it runs on every keystroke of a search box.
- **Case-insensitive** — `"RAJIV"` and `"rajiv"` are the same prefix.
- **Deterministic** — the same query in the same order always returns the same
  suggestions, so the UI is stable and the behaviour is unit-testable.
- **Spelling-preserving** — the search layer returns the original station names,
  not normalised keys, so the commuter sees exactly what the dataset spells.

This layer is the *only* current consumer-driven feature (station lookup) in
MetroMind; everything else so far is route *computation*.

## 2. Route Planning Needs an Id, not a Guess

Search and routing deliberately speak different languages:

- The search index (this Phase) works on station **names** — human-readable
  strings (`"Rajiv Chowk"`).
- The routing algorithms (BFS, Dijkstra, A\*) work on station **IDs** —
  stable machine keys (`"RAJIV_CHOWK"`) indexing into the `MetroGraph`.

The bridge is the *selection step*: a user picks a station from the search
suggestions, the UI resolves that name to its ID, and only then is the ID handed
to the router. This is the same naming split used by the earlier phases — a
`Station` carries both a human `name` and a machine `id`, and routing has always
operated on `id`.

> A search result of `"Rajiv Chowk"` is a *name*. Routing takes `RAJIV_CHOWK`.
> The Trie never sees an ID; the routers never see a name. This separation is
> enforced by construction in `com.metromind.search` (Trie knows only names) and
> `com.metromind.graph` / `com.metromind.routing` (graphs know only IDs).

## 3. Why a Trie Is Appropriate

A **Trie** (prefix tree) is a tree where each node holds one character of a key
and the path from the root to a node spells out a prefix. Station names are short
(typically 10–30 characters), and the dominant query shape — *"list everything
that starts with this prefix"* — is exactly what a trie answers in time
proportional to the prefix length and the number of matches, independent of the
total number of stations.

| Requirement | Why the Trie fits |
|---|---|
| prefix autocomplete | a prefix walk lands on one node, then a subtree walk collects matches |
| case-insensitive | keys are normalised to lower case; query prefixes are normalised identically |
| exact lookup `contains` | a path walk, O(L) |
| spelling preservation | each terminal node stores the original inserted name |
| no duplicates | first spelling of a name wins; re-inserts are idempotent |
| determinism | the result list is sorted with a fixed, case-insensitive order |

The alternative — a linear scan over all 40+ names per keystroke — is simpler but
still O(N) per query and gives no natural "prefix boundary"; a trie makes the
intent of the data structure match the intent of the feature India-wide scale,
and it stays trivial to test in isolation.

## 4. Trie Structure

```
                 (root)
                  |
      ---------------------------------
      |      |      |      |       |
      r      h      n      d       ...
      |      |      |      |
      a      a      e      w
      |      |      |      |
      j      u      w      a  ...
      |      |      |
      i      z      [New Delhi]*      (* terminal node, stores "New Delhi")
      v      |
             [Hauz Khas]*           (* terminal node, stores "Hauz Khas")
```

Each node has:

- `Map<Character, TrieNode> children` — one edge per distinct next character.
- `String name` — non-null **iff** a complete station name ends at this node;
  it holds the *original* spelling for the name (space- and case-preserving).

A station name is inserted by writing its lower-cased key character by character,
creating nodes as needed, and finally stamping the original name on the terminal
node. Because parallel edges never exist in a metro name (each name is unique),
each terminal stores exactly one name.

## 5. Case-Insensitive Matching

All lookups are case-insensitive:

- **Insert** stores a key computed as `name.trim().toLowerCase(Locale.ROOT)`,
  but keeps the original spelling.
- **Exact search / contains / startsWith / search / suggest** normalise the
  *query* the same way before walking the trie.

So `insert("New Delhi")` makes `contains("new delhi")`, `contains("NEW DELHI")`,
`contains("  new Delhi  ")` all return `true`, and `search("ne")` returns the
original `"New Delhi"`. Comparison is done on the normalised lower-case key, so
the match is purely case-insensitive; it is **not** affected by whether the
original name had capitals.

The lower-casing uses `Locale.ROOT` so results are deterministic on every
platform, regardless of the JVM's default locale.

## 6. Duplicate Handling

Inserting the same name twice, or two spellings that normalise to the same key,
is handled safely:

- The trie keeps the **first** original spelling that was inserted.
- Inserting again is an idempotent no-op — it never creates a duplicate entry.
- `search`, `suggest`, and `contains` therefore never return the same station
  twice, and `size()` counts unique names.

This is what makes the "No TRI filename" rule in the spec a non-issue: each
station is inserted exactly once from the dataset, and a name collision (should
the data ever change) degrades gracefully to a single spelling rather than a
duplicated suggestion.

## 7. StationSearchIndex — Building the Trie from the Dataset

`com.metromind.search.StationSearchIndex` is the integration component that
connects the raw network to the trie:

```
MetroNetwork
      ↓
StationSearchIndex
      ↓
StationTrie
```

- `new StationSearchIndex(MetroNetwork network)` walks `network.getStations()`
  and inserts every `Station.getName()` into a `StationTrie`.
- It never loads the JSON file itself — the caller supplies an in-memory
  `MetroNetwork` (produced by `MetroNetworkLoader.loadDefault()`).
- It validates the input: a `null` network is rejected; null, blank, or
  whitespace-only entries are skipped defensively so a single malformed record
  cannot break the whole index.

`StationSearchIndex` intentionally has **no** Spring annotationsasi, no
filesystem access, and no dependency on the loader. It is a plain object that
turns a `MetroNetwork` into a searchable structure. (There is an obvious typo in
`MetroNetwork.loadDefault`'s javadoc name — `MetroNetworkLoader.loadDefault` —
but that is a naming detail, not a code change.)

## 8. Public API

`com.metromind.search.StationTrie`:

| Method | Behaviour |
|---|---|
| `void insert(String stationName)` | inserts a name (case-insensitive key, first spelling kept); rejects null / blank |
| `boolean contains(String stationName)` | exact name lookup, case-insensitive |
| `boolean startsWith(String prefix)` | does any name start with this prefix? |
| `List<String> search(String prefix)` | every name starting with the prefix, sorted, deduplicated |
| `List<String> suggest(String prefix, int limit)` | up to `limit` sorted suggestions |
| `int size()` / `boolean isEmpty()` | number of unique names inserted |

`com.metromind.search.StationSearchIndex` (integration): constructor from a
`MetroNetwork` plus the same search methods, all delegated to the trie.

Search methods never throw on blank/unknown prefixes: they simply return an
empty list. Only `null` arguments throw (an `IllegalArgumentException`), and a
non-positive `limit` returns an empty list. Results are returned as unmodifiable
lists and are deterministic (sorted with `String.CASE_INSENSITIVE_ORDER`).

## 9. Complexity

For a station name of length `L`, a prefix of length `P`, and `K` returned
candidates:

| Operation | Time | Space |
|---|---|---|
| `insert` | O(L) | O(L) new nodes worst case |
| `contains` (exact) | O(L) | O(1) |
| `startsWith` | O(P) | O(1) |
| `search` / `suggest` | O(P + K) to reach and collect; O(K log K) to sort | O(K) results |
| overall trie | — | O(total inserted characters) ≈ O(L·N) for N names |

The sort of `K` results is what makes the output deterministic; N (total names)
never appears in the per-query cost. This is why prefix lookup stays fast even
as the network grows: O(P + K log K) is independent of N.

## 10. Edge Cases

- **Null names/prefixes** are rejected up front with a clear exception rather
  than surfacing as obscure failures mid-walk.
- **Blank / whitespace-only** prefixes return an empty list (never an error),
  and `search("")` returns no suggestions.
- **Unknown prefixes** return an empty list.
- **Leading/trailing whitespace** in either the inserted name or the query is
  trimmed, so `"  dwarka  "` is treated the same as `"dwarka"`.
- **Case variants** match case-insensitively, so `"RAJIV"` and `"rajiv"` are
  the same prefix.
- **Duplicate inserts** are idempotent and never produce duplicate suggestions.
- **Names with spaces / punctuation** ("Dilli Haat - INA") index and search
  correctly.

## 11. Example Searches

With the real Delhi dataset loaded through `StationSearchIndex`:

| Query | Result |
|---|---|
| `search("Rajiv")` | `["Rajiv Chowk"]` |
| `search("Dwarka")` | 8 names: Dwarka Mor, Dwarka Sector 10, 11, 12, 14, 21, 8, 9 (sorted) |
| `search("hauz")` | `["Hauz Khas"]` |
| `suggest("D", 3)` | 3 suggestions (the first three "D..." names, sorted) |
| `contains("new delhi")` | `true` |
| `startsWith("Kash")` | `true` |

## 12. Search vs Routing — the connection

Search produces **names**; routing consumes **IDs**. The station picked from
autocomplete resolves to an ID, which then feeds the `MetroGraph`-based
routers (BFS, Dijkstra, A\*). The Trie does not compute routes, and the routers
do not search names. Keeping them separate means:

- the Trie stays tiny, pure, and testable in isolation;
- routing stays focused on graph weights and never re-parse station names;
- a station can be renamed without touching routing, and vice versa.

## 13. Limitations

- **Name search only** — the Trie matches station *names*, not IDs, not partial
  IDs, not line names (searching for a line like "Yellow Line" requires a
  different index, provided by the dataset's line list).
- **No typo tolerance** — a single wrong letter breaks a prefix match; no
  Levenshtein or fuzzy matching is implemented.
- **No ranking by relevance** — suggestions are returned in a deterministic,
  case-insensitive alphabetic order, not by popularity or frequency.
- **No deployment** — the search index is a plain Java object; it is not exposed
  over REST or to a frontend yet.

## 14. Summary

The Trie gives MetroMind a fast, case-insensitive, deterministic station-name
search and autocomplete layer, built from the same validated dataset used by
routing. It is a pure in-memory data structure with a clean API, separated from
filesystem, Spring, and the routing algorithms, and verified against the real
Delhi Metro station names by both unit tests and dataset-level tests.
