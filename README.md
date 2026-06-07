<!--
SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Onymdroid

An Android port of [Onym](https://forge.ursa.nz/ursa-nz/Onym), the GTK4 and libadwaita
WordNet thesaurus. Look a word up and Onymdroid shows its meanings, synonyms, antonyms,
and the lexical relations that connect it to other words — what it is a kind of, its
kinds, and the wholes and parts it belongs to — each as a tree you can open. Every
synonym, antonym, and term in a relation tree is clickable, so one lookup leads to the
next.

Onymdroid is offline-first: the WordNet database is bundled in the app, so there is no
network, no accounts, and no background work.

## Status

In development, working towards v0.1. The lexical engine is a Kotlin reimplementation of
Onym's lookup logic (itself derived from Artha), checked for output parity against the
upstream `onym-cli` golden oracle.

## Architecture

- **`:core`** — a pure-Kotlin module, with no Android dependencies, holding the immutable
  result model, the lookup and relation-mapping logic, completion and suggestion, and the
  `WordNetSource` adapter behind which the WordNet reader is sealed.
- **`:app`** — a Jetpack Compose (Material 3 and Material You) front end and its
  ViewModel.

Dependencies point strictly inward: the UI never sees a WordNet type, and the engine
never sees an Android one.

## Building

Requires the Android SDK (platform 36, build-tools 36.1.0) and JDK 21.

```sh
./gradlew :app:assembleDebug
```

## Out of scope for v0.1

These are deliberately absent, not stubbed:

- Text-selection lookup (the `PROCESS_TEXT` action) and a share target
- A home-screen search widget
- Download-on-first-run (the database is bundled instead)
- Tablet and adaptive layouts
- Kotlin Multiplatform
- Distribution automation

## Attribution

- Word data from [WordNet](https://wordnet.princeton.edu), the lexical database from
  Princeton University.
- The lookup logic is derived from [Artha](https://github.com/sria91/artha), an earlier
  WordNet thesaurus by Sundaram Ramaswamy.
- Crafted on Kaurna Pangkarra, in Australia, with respect to the Kaurna people, their
  language, and their continuing connection to this Country.

## Licence

GPL-3.0-or-later. Full licence texts live in [LICENSES/](LICENSES/), and the repository is
[REUSE](https://reuse.software)-compliant.
