pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Lets the Java toolchain auto-resolve/provision a JDK 21 on machines that
    // don't have one (locally it discovers the sdkman-installed 21).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "java-mcp"

include("skill-core", "server", "examples")
