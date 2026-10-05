plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    // See navigatorkit-core: this is what makes the module resolvable from git.
    `maven-publish`
}

group = "io.github.memfrag.navigatorkit"
version = "1.0.0"

android {
    namespace = "io.github.memfrag.navigatorkit.compose"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    api(project(":navigatorkit-core"))

    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.12.4")

    // Navigation 3 renders each context's back stack (RoutedStack). 1.1.x is the
    // last line that builds against compileSdk 36 / AGP 8.x; 1.2 needs 37 / AGP 9.1.
    implementation("androidx.navigation3:navigation3-runtime:1.1.7")
    implementation("androidx.navigation3:navigation3-ui:1.1.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0")

    testImplementation("junit:junit:4.13.2")
}
