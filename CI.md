<!--
SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Continuous integration

CI runs on the self-hosted Forgejo Actions runner on atutahi (docker mode), defined under
`.forgejo/workflows/`. The build gate is mirrored to GitHub Actions under `.github/workflows/`;
release signing stays on Forgejo alone, where the key is held.

## Workflows

| File | Trigger | Does |
|------|---------|------|
| `gate.yml` | PR, push to `main` | Cross-compiles onym-engine for the three ABIs, runs ktlint + `:core` tests, assembles a debug APK. No secrets. Mirrored on GitHub. |
| `release.yml` | tag `v*`, manual dispatch | Builds a release-signed APK with the onym app key and attaches it to the tag's Forgejo release. Forgejo only. |

Both jobs pin `container.image: catthehacker/ubuntu:act-22.04`, then install JDK 21, the Android
SDK/NDK, and Rust on top, leaning on `actions/cache` so warm runs skip the toolchain installs. They
check out the `onym-data` submodule, whose `prepare.sh` bundles the WordNet data into the APK.

## The onym-engine dependency

The release APK loads the [onym-engine](https://forge.ursa.nz/ursa-nz/onym-engine) Rust core
over JNI, so each run clones it (`ENGINE_REF`, currently `main` = latest) into the workspace and
points the build at it with `-Donym.engine.dir`. The resolved engine commit is written to the
job summary. To freeze a release against a specific engine commit, set `ENGINE_REF` to that tag
or SHA in `release.yml`.

## Secrets

Set on the `ursa-nz/Onymdroid` repo (Settings → Actions → Secrets). Only the **app** signing
key lives in CI; the F-Droid index key does not.

| Secret | Value |
|--------|-------|
| `ONYM_RELEASE_JKS_B64` | `base64 -w0 onym-release.jks` |
| `ONYM_STORE_PASSWORD` | keystore password (alias `onym`) |
| `ONYM_KEY_PASSWORD` | key password |

Source of truth: `signing-keys/onymdroid-app/` in the development-ursa vault. The keystore must
stay constant across releases — F-Droid distributes the APK as-is.

## Publishing to F-Droid (manual)

CI stops at the signed APK so the live repo keeps a human gate. To publish a release:

```sh
# fetch the APK attached to the tag's release
curl -LO https://forge.ursa.nz/ursa-nz/Onymdroid/releases/download/vX.Y.Z/onymdroid-vX.Y.Z.apk

# on atutahi, where the repo and the index key live
cp onymdroid-vX.Y.Z.apk /opt/fdroid/repo/
cd /opt/fdroid && fdroid update          # signs the index with the ursa repo key
```

It is then live at <https://software.ursa.nz/fdroid/repo>.
