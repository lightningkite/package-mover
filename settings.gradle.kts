rootProject.name = "package-mover"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
    }
    val kotlinVersion: String by settings

    plugins {
        kotlin("plugin.serialization") version kotlinVersion
    }
}
