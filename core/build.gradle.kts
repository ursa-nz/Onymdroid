// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

// The pure-Kotlin engine: result model, lookup and relation mapping, completion and
// suggestion, and the WordNetSource adapter. It has no Android dependency, so the boundary
// is enforced by the build, not just by discipline.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // extJWNL is the WordNet reader, sealed behind the WordNetSource adapter; nothing outside
    // the adapter implementation may import it.
    implementation(libs.extjwnl)

    testImplementation(libs.junit)
}

// Regenerates the onym-engine conformance fixtures from this engine, the reference
// implementation until the Rust core takes over.
tasks.register<JavaExec>("generateFixtures") {
    description = "Regenerate the onym-engine conformance fixtures from this engine."
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("nz.ursa.onymdroid.core.FixtureGenKt")
    systemProperty("onym.wordnet.dir", providers.systemProperty("onym.wordnet.dir").getOrElse("/usr/share/wordnet"))
}

tasks.withType<Test>().configureEach {
    useJUnit()
    // Point the reader at a WordNet database for tests; defaults to the system install.
    systemProperty("onym.wordnet.dir", providers.systemProperty("onym.wordnet.dir").getOrElse("/usr/share/wordnet"))
    // The onym-engine conformance kit, the golden oracle; a sibling checkout by default, and the
    // parity tests skip if it is absent.
    systemProperty(
        "onym.conformance",
        providers
            .systemProperty("onym.conformance")
            .getOrElse(
                rootProject.layout.projectDirectory
                    .dir("../onym-engine/conformance")
                    .asFile.absolutePath,
            ),
    )
}
