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
    implementation(platform("io.micronaut.platform:micronaut-platform:${libs.versions.micronaut.get()}"))
    implementation(project(":skill-core"))
    implementation(libs.jackson.databind)
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.aws:micronaut-function-aws-api-proxy")
    testImplementation("io.micronaut:micronaut-http-client")
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
    // The end-to-end test discovers skills out of the built example library jar.
    dependsOn(":examples:jar")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    mergeServiceFiles()
}
