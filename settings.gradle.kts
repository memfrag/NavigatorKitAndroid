pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "navigatorkit-android"

include(":navigatorkit-core")
include(":navigatorkit-compose")
include(":sample-app")
