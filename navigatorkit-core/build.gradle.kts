plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
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
