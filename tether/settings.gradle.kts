pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Versions live here so each plugin is only downloaded by the module that applies it.
    plugins {
        id("com.android.application") version "8.9.1"
        id("org.jetbrains.kotlin.android") version "2.1.20"
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
        id("org.jetbrains.kotlin.jvm") version "2.1.20"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "miroir-tether"
include(":core")
// The Android app needs the Android SDK; set -PcoreOnly to build/test just the protocol code.
if (!providers.gradleProperty("coreOnly").isPresent) include(":app")
