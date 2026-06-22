plugins {
    alias(libs.plugins.micronaut.application)
    alias(libs.plugins.shadow)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":skill-core"))
    implementation(libs.jackson.databind)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

micronaut {
    version(libs.versions.micronaut.get())
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("io.javamcp.*")
    }
}

application {
    mainClass = "io.javamcp.server.Application"
}

tasks.test {
    useJUnitPlatform()
}
