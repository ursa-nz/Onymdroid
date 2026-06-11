// SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
// SPDX-License-Identifier: GPL-3.0-or-later

import java.util.Properties
import javax.inject.Inject

// The Android application: a Jetpack Compose (Material 3 / Material You) front end over the
// pure-Kotlin :core engine. Dependencies point inward — this module may see :core, but
// :core never sees Android.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

// Release signing is read from an untracked keystore.properties (see README); without it, release
// builds fall back to the debug key so the project still builds and runs on a fresh checkout.
val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
val hasReleaseKeystore = rootProject.file("keystore.properties").exists()

// The lookup engine is the shared onym-engine Rust core, crossing in as a JNI library: one
// cargo-built .so per ABI, gathered into a generated jniLibs directory that the packaging
// merges like any other source. The checkout is the sibling directory by default, the same
// convention :core's host build and the conformance kit use; -Donym.engine.dir overrides it.
val onymEngineDir =
    File(
        providers.systemProperty("onym.engine.dir").getOrElse(
            rootProject.layout.projectDirectory
                .dir("../onym-engine")
                .asFile.absolutePath,
        ),
    )
val rustJniLibs = layout.buildDirectory.dir("rustJniLibs")

// One Rust target per packaged ABI: the jniLibs directory, the Rust triple, and the NDK clang
// wrapper. The wrapper's suffix is the library's target API level; NDK r28 tops out at 35,
// which is safely below minSdk, since a library may always target an older level than the app.
val rustAbis =
    listOf(
        Triple("arm64-v8a", "aarch64-linux-android", "aarch64-linux-android35-clang"),
        Triple("armeabi-v7a", "armv7-linux-androideabi", "armv7a-linux-androideabi35-clang"),
        Triple("x86_64", "x86_64-linux-android", "x86_64-linux-android35-clang"),
    )

// AGP resolves its own NDK for stripping; this lookup is only for cargo's cross linkers. The
// newest side-by-side install wins, and ANDROID_NDK_HOME overrides.
fun ndkRoot(): File {
    System.getenv("ANDROID_NDK_HOME")?.let { return File(it) }
    val sdk =
        File(
            Properties()
                .apply {
                    val file = rootProject.file("local.properties")
                    if (file.exists()) file.inputStream().use { load(it) }
                }.getProperty("sdk.dir")
                ?: System.getenv("ANDROID_HOME")
                ?: "${System.getProperty("user.home")}/Android/Sdk",
        )
    return File(sdk, "ndk").listFiles()?.maxByOrNull { it.name }
        ?: error("no NDK under $sdk/ndk; install one with sdkmanager")
}

// rustup is user-local and the Gradle daemon's PATH may not carry it, so prefer
// ~/.cargo/bin/cargo; running in the engine checkout honours its rust-toolchain.toml pin, and
// cargo's own change tracking makes re-runs cheap.
val cargoJniAndroid =
    rustAbis.map { (abiDir, triple, clang) ->
        val taskName = "cargoJni" + abiDir.split("-", "_").joinToString("") { it.replaceFirstChar(Char::uppercase) }
        tasks.register<Exec>(taskName) {
            description = "Build the onym-engine JNI library for $abiDir."
            group = "build"
            workingDir = onymEngineDir
            val cargo = File(System.getProperty("user.home"), ".cargo/bin/cargo")
            commandLine(
                if (cargo.canExecute()) cargo.absolutePath else "cargo",
                "build",
                "--release",
                "--locked",
                "-p",
                "onym-engine-jni",
                "--target",
                triple,
            )
            doFirst {
                val linker = ndkRoot().resolve("toolchains/llvm/prebuilt/linux-x86_64/bin/" + clang)
                environment("CARGO_TARGET_" + triple.uppercase().replace('-', '_') + "_LINKER", linker.absolutePath)
            }
        }
    }

// Gathers the cargo-built libraries into one directory, an ABI subdirectory each. A custom
// task rather than Copy because AGP's variant API wires generated jniLibs through a task
// output declared as a DirectoryProperty, which Copy does not expose.
abstract class PackRustJniLibsTask : DefaultTask() {
    @get:Inject
    abstract val fs: FileSystemOperations

    @get:InputFiles
    abstract val libraries: ConfigurableFileCollection

    @get:Input
    abstract val abiDirs: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun pack() {
        val libs = libraries.files.toList()
        abiDirs.get().forEachIndexed { i, abi ->
            fs.copy {
                from(libs[i])
                into(outputDir.dir(abi))
            }
        }
    }
}

val packRustJniLibs =
    tasks.register<PackRustJniLibsTask>("packRustJniLibs") {
        description = "Gather the cargo-built JNI libraries into the packaged layout."
        group = "build"
        dependsOn(cargoJniAndroid)
        abiDirs.set(rustAbis.map { it.first })
        libraries.from(
            rustAbis.map { (_, triple, _) ->
                File(
                    onymEngineDir,
                    "target/" + triple + "/release/libonym_engine_jni.so",
                )
            },
        )
        outputDir.set(rustJniLibs)
    }

android {
    namespace = "nz.ursa.onymdroid"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "nz.ursa.onymdroid"
        minSdk = 36
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0"

        ndk {
            // Package exactly the ABIs the engine is built for; AndroidX would otherwise add a
            // stray x86 directory the app cannot actually run from.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        create("release") {
            keystoreProperties.getProperty("storeFile")?.let { storeFile = rootProject.file(it) }
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig =
                if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
}

// AGP 9's variant API: the generated jniLibs directory rides in through the task that fills
// it, which also carries the task dependency; AGP redirects the copy's destination to its own
// generated-sources location.
androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(packRustJniLibs, PackRustJniLibsTask::outputDir)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ktlint {
    version.set(libs.versions.ktlint.get())
    android.set(true)
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
