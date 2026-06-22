plugins {
    `java-library`
}

// A larger, realistic sample library (com.example:notes) with several packages,
// classes, and methods — and a top-down skill tree under
// src/main/resources/META-INF/skills/. The jar carries its coordinate in the
// manifest (the path the discovery scanner falls back to without a Maven pom).
group = "com.example"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Vendor-Id" to "com.example",
            "Implementation-Title" to "notes",
            "Implementation-Version" to "1.0.0",
        )
    }
}
