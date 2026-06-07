<!--
SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Onymdroid — plan and context

An Android port of **Onym**, the GTK4/libadwaita WordNet thesaurus. This document is mostly a
faithful specification of how the existing app works, so the engine and the behaviour can be
reproduced exactly on Android. The port plan follows at the end.

The source app lives at `../onym` (a sibling of this folder). Read it alongside this document; every
claim here is drawn from that tree and cites the file it came from.

- Upstream: `nz.ursa.Onym`, version 0.1.0, GPL-3.0-or-later.
- Data: WordNet 3.0 (Princeton), via Debian's package. Engine derived from **Artha** (Sundaram
  Ramaswamy).
- The single most useful fact for this port: **`onym-cli` is a deterministic golden oracle.** You can
  diff the Android engine's output against it for thousands of words and get exact parity, byte for
  byte, in the format documented in [§7](#7-the-cli-golden-oracle).

---

## Contents

- Part 1 — Context (what Onym is, exactly)
  - [0. Principles to carry across](#0-principles-to-carry-across)
  - [1. What the app does](#1-what-the-app-does)
  - [2. Architecture and what it means for the port](#2-architecture-and-what-it-means-for-the-port)
  - [3. The data model](#3-the-data-model)
  - [4. The lookup pipeline and relation→section mapping](#4-the-lookup-pipeline)
  - [5. Completion and suggestion](#5-completion-and-suggestion)
  - [6. The WordNet data dependency](#6-the-wordnet-data-dependency)
  - [7. The CLI golden oracle](#7-the-cli-golden-oracle)
  - [8. The UX specification](#8-the-ux-specification)
  - [9. Strings, constants, settings](#9-strings-constants-settings)
- Part 2 — The port plan
  - [10. Strategy: reimplement the engine in Kotlin](#10-strategy)
  - [11. UI mapping to Compose Material 3 / Material You](#11-ui-mapping)
  - [12. State and ViewModel](#12-state-and-viewmodel)
  - [13. Data packaging](#13-data-packaging)
  - [14. Distribution (no Play Store)](#14-distribution)
  - [15. Validation strategy](#15-validation-strategy)
  - [16. Milestones and effort](#16-milestones-and-effort)
  - [17. Risks](#17-risks)
  - [18. Open decisions](#18-open-decisions)
- [Appendix A — Onym repo file index](#appendix-a)
- [Appendix B — Conventions (copy-pasteable)](#appendix-b)

---

# Part 1 — Context

## 0. Principles to carry across

These are the things that make Onym a clean codebase, not the mechanics. Reproducing §4's relation
mapping but ignoring these would yield a working app that loses what is worth porting. Each principle
is stated as it manifests in Onym, then translated to the Android/Kotlin/Compose context. **Treat this
section as the acceptance criteria for the architecture, the same way §7's oracle is the acceptance
criteria for the engine.**

### Architecture and boundaries

1. **Layers that narrow as data flows up; dependencies point one way.** In Onym the engine returns
   WordNet structures, the bridge copies them into model objects and frees them, and "from there
   nothing knows WordNet exists" (`ARCHITECTURE.md`). Each layer knows less about what is below it.
   *Android*: keep the same three modules — a pure engine/core (no Android), a ViewModel/state layer,
   and Compose UI — with dependencies pointing strictly inward. The UI must never import a WordNet
   type, and the engine must never import an Android one. Enforce it with module boundaries
   (a `:core` Gradle module with no `android` dependency), not just discipline.

2. **Seal borrowed code behind a stable interface; never edit it; record provenance.** Artha's
   `wni.c/.h` are "vendored verbatim", built as their own static library with warnings silenced, and
   "never edited, so the borrow stays verifiable against upstream", with licensing in `REUSE.toml`
   and a `PROVENANCE.md`. *Android*: the WordNet reader (extJWNL) plays Artha's role. Put it behind a
   **single adapter interface** (`WordNetSource`) that the rest of the code talks to; never fork or
   patch it; pull it as a versioned dependency. If you ever must vendor or patch a reader, isolate it
   in one module and record provenance the same way. The adapter is the new borrow boundary — only it
   knows extJWNL exists.

3. **A read-only, immutable, language-neutral model is the only thing that crosses the boundary.**
   Every model type is "a small final GObject", every collection a list; constructors live in a
   private, uninstalled header, so "consumers read the model; they never build it." The model carries
   "no WordNet or engine types." This is precisely why a port is even feasible. *Android*: the model
   is immutable `data class`es (§3) with **`internal` constructors owned by `:core`**; the UI receives
   them and only reads. No presentation types (no `Color`, no `AnnotatedString`) leak into the model.

4. **The view is dumb; every semantic decision lives in the library.** "The model carries the section
   order, so display order is decided here" — in the bridge, not the view. Section titles, what counts
   as a result, dropping empty sections, the direct/indirect antonym distinction: all decided in the
   library; the renderer just walks the model in order. *Android*: the engine emits ordered, titled
   sections; the composables render whatever they are handed without re-sorting, re-titling, or
   deciding what to hide. Lexical knowledge never migrates into the UI.

### Correctness and testability

5. **A headless oracle and golden tests that assert structure, not content.** `onym-cli` exists to
   "prove the library" with "deterministic output"; the lookup test "asserts structural facts rather
   than exact glosses, so it stays robust across WordNet releases", and skips itself when data is
   absent. *Android*: this is §15 — a CLI-equivalent dumper plus structural comparison, run as a CI
   gate. Test the shape (sections present, order, tree structure, direct/indirect flags), not exact
   glosses, so tests survive a WordNet bump.

6. **Pure functions, isolated from I/O, testable without data.** The display/query form helpers and
   edit distance "are exported so the unit tests can exercise them without any WordNet data." *Android*:
   keep the form conversions, edit distance, and as much of the relation-mapping logic as possible as
   **pure Kotlin functions in `:core`** with no Android and no asset dependency, unit-tested directly.
   I/O (reading the dict) sits at the edge behind the §2 adapter.

7. **Be honest about constraints; document invariants precisely.** Onym states plainly that "the
   WordNet C library keeps global state and is not reentrant, so every call on an engine must come
   from one thread", and explains why that is acceptable (sub-millisecond lookups). *Android*: document
   the threading model (lookups off the main thread via a coroutine dispatcher, the reader's
   reentrancy assumptions), the data-dir contract, and the morphology assumptions — in comments and a
   short `ARCHITECTURE.md` of its own. Do not hide a constraint; name it and say why it is fine.

8. **Defensive at boundaries; degrade gracefully.** The bridge hands the engine a copy because "the
   engine may write to the search string", null-checks every item in every loop, and **drops a section
   that gathered no items** rather than render an empty heading (`add_section_if_filled`). *Android*:
   null-safe, total mapping from the reader; never render a hollow section; a missing dict is a clear
   error state, an absent word is a clean "no entry", a partial word in live search shows nothing
   rather than flashing not-found (§8.2).

### Restraint and craft

9. **A thin application; resist feature creep.** The app is deliberately "a thin GTK4 and libadwaita
   application that consumes" the library; the preferences are "a small dialog: for now, how far
   relation trees open by default" — one setting, until there is a reason for two. *Android*: match
   Onym's surface area first. One preference, the same three states, the same entry points. Native
   extras (text-selection lookup, share target) are additions at the *edges*, not new complexity in
   the core. Ship the thin thing well before broadening it.

10. **Accessibility is part of "done", woven in, not a later pass.** Onym rolls its own flow cells so a
    screen reader "reads accurately and quiet", exposes tree depth and expanded state, and gives each
    chip a relation description. *Android*: design TalkBack semantics alongside each composable (§8.6),
    not as a cleanup sprint. A section is not finished until its semantics are.

11. **Provenance, licensing, and attribution rigour, as identity.** Every file carries an SPDX header;
    `REUSE.toml` tracks licences; the About screen credits Princeton's WordNet and Artha/Sundaram
    Ramaswamy, and carries a Country acknowledgement (Kaurna). This is part of what the project *is*.
    *Android*: SPDX headers and REUSE compliance from commit one (also smooths F-Droid, §14); carry the
    WordNet and Artha credit and the Country acknowledgement across verbatim; keep the GPL-3.0-or-later
    licence. Attribution is not boilerplate here — it is the ethic.

12. **Literate prose; plain Australian English; no marketing.** The comments explain *why*, not *what*
    ("a GtkRevealer is used rather than a GtkExpander because…"), the spelling is British/Australian
    ("colour", "behaviour", "licence", "maximised"), and the copy is plain. *Android*: match it — `why`
    comments, Australian spelling in code and user-facing strings, no marketing voice. See the project's
    house style (short sentences, Australian English, FOSS-first, no marketing).

The thread tying these together: **knowledge lives in exactly one place and flows one way.** WordNet
knowledge lives in the adapter; lexical/semantic decisions live in the engine; presentation lives in
the UI; and nothing downstream reaches back upstream. If a change tempts you to leak a WordNet detail
into a composable or a colour into the model, that is the signal a boundary is being crossed.

The concrete house rules these imply — exact SPDX headers, the licence split, comment templates,
prose style, and commit conventions — are spelled out copy-pasteable in [Appendix B](#appendix-b).

## 1. What the app does

Onym is a thesaurus and dictionary. You type a word; it resolves the word through WordNet morphology
(so an inflected query like *running* resolves to *run*) and shows a single reading column:

1. the resolved **headword** as a title;
2. its **meanings**, grouped and numbered, each with a dimmed part of speech and italic example
   sentences;
3. its **synonyms** and **antonyms** as clickable chips;
4. the **lexical relations** that connect it to other words — derived forms, similar-to, attributes,
   causes, entails, and the *is-a / kinds / part-of / parts* hierarchies shown as expandable trees.

Every synonym, antonym, and term in a relation tree is clickable, so one lookup leads to the next.
Search is live: completion appears as you type, the result updates after a short pause, and a
"did you mean" suggestion appears when a word is missed. There is back/forward history, a recent-words
list, and one preference (how far relation trees open by default).

No network, no accounts, no background work. Everything is a local WordNet lookup that returns in well
under a millisecond.

## 2. Architecture and what it means for the port

Onym is three layers with deliberate boundaries (`../onym/ARCHITECTURE.md`):

```
 application (GTK4 + libadwaita)   src/*.c            ~1,300 lines   ← NOT reusable; rebuild in Compose
 ─────────────────────────────────────────────────────────────────
 libonym  (public model + engine) libonym/*.c        ~1,400 lines   ← reimplement the LOGIC in Kotlin
 ─────────────────────────────────────────────────────────────────
 engine   (vendored Artha)        libonym/engine/    ~2,300 lines   ← replace with a JVM WordNet lib
 ─────────────────────────────────────────────────────────────────
 WordNet C library + dict files   (system / bundled) ~15 MB data    ← reuse the DATA, not the C lib
```

What this means concretely:

- **The model (`onym-result.h`) is a language-neutral contract.** It carries no GTK and no WordNet
  types — every type is a small immutable object, every collection a list. It maps 1:1 to Kotlin
  `data class`es. Mirror it verbatim ([§3](#3-the-data-model)).
- **The UI does not port.** It is rebuilt in Jetpack Compose. The GTK code is still valuable as the
  exact behavioural spec ([§8](#8-the-ux-specification)).
- **The engine is reimplemented, not ported.** The genuinely valuable, hard-to-rederive logic is the
  ~340-line bridge `onym-lookup.c` ([§4](#4-the-lookup-pipeline)) plus the ~275-line index in
  `wn-index.c` ([§5](#5-completion-and-suggestion)). Both are *rules over WordNet data*, expressible
  in Kotlin against any WordNet reader. Do **not** try to cross-compile the C/GLib stack for Android
  (see [§10](#10-strategy) for why that is a trap).

## 3. The data model

From `../onym/libonym/onym-result.h` and `onym-result.c`. Every string a consumer sees is in
**display form** (underscores already turned to spaces — see [§4](#4-the-lookup-pipeline)).

A faithful Kotlin mirror:

```kotlin
data class OnymResult(
    val term: String,                 // resolved headword, display form
    val sections: List<OnymSection>,  // in display order; see §4 for the exact order
)

sealed interface OnymSection {
    val title: String                 // exact title strings in §4 / §9
    data class Definitions(override val title: String, val items: List<OnymDefinition>) : OnymSection
    data class Words(override val title: String, val items: List<OnymWord>) : OnymSection
    data class Antonyms(override val title: String, val items: List<OnymAntonym>) : OnymSection
    data class Tree(override val title: String, val items: List<OnymTreeNode>) : OnymSection
}

data class OnymWord(val term: String)                              // a clickable term

data class OnymDefinition(
    val pos: String?,                 // "noun" | "verb" | "adjective" | "adverb" | null
    val gloss: String,
    val examples: List<String>,       // may be empty
)

data class OnymAntonym(
    val term: String,
    val direct: Boolean,              // direct vs indirect (through a similar sense)
    val implications: List<OnymWord>, // related implication terms; may be empty
)

data class OnymTreeNode(
    val terms: List<String>,          // the synset's terms, each clickable
    val children: List<OnymTreeNode>,
) {
    // EXACT: label = terms.joinToString(", ")  — used for display and CLI dedup (onym-result.c:304)
    val label: String get() = terms.joinToString(", ")
}
```

The original `OnymSectionKind` enum (`DEFINITIONS, WORDS, ANTONYMS, TREE`) tells a renderer which item
type a section holds; the sealed interface above encodes the same thing.

## 4. The lookup pipeline

Source: `../onym/libonym/onym-lookup.c` (the bridge) and `onym-engine.c`. This is the crux of the
port — reproduce it exactly.

### 4.1 Query and display forms (`wn-index.c`)

- **query form** = trim whitespace, then replace spaces with underscores. `"  ice cream "` → `"ice_cream"`.
- **display form** = replace underscores with spaces. `"ice_cream"` → `"ice cream"`.
- The engine queries WordNet with the query form; every string in the returned model is display form.

### 4.2 Morphology and headword resolution

- WordNet's Morphy resolves an inflected query to a base form. The Android WordNet library must do the
  same (extJWNL/JWI both ship a Morphy equivalent — `WordnetStemmer` / `MorphologicalProcessor`).
- The **resolved headword** is taken from the overview's first definition item's lemma, in display
  form; if absent, the original query string is used (`onym-lookup.c:overview_headword`,
  `onym_bridge_lookup`).
- A word simply not in WordNet → **no result** (distinct from "database missing", which is an error).

### 4.3 Part-of-speech codes → names (`onym-lookup.c:pos_name`)

```
1 → "noun"    2 → "verb"    3 → "adjective"    4 → "adverb"    5 → "adjective" (satellite)    else → null
```

### 4.4 Sections, in exact emission order

`onym_bridge_lookup` emits sections in this order. **A section that gathers zero items is dropped**
(`add_section_if_filled`). So a real result is this list, minus the empties.

| # | Title (exact) | Kind | WordNet source | Notes |
|---|---|---|---|---|
| 1 | `Definitions` | Definitions | OVERVIEW glosses | Grouped sense by sense; each definition becomes one item with its `pos`, gloss, and examples. |
| 2 | `Synonyms` | Words | OVERVIEW synonyms | Each is a display-form term. |
| 3 | `Antonyms` | Antonyms | ANTONYMS | direct vs indirect + implication terms (§4.5). |
| 4 | `Derived forms` | Words | DERIVATIONS | flat list. |
| 5 | `Similar to` | Words | SIMILAR | flat list. |
| 6 | `Attributes` | Words | ATTRIBUTES | flat list. |
| 7 | `Causes` | Words | CAUSES | flat list. |
| 8 | `Entails` | Words | ENTAILS | flat list. |
| 9 | `Pertains to` | Tree | PERTAINYMS | hierarchy (§4.6). |
| 10 | `Is a kind of` | Tree | HYPERNYMS | hierarchy to root (the is-a chain). |
| 11 | `Kinds` | Tree | HYPONYMS | hierarchy (the full subtree — can be large). |
| 12 | `Part of` | Tree | HOLONYMS | **all holonym subtypes combined** (member/substance/part). |
| 13 | `Parts` | Tree | MERONYMS | **all meronym subtypes combined** (member/substance/part). |
| 14 | `Domains` | Words | CLASS | domain/category terms, flat list. |

The engine is asked for **all relations at full depth** (`WORDNET_INTERFACE_ALL`, advanced mode), so
the tree relations come back grown to full depth, not just one level.

### 4.5 Antonyms (`add_antonyms`)

Each antonym carries:
- `term` (display form);
- `direct` = true when WordNet marks it a **direct** antonym, false when **indirect** (an antonym
  reached through a *similar-to* sense — this is how satellite adjectives get antonyms);
- `implications` = a list of related implication terms (display form), may be empty.

Indirect antonyms are the fiddliest thing to reproduce. In WordNet terms: for an adjective satellite
with no direct antonym, you follow `SIMILAR_TO` to the head synset and report its antonym as an
indirect antonym of the query, with the head/cluster terms as implications. Validate this hard against
the oracle (try *beautiful*, *fast*, *good*).

### 4.6 Trees (`add_tree` + `convert_gnode`)

- The engine returns, per sense, a root node whose **children** are the top-level relation nodes.
- Each node's data is a synset; `convert_gnode` turns it into an `OnymTreeNode` whose `terms` are the
  synset's terms in display form, recursing into children to full depth.
- **Top-level nodes are de-duplicated by `label` across senses** (a `seen` set keyed on the joined
  label). Deeper nodes are not de-duplicated.
- `label = terms.joinToString(", ")`.

### 4.7 Domains (`add_domains`)

CLASS relation → a `Words` section titled `Domains`, each term in display form.

## 5. Completion and suggestion

Source: `../onym/libonym/wn-index.c`. This module depends only on the index files, not the WordNet
runtime. It is small and ports directly to Kotlin.

### 5.1 Building the lemma index

- Read `index.noun`, `index.verb`, `index.adj`, `index.adv` from the data dir.
- For each line **not** starting with a space (space-led lines are the licence header), take the first
  space-delimited field — the lemma.
- Convert to **lowercased display form** (underscores → spaces, then ASCII-lowercase).
- Collect all four files, **sort** (plain `strcmp`/byte order), then **de-duplicate** adjacent equals.
- Result: one sorted, deduped array of every headword, lowercased, in display form.

### 5.2 Completion (`wn_index_complete`)

- Needle = lowercased display form of the typed prefix.
- Binary-search the lower bound, then walk forward collecting lemmas that start with the needle, until
  the prefix stops matching or the cap is hit.
- Cap: **8** in the app's completion popover (`ONYM_COMPLETION_MAX`); the CLI uses 20.
- Returned in sorted order (i.e. alphabetical), lowercased display form.

### 5.3 Suggestion / "did you mean" (`wn_index_suggest`)

- Needle = lowercased display form of the missed word.
- For each lemma: skip if `abs(len(lemma) − len(needle)) > 2` (cheap length prefilter).
- Compute Levenshtein edit distance (`onym_edit_distance`, a standard two-row DP over bytes); keep
  lemmas with **distance in [1, 2]**.
- Sort candidates by **(distance asc, then term alphabetical)**; return up to the cap.
- Cap: the not-found page requests **5**; the CLI `--suggest` uses 10.

## 6. The WordNet data dependency

- Footprint: **~15 MB** uncompressed. Largest files: `data.noun` 7.5 MB, `index.noun` 2.4 MB,
  `data.adj` 1.7 MB, `data.verb` 1.5 MB. Plus `index.{verb,adj,adv}`, `data.{verb,adj,adv}`, the
  morphology exception files `*.exc`, `cntlist.rev`, and the verb-frame files.
- Onym locates the dir by: `WNSEARCHDIR`, else `WNHOME/dict`, else a build-time default
  (`/usr/share/wordnet`). It probes for `index.noun` to decide the data is present
  (`onym-engine.c:resolve_data_dir`, `ensure_data`).
- For Android: ship these files in `assets/` (or a downloadable pack) and point the WordNet reader at
  the extracted/asset path. See [§13](#13-data-packaging).
- Licence: WordNet 3.0 is under the permissive WordNet licence (BSD-like) — redistributable. The
  Flatpak already bundles Debian's prebuilt database; reuse the same files.

## 7. The CLI golden oracle

`../onym/tools/onym-cli.c` prints a **deterministic, stable** rendering. The project's own tests rely
on it. Use it as the parity oracle for the Android engine.

Modes:

```
onym-cli WORD                 # look up and print the entry  (same as --dump)
onym-cli --dump WORD          # identical, named for snapshots
onym-cli --complete PREFIX    # headwords beginning with PREFIX (cap 20)
onym-cli --suggest WORD       # spelling suggestions (cap 10)
```

Exact `--dump` format (reproduce this in a Kotlin dumper, then diff):

```
term: <headword>
[<Section Title>]
  - (<pos>) <gloss>          # definitions; "(pos) " omitted when pos is null
      "<example>"            # one per example, six-space indent
  - <term>                  # words sections
  - <term> (direct|indirect) # antonyms
      -> <implication>       # one per implication
- <label>                    # tree roots at depth 0 (two-space indent per the printer)
  - <label>                  # children indented two more spaces per level
```

Note the tree printer indents `depth+1` units of two spaces (root prints with one leading indent;
each level adds two more). Match the printer in `onym-cli.c:print_tree_node` exactly if you diff tree
output — or, more robustly, parse both sides into the structured model and compare trees structurally.

Missed lookup prints:

```
No entry for "<word>".
Did you mean: <s1>, <s2>, ...     # only when there are suggestions
```

## 8. The UX specification

Source: `../onym/src/onym-window.c`, `onym-window.ui`, `onym-result-view.c`, `onym-application.c`,
`onym.css`. Reproduce the *behaviour*; the chrome becomes Material You.

### 8.1 Window and states

- One window. Header bar holds: **Back**, **Forward**, a centred **search field**
  ("Search a word"), and an overflow **menu**.
- The body is a stack of three states with a crossfade:
  - **welcome** — centred icon + "Onym" + "Search for a word to see its meanings, synonyms, and
    antonyms." Shown at start and when the field is cleared.
  - **notfound** — centred icon + `No entry for "<word>"`; if suggestions exist, a "Did you mean" line
    and a row of clickable suggestion chips (each commits a lookup). Otherwise no chips.
  - **result** — a vertically scrolling reading column, width-clamped to **640 px** and centred
    (`AdwClamp`; margins top 18, bottom 24, sides 12).
- The window title tracks the shown word (the headword on the result state, "Onym" otherwise). On
  Android this maps to the screen/app-bar title and is also an accessibility cue.

### 8.2 Live search (the important behaviour)

On every keystroke (`on_search_changed`), two things happen:

1. **Completion** updates immediately: prefix-complete the typed text (cap 8) and show a dropdown
   under the field. Hide it when there is nothing to add, or when the only match equals what was
   typed (`only_exact`). Keyboard: Down/Up move the selection, Escape hides, Enter accepts the
   selected completion.
2. **A debounced silent lookup** is (re)scheduled for **250 ms** later (`ONYM_DEBOUNCE_MS`). When it
   fires, it looks the typed word up and, **if found**, updates the result view — but does **not**
   record history/recents and does **not** flash the not-found page on a partial word
   (`on_debounce`, and `show_word(..., record=false, show_miss=false)` semantics).

A **committed lookup** (records history + recents, shows not-found on a miss) happens on: pressing
Enter, accepting a completion, clicking a word/antonym/tree chip, clicking a suggestion, or choosing a
recent word.

When a lookup resolves, the search field is set to the resolved headword **without** retriggering the
live handler (a `suppress_changed` guard — replicate with a flag or by not feeding programmatic text
edits back into the debounce flow).

### 8.3 History (back/forward)

- A list of resolved headwords plus a current index (`history`, `history_pos`).
- A committed lookup `push_history(term)`: if it equals the current entry, no-op; otherwise truncate
  everything after the current index (you've branched) and append, moving the index to the end.
- Back/forward move the index and re-show that word **without** re-recording. The buttons enable/
  disable at the ends.

### 8.4 Recent words

- Persisted list, most-recent-first, **deduplicated**, capped at **10** (`ONYM_RECENT_LIMIT`). A
  committed lookup moves its headword to the front. Shown as a section in the overflow menu; choosing
  one commits a lookup.

### 8.5 Result rendering (`onym-result-view.c`)

- **Headword**: large title (`title-1`), heading role.
- **Each section**: a heading, then its body.
- **Definitions**: a numbered list. Each line: `N.  <dimmed pos>  <gloss>` (the pos is dimmed to ~60%
  alpha; omitted when null). Example sentences below, italic, in quotes, indented.
- **Synonyms / Antonyms / Domains / flat relations**: wrapped **chips** (a flow of pill buttons).
  Antonyms read their direct/indirect status to assistive tech but look the same.
- **Trees**: each node shows its terms as chips on one line; a node with children gets a **disclosure
  toggle** beside the chips and an animated reveal of its indented children (a guide line marks the
  nesting). Tapping a term chip commits a lookup; tapping the toggle expands/collapses.
- **Default tree open state** follows the *tree-expansion* preference:
  - `collapsed` (0) — nothing open;
  - `chains` (1, the default) — open a node **iff it has exactly one child** (i.e. open linear chains,
    keep branching nodes closed);
  - `all` (2) — everything open.
  Changing the preference re-renders the current result in place.

### 8.6 Accessibility (worth matching on TalkBack)

The GTK version is careful: headings carry levels; chips carry a relation description ("felicitous,
synonym"); the tree exposes tree/treeitem roles with depth and expanded state; the completion field
advertises a popup and active-descendant. In Compose, reproduce with `semantics { heading() }`,
`contentDescription`, `collapse`/`expand` actions, `stateDescription`, and a live region on the
result. This is real work and is easy to under-budget.

### 8.7 Entry points

Desktop Onym accepts `onym WORD` to open straight to a word. The Android equivalents are the genuinely
nice native wins: a **text-selection "Onym" action** (`PROCESS_TEXT` intent), a **share-to-Onym**
target, and optionally a home-screen **search widget**.

## 9. Strings, constants, settings

**Exact section titles** (also in §4.4): `Definitions`, `Synonyms`, `Antonyms`, `Derived forms`,
`Similar to`, `Attributes`, `Causes`, `Entails`, `Pertains to`, `Is a kind of`, `Kinds`, `Part of`,
`Parts`, `Domains`.

**Constants** (source → value):
- debounce before silent lookup: **250 ms** (`ONYM_DEBOUNCE_MS`)
- completion cap in popover: **8** (`ONYM_COMPLETION_MAX`)
- recent words cap: **10** (`ONYM_RECENT_LIMIT`)
- suggestions on not-found: **5**; suggestion edit-distance window: **1–2**; length prefilter: **≤2**
- reading column max width: **640** px (`AdwClamp`)
- "roomy" extra section spacing applies above a window height of **700** px (cosmetic; ignore on phone)

**Persisted settings** (`data/nz.ursa.Onym.gschema.xml`) — map to Jetpack DataStore:
- `recent-words: string[]` (most recent first)
- `tree-expansion: enum {collapsed=0, chains=1, all=2}`, default `chains`
- `window-width/height/maximized` — desktop only, drop on Android.

**Other UI strings**: placeholder "Search a word"; welcome title "Onym" / description "Search for a
word to see its meanings, synonyms, and antonyms."; not-found title `No entry for "<word>"`,
description "Did you mean"; menu "Recent", "Preferences", "About Onym"; preference row "Expand relation
trees" with options "Collapsed", "Linear chains", "Everything" and subtitle "How the is-a, kinds, and
part-of trees open by default". The About text and acknowledgements (Princeton WordNet, Artha/Sundaram
Ramaswamy, GNOME stack) are in `onym-application.c` — carry the WordNet and Artha credit across; the
GNOME credit is replaced by the Android stack.

---

# Part 2 — The port plan

## 10. Strategy

**Reimplement the engine in pure Kotlin over a JVM WordNet library; rebuild the UI in Jetpack Compose
Material 3. No NDK.**

Why not reuse the C via NDK + JNI: it would mean cross-compiling GLib (+ libffi, pcre2, zlib,
gettext) and the WordNet C library for every ABI, then writing a flattening JNI shim because the
GObject/list model does not bridge to the JVM cleanly — a heavy APK and a fragile multi-ABI build, to
reuse ~1,400 lines of logic that is really *rules over data*. Not worth it.

The Kotlin path: a JVM WordNet reader gives synsets, pointers, glosses, examples, morphology, and the
index — everything the bridge needs. Re-express [§4](#4-the-lookup-pipeline) and
[§5](#5-completion-and-suggestion) against it, mirror the model from [§3](#3-the-data-model), and
validate against the oracle ([§7](#7-the-cli-golden-oracle)). Pure JVM: no native build matrix, small
APK overhead, debuggable.

**WordNet library choice — prefer extJWNL.** extJWNL is BSD-2, published on Maven Central, reads the
standard dict files, and exposes every pointer type the bridge needs (`HYPERNYM`, `HYPONYM`, the three
`*_HOLONYM`/`*_MERONYM` subtypes, `ANTONYM`, `SIMILAR_TO`, `ATTRIBUTE`, `DERIVED`, `ENTAILMENT`,
`CAUSE`, `PERTAINYM`, the domain/`CATEGORY` types) plus a Morphy. JWI is an alternative but its
licensing/distribution is fuzzier and F-Droid builds from source, so extJWNL is the cleaner fit
([§14](#14-distribution)). Confirm extJWNL's relation coverage maps onto §4.4 during a spike before
committing.

## 11. UI mapping

Material You = Material 3 + dynamic colour (`dynamicLightColorScheme(context)` /
`dynamicDarkColorScheme`, API 31+). The existing `.word-chip` is already a Material pill, so the
aesthetic transfers naturally.

| Onym (GTK/libadwaita) | Compose Material 3 |
|---|---|
| `AdwApplicationWindow` / `AdwToolbarView` | `Scaffold` |
| `AdwHeaderBar` with search in the title | M3 `SearchBar` / `DockedSearchBar` (gives the completion dropdown for free) |
| Back / Forward / menu | `IconButton`s + `DropdownMenu` |
| `GtkStack` welcome/notfound/result | `Crossfade` over a sealed UI state |
| `AdwStatusPage` | centred `Column` (icon + title + body + suggestion chips) |
| `AdwClamp` (640 px) | `Modifier.widthIn(max = 640.dp)` centred |
| `GtkScrolledWindow` | `LazyColumn` |
| `.word-chip` in a `GtkFlowBox` | `SuggestionChip` in `FlowRow` |
| definition markup (dim pos, italic example) | `Text` + `AnnotatedString` |
| tree node + `GtkRevealer` disclosure | recursive composable + `AnimatedVisibility`, hoisted expand state |
| completion popover | `SearchBar` suggestions slot, or a `LazyColumn` under the field |
| `GSettings` | DataStore Preferences |
| `AdwAboutDialog` / `AdwPreferencesDialog` | an About screen + a single-choice settings dialog |
| `AdwBreakpoint` "roomy" | drop, or `WindowSizeClass` if you support tablets |

The recursive collapsible tree is the only non-trivial widget; it is a straightforward recursive
composable with hoisted `expanded` state and the §8.5 default-open rule.

## 12. State and ViewModel

`onym-window.c` (656 lines) collapses into one `ViewModel`:

- State: `query`, `completions: List<String>`, `uiState: Welcome | NotFound(word, suggestions) |
  Result(OnymResult)`, `history: List<String>`, `historyPos: Int`, `recents: List<String>`,
  `treeExpansion`.
- Live search: a `MutableStateFlow<String>` of the query → `debounce(250).mapLatest { lookup(it) }`
  for the silent update; completion computed synchronously/immediately on each change. This is cleaner
  than the manual `g_timeout` in C.
- Commit paths (Enter / pick completion / chip / suggestion / recent) call a `commit(word)` that looks
  up, sets the field to the headword, and updates history + recents per §8.3–8.4.
- Recents persist to DataStore; the rest is in-memory.

## 13. Data packaging

Options, pick during the spike:

1. **Bundle in `assets/`** (~15 MB, or ~5–6 MB if compressed and unpacked on first run). Simplest,
   fully offline at install. Most WordNet readers want real file paths, so copy assets to
   `filesDir` on first launch, then point the reader there. Increases install size.
2. **Download-on-first-run** from ursa.nz / the forge. Smaller APK, needs network once, more code and
   a hosting commitment.

Recommendation: bundle (option 1) for a true offline-first thesaurus; revisit only if install size
becomes a complaint. Reuse the exact Debian/Flatpak dict files for parity with the oracle.

## 14. Distribution

Distribution is **not** the Play Store. That removes the target-SDK treadmill, the Play Console
account/review/data-safety/privacy-policy overhead, and AAB/asset-pack machinery (and since the engine
is pure-JVM there are no ABIs — a single universal APK). It also means no store can gatekeep it.

Channels (increasing control):

- **Mainline F-Droid** — builds from source on their infra, signs with their key. Wants FOSS deps (✓
  GPL-3 app, BSD extJWNL, WordNet licence), reproducible-ish builds, `fastlane/metadata/android/...`
  for listing text + screenshots (the same metadata muscle as Onym's AppStream metainfo), no trackers
  (none). Onym is a model citizen here.
- **IzzyOnDroid** — F-Droid-format repo that accepts your own signed APKs; lighter process.
- **Self-hosted F-Droid repo on forge.ursa.nz / ursa.nz** — run `fdroid` (fdroidserver); users add the
  repo URL once and get auto-updates. Full control, your key, your infra — the path most consistent
  with the project's self-hosting setup.

Consequences to plan for: **you own the signing key forever** (no Play App Signing safety net — lose
it and you cannot ship updates); users must allow sideloading (the F-Droid client smooths this);
**minSdk is free**, but Material You dynamic colour needs API 31+, so either set `minSdk 31` and lean
into Material You, or go lower with a static M3 palette fallback below 31.

## 15. Validation strategy

The oracle makes engine parity testable, not guesswork:

1. Build `onym-cli` once (`meson setup ../onym/_build && meson compile -C ../onym/_build`).
2. Take a word list (e.g. every headword from the index files, or a stratified sample across POS:
   nouns, verbs, adjectives incl. satellites, adverbs, multi-word entries, inflected queries).
3. For each word, capture `onym-cli --dump WORD`, `--complete PREFIX`, `--suggest WORD`.
4. Run the Kotlin engine through a matching text dumper (replicate §7's format, or — more robust —
   parse both into the structured model and compare trees structurally).
5. Diff. Drive the diff to zero. Prioritise the known-fiddly cases: indirect antonyms (*beautiful*,
   *fast*, *good*), deep hyponym trees (*animal*, *dog*), holonym/meronym grouping (*hand*, *tree*),
   multi-word lemmas (*ice cream*), and morphology (*ran*, *better*, *mice*).

A JVM unit test can shell out to the prebuilt `onym-cli` and assert equality on a sampled list, so
parity stays a CI gate, not a one-off.

## 16. Milestones and effort

Rough, one experienced Android/Kotlin dev. A scrappy prototype is ~1 week; a polished v1 is ~3–4 weeks.

1. **Spike (1–2 d)** — Compose + M3 + DataStore scaffold; extJWNL reading the bundled dict on device;
   confirm relation coverage maps onto §4.4. De-risks the two unknowns.
2. **Engine (5–7 d)** — model (§3), lookup + relation mapping (§4), completion + suggestion (§5),
   morphology; stand up the oracle diff (§15) and drive mismatches down.
3. **UI (4–6 d)** — search + completion, three states, result renderer (definitions/chips/antonyms),
   the recursive tree with the three expansion modes.
4. **State (2–3 d)** — ViewModel: debounced live search, history, recents (DataStore), commit paths;
   `PROCESS_TEXT` + share entry points.
5. **A11y + polish (3–4 d)** — TalkBack parity (§8.6), dynamic colour, dark/light, edge-to-edge,
   predictive back, adaptive icon.
6. **Distribution (1–2 d)** — signing, fastlane metadata, stand up the F-Droid channel (§14).

The tail most likely to overrun is engine parity (step 2) — but it is bounded by the oracle.

## 17. Risks

- **Exact engine parity** — indirect antonyms, tree construction/dedup, holonym/meronym subtype
  grouping, and morphology edge cases are subtle. Mitigated by the oracle; expect a tail of
  mismatches. *Highest risk; front-load it.*
- **Accessibility parity** — Onym's a11y is unusually thorough; matching it on TalkBack is real work.
- **extJWNL fidelity** — confirm in the spike that its pointer set and Morphy reproduce Artha's output;
  if a relation is missing or grouped differently, you may need a thin custom reader over the raw
  `data.*`/`index.*` files (the formats are simple and documented).
- **Two codebases** — no shared code with the GTK app; engine rules must be changed in both places.
  If that becomes a burden, consider a Kotlin Multiplatform engine core later (overkill for v1).
- **Key custody** — self-distribution means safeguarding the signing key indefinitely.

## 18. Open decisions

1. **minSdk**: 31 (Material You everywhere, simpler) vs lower with a static-palette fallback?
2. **Data**: bundle in assets vs download-on-first-run? (Recommended: bundle.)
3. **WordNet library**: extJWNL (recommended) vs JWI vs a thin custom reader — decide in the spike.
4. **Primary channel**: self-hosted F-Droid repo on the forge vs mainline F-Droid (or both)?
5. **Scope of v1**: ship the full relation set, or start with definitions/synonyms/antonyms/is-a and
   add the deeper trees in a follow-up?
6. **Name / app-id**: `nz.ursa.onymdroid`? Reuse the icon, adapted to an adaptive icon.
7. **KMP** now or never — share an engine core across platforms, or keep two codebases?

---

## Appendix A — Onym repo file index

For the builder: where each piece of the spec lives in `../onym`.

| File | What it gives you |
|---|---|
| `ARCHITECTURE.md` | the three-layer design and boundaries |
| `libonym/onym-result.h` / `onym-result.c` | the public model (§3); tree `label` join at `onym-result.c:304` |
| `libonym/onym-result-private.h` | the model constructors (what the bridge builds) |
| `libonym/onym-lookup.c` | **the relation→section bridge (§4)** — the most important file |
| `libonym/onym-engine.c` / `.h` | engine entry points, data-dir resolution, lazy index |
| `libonym/wn-index.c` / `.h` | index build, completion, suggestion, edit distance (§5) |
| `libonym/engine/` | vendored Artha (`wni.c/.h`) — the only WordNet C caller; replaced wholesale |
| `tools/onym-cli.c` | **the golden oracle** and its exact output format (§7) |
| `src/onym-window.c` / `.ui` | window behaviour: live search, history, recents, completion (§8) |
| `src/onym-result-view.c` | result rendering: definitions, chips, trees, expansion modes (§8.5) |
| `src/onym-application.c` | About, Preferences, command-line entry point |
| `src/onym.css` | chip pill, tree guide line, disclosure styling |
| `data/nz.ursa.Onym.gschema.xml` | persisted settings (§9) |
| `data/nz.ursa.Onym.metainfo.xml.in` | canonical app summary/description and screenshots |
| `data/icons/` | the app icon (SVG) to adapt to an adaptive icon |
| `build-aux/nz.ursa.Onym.yaml` | the Flatpak manifest — shows how WordNet is bundled for parity |
| `REUSE.toml` | how licensing of un-headered and borrowed files is declared |
| `libonym/engine/PROVENANCE.md` | the provenance template for borrowed code |
| `.editorconfig` / `.clang-format` | the formatting baseline |

---

## Appendix B — Conventions

The concrete house rules, taken from `../onym`. This is the difference between capturing a principle
and a contributor being able to follow it. `reuse lint` and a formatter should be CI gates from the
first commit.

### Licensing and REUSE

- **App licence: GPL-3.0-or-later** — carried across from Onym. Keep it.
- **Metadata/listing text uses FSFAP**, as Onym's AppStream metainfo does (the permissive
  Free-Software-Foundation all-permissive licence, for descriptive text — F-Droid/fastlane listing
  copy is the equivalent here). Code stays GPL-3.0-or-later.
- Full licence texts live in a **`LICENSES/`** directory — Onym ships `GPL-3.0-or-later.txt`,
  `FSFAP.txt`, and `GPL-2.0-or-later.txt` (the last for borrowed code).
- **Every file carries an inline SPDX header** (templates below). Files that cannot — binaries,
  generated files, icons, golden fixtures, dotfiles — are declared in **`REUSE.toml`** instead.
- **Borrowed code keeps its own licence, declared in `REUSE.toml` with `precedence = "override"`,
  never by editing the borrowed files** — so the borrow stays byte-identical and verifiable against
  upstream. In Onym this is Artha's `wni.*`: **GPL-2.0-or-later, © 2009–2014 Sundaram Ramaswamy** — a
  *different* licence from the app, which is exactly why it is declared, not assumed. extJWNL (BSD)
  gets the same treatment if ever vendored.
- Copyright string: `2026 ursa.nz <code@ursa.nz>`; display form on the About screen: `© 2026 ursa.nz`.
- Keep the repo **`reuse lint`-clean** as a CI gate — it also smooths F-Droid inclusion (§14).

### SPDX header templates

Kotlin / Java / Gradle KTS:

```kotlin
// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later
```

XML / Markdown / HTML:

```xml
<!--
SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
SPDX-License-Identifier: GPL-3.0-or-later
-->
```

Shell / `.properties` / YAML:

```
# SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
# SPDX-License-Identifier: GPL-3.0-or-later
```

### Borrowed / third-party code (e.g. extJWNL)

Prefer a versioned dependency you never fork (principle §0.2). If a reader must be vendored or patched,
isolate it in one module and add a **`PROVENANCE.md`** beside it, mirroring
`libonym/engine/PROVENANCE.md`: project, upstream mirror, **commit hash**, files taken, copyright,
licence, **modifications (ideally "None")**, why it is isolated, and integration notes. Declare its
licence via the REUSE override, not by editing it.

### Code comments

- A source file opens with the **SPDX header**, then a short **block / KDoc stating the file's
  responsibility** in the architecture — its role, not a changelog or author tag. Onym's model:
  `/* The bridge from the WordNet engine to the public model. It walks the GSList... */`.
- **Public API carries KDoc** with `@param` / `@return` — the analogue of Onym's GTK-Doc comments on
  the library surface.
- **Inline comments explain *why*, not *what***, in full sentences, and name the rejected alternative
  when the choice is non-obvious. The exemplar: *"a GtkRevealer is used rather than a GtkExpander
  because it allocates and propagates its child's height reliably as the chips reflow."*

### Prose and user-facing copy

- **Australian / British English** everywhere — code, comments, and UI strings: `colour`, `behaviour`,
  `licence`, `maximised`, `-ise` endings.
- **Short sentences, plain, no marketing voice.** Say what it does; FOSS-first. (Matches the project's
  documented house style.)
- Carry the **attributions** across verbatim onto the About screen: WordNet (Princeton University),
  engine derived from **Artha / Sundaram Ramaswamy**, and the **Kaurna Country acknowledgement**.

### Commit messages

- **Conventional Commits**: `type(scope): subject`, lowercase, concise and descriptive. Types in use:
  `feat`, `fix`, `docs`, `style`, `chore`, `ci`; scopes are free (`readme`, `packaging`, `ci`,
  `appimage`, …). Example: `fix(packaging): supply the missing build dependencies`.
- **Never add AI / Claude co-authorship trailers** to commits or tags.

### Formatting

- Carry the universal `.editorconfig` rules: UTF-8, LF line endings, final newline, trim trailing
  whitespace (except Markdown).
- Adopt a Kotlin formatter (**ktlint** or **ktfmt**) as a CI gate, mirroring Onym's clang-format
  discipline. Indent: Onym uses 2 spaces project-wide, but Kotlin's official style is 4 — pick one and
  set it in `.editorconfig` (a minor decision; default to Kotlin's 4 unless matching Onym matters
  more).
