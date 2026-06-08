// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

// AGP 9 provides Kotlin support built in. Declaring the Kotlin Gradle plugin here (apply
// false) puts the chosen Kotlin version on the build classpath so the Android module's
// built-in Kotlin and the Compose compiler plugin all agree on one version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktlint) apply false
}
