# SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
# SPDX-License-Identifier: GPL-3.0-or-later

# The engine crosses the JNI boundary through NativeEngine's native methods, which the default
# Android rules already keep by name; everything else is plain Kotlin that R8 may optimise
# freely, so this file currently adds nothing.
