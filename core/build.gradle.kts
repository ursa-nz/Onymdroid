// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

// The engine module: the result model and the JNI front over the shared onym-engine Rust
// core. It has no Android dependency, so the boundary is enforced by the build, not just by
// discipline.
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
    testImplementation(libs.junit)
}

// The onym-engine checkout, the same sibling convention the conformance kit default uses.
val onymEngineDir = File(
    providers.systemProperty("onym.engine.dir").getOrElse(
        rootProject.layout.projectDirectory.dir("../onym-engine").asFile.absolutePath,
    ),
)

// Build the JNI library from the engine checkout, so tests and tools load the same native core
// the app ships. rustup is user-local and the Gradle daemon's PATH may not carry it, so the
// task prefers ~/.cargo/bin/cargo; running in the checkout honours its rust-toolchain.toml pin.
// The task always runs and cargo's own change tracking makes a clean re-run cheap.
val cargoJniHost = tasks.register<Exec>("cargoJniHost") {
    description = "Build the onym-engine JNI library for the host."
    group = "build"
    workingDir = onymEngineDir
    val cargo = File(System.getProperty("user.home"), ".cargo/bin/cargo")
    commandLine(
        if (cargo.canExecute()) cargo.absolutePath else "cargo",
        "build",
        "--release",
        "-p",
        "onym-engine-jni",
    )
}

// Regenerates the onym-engine conformance fixtures through the JNI front, one of the two
// routes into the shared Rust core (conformance/gen-fixtures is the other).
tasks.register<JavaExec>("generateFixtures") {
    description = "Regenerate the onym-engine conformance fixtures from this engine."
    group = "verification"
    dependsOn(cargoJniHost)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("nz.ursa.onymdroid.core.FixtureGenKt")
    systemProperty("onym.wordnet.dir", providers.systemProperty("onym.wordnet.dir").getOrElse("/usr/share/wordnet"))
    systemProperty("java.library.path", File(onymEngineDir, "target/release").absolutePath)
}

// Streams dumps for a word list through this engine, the JNI side of the onym-engine total
// cross-diff against onym-dump.
tasks.register<JavaExec>("batchDump") {
    description = "Dump every word of a list for the onym-engine cross-diff."
    group = "verification"
    dependsOn(cargoJniHost)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("nz.ursa.onymdroid.core.BatchDumpKt")
    systemProperty("onym.wordnet.dir", providers.systemProperty("onym.wordnet.dir").getOrElse("/usr/share/wordnet"))
    systemProperty("java.library.path", File(onymEngineDir, "target/release").absolutePath)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    // The native engine library, built fresh so the parity suite answers for the real core.
    dependsOn(cargoJniHost)
    systemProperty("java.library.path", File(onymEngineDir, "target/release").absolutePath)
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
