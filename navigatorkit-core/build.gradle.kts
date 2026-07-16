plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    // Declares the module's identity, which is what lets a consumer depend on
    // it straight from git via settings.gradle.kts `sourceControl` — Gradle
    // only matches a coordinate to a repository if the build says it produces
    // that coordinate. Nothing is published anywhere by applying this.
    `maven-publish`
}

group = "io.github.memfrag.navigatorkit"
version = "0.1.0"

dependencies {
    // JetBrains' multiplatform Compose runtime: gives the state tree
    // snapshot-backed observability (mutableStateOf / SnapshotStateList)
    // while keeping this module pure JVM — tests run without an emulator.
    api("org.jetbrains.compose.runtime:runtime:1.8.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.test {
    useJUnitPlatform()
}
