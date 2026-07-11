// Plugin versions declared once here (apply false) so each subproject applies
// them without re-declaring versions — avoids the "Kotlin Gradle plugin
// loaded multiple times" warning.
plugins {
    id("com.android.library") version "8.13.0" apply false
    kotlin("jvm") version "2.2.0" apply false
    kotlin("android") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
}
