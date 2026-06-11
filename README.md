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

v0.2. The lexical engine is [onym-engine](https://forge.ursa.nz/ursa-nz/onym-engine), the
shared Rust core Onym also uses, loaded over JNI. Its output answers to the engine's
conformance kit, and a cross-diff of every WordNet headword through the JNI path is
byte-identical to the engine's own CLI.

## Architecture

- **`:core`** — a pure-Kotlin module, with no Android dependencies, holding the immutable
  result model and the JNI front over the shared onym-engine Rust core: a loader with one
  external function per operation, and a decoder that rebuilds the model from the engine's
  serialised buffer.
- **`:app`** — a Jetpack Compose (Material 3 and Material You) front end and its
  ViewModel. The UI is adaptive: a list-detail layout that shows a single pane on a phone
  and a search pane beside the word detail on a tablet or unfolded foldable. It draws edge
  to edge, follows the system light/dark setting with dynamic colour, and debounces live
  search through the ViewModel, which also owns the back/forward word history.

Dependencies point strictly inward: the UI never sees a WordNet type, and the engine
never sees an Android one.

## Building

Requires the Android SDK (platform 36, build-tools 36.1.0) and JDK 21.

```sh
./gradlew :app:assembleDebug
```

Debug builds are not optimised — `debuggable` disables ART's ahead-of-time compilation —
so scrolling feels laggy. The release build is the representative one.

### Releasing

The release build is minified and resource-shrunk with R8. The lookup engine is the shared
onym-engine Rust core, built by cargo from the sibling checkout for each packaged ABI and
loaded over JNI, so a build needs rustup (with the Android targets) and an NDK; the build
also packages the Compose libraries' baseline profiles, so it scrolls smoothly. Release signing is read from a `keystore.properties` at the
repository root, which is never committed:

```properties
storeFile=onym-release.jks
storePassword=…
keyAlias=onym
keyPassword=…
```

Without that file the release falls back to the debug key, so a fresh checkout still builds.

```sh
./gradlew :app:assembleRelease    # app/build/outputs/apk/release/app-release.apk
```

That signed APK is what the [software.ursa.nz](https://software.ursa.nz/fdroid/repo) F-Droid
repository serves; F-Droid distributes it as-is, so the app-signing key must stay constant
across releases.

## Out of scope

These are deliberately absent, not stubbed:

- IPA pronunciation (WordNet carries no phonetics) and any audio/text-to-speech
- A manual light/dark theme toggle — the app follows the system setting and Material You
  dynamic colour instead
- Text-selection lookup (the `PROCESS_TEXT` action) and a share target
- A home-screen search widget
- Download-on-first-run (the database is bundled instead)
- Kotlin Multiplatform
- A custom, app-specific baseline profile — the release packages the Compose libraries'
  own baseline profiles, which is enough for smooth scrolling
- In-repository continuous integration — release APKs are built and signed locally and
  published through the separate `software.ursa.nz` deploy repository

## Attribution

- Word data from [WordNet](https://wordnet.princeton.edu), the lexical database from
  Princeton University.
- The lookup engine is [onym-engine](https://forge.ursa.nz/ursa-nz/onym-engine),
  GPL-3.0-or-later, whose behaviour derives from [Artha](https://github.com/sria91/artha),
  an earlier WordNet thesaurus by Sundaram Ramaswamy; the derivation is recorded in that
  repository's PROVENANCE.md.
- The interface uses the [Lora](https://github.com/cyrealtype/Lora-Cyrillic) serif and the
  [Roboto Flex](https://github.com/googlefonts/roboto-flex) body face, both from the Google
  Fonts project under the SIL Open Font Licence. They are bundled, not fetched at runtime, so
  the app needs no Google Play Services.
- Built in Narrm on Woiwurrung, Boonwurrung Country, with respect to the Wurundjeri and
  Bunurong peoples, their languages, and their continuing connection to this Country.

## Licence

GPL-3.0-or-later. Full licence texts live in [LICENSES/](LICENSES/), and the repository is
[REUSE](https://reuse.software)-compliant.
