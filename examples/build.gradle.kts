plugins {
    `java-library`
}

// A self-contained sample library whose skill tree lives under
// src/main/resources/META-INF/skills/. The ordinary jar ships it; the coordinate
// travels in the manifest (the Gradle-idiomatic path the discovery scanner falls
// back to when there is no Maven pom.properties).
group = "com.acme"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Vendor-Id" to "com.acme",
            "Implementation-Title" to "widgets",
            "Implementation-Version" to "1.0.0",
        )
    }
}
