import com.lightningkite.deployhelpers.developer
import com.lightningkite.deployhelpers.github
import com.lightningkite.deployhelpers.mit
import com.lightningkite.deployhelpers.standardPublishing

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    signing
    `maven-publish`
    id("org.jetbrains.dokka") version "1.9.20"
}

buildscript {
    val kotlinVersion:String by extra
    repositories {
        mavenLocal()
//        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven(url = "https://s01.oss.sonatype.org/content/repositories/releases/")
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        classpath("com.lightningkite:deploy-helpers:0.0.7")
        classpath("com.android.tools.build:gradle:8.2.2")
    }
}

group = "com.lightningkite"

gradlePlugin {
    plugins {
        create("lightningkite-packagemover") {
            id = "com.lightningkite.packagemover"
            implementationClass = "com.lightningkite.packagemover.PackageMoverPlugin"
        }
    }
}

repositories {
    mavenCentral()
}
dependencies {
    testImplementation(kotlin("test"))
}
tasks.validatePlugins {
    enableStricterValidation.set(true)
}

standardPublishing {
    name.set("Package-Mover")
    description.set("Move elements from package to package with an automatic migration.")
    github("lightningkite", "package-mover")

    licenses {
        mit()
    }

    developers {
        developer(
            id = "LightningKiteJoseph",
            name = "Joseph Ivie",
            email = "joseph@lightningkite.com",
        )
    }
}

afterEvaluate {
    tasks.findByName("signPluginMavenPublication")?.let { signingTask ->
        tasks.filter { it.name.startsWith("publish") && it.name.contains("PluginMarkerMavenPublication") }.forEach {
            it.dependsOn(signingTask)
        }
    }
    tasks.findByName("signLightningkite-packagemoverPluginMarkerMavenPublication")?.let { signingTask ->
        tasks.findByName("publishPluginMavenPublicationToMavenLocal")?.dependsOn(signingTask)
        tasks.findByName("publishPluginMavenPublicationToSonatypeRepository")?.dependsOn(signingTask)
    }
}
