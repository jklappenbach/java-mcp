package io.javamcp.skill;

import java.util.Optional;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * The Maven coordinate (group, artifact, version) of the jar that owns a skill tree. Read at
 * runtime from {@code META-INF/maven/<g>/<a>/pom.properties}, falling back to the jar manifest
 * (§2.1.3). Composed with a canonical name into a {@link SkillUri}.
 */
public record SkillCoordinate(String group, String artifact, String version) {

    /** Read from a {@code pom.properties} (Maven's {@code groupId}/{@code artifactId}/{@code version}). */
    public static Optional<SkillCoordinate> fromPomProperties(Properties p) {
        return of(p.getProperty("groupId"), p.getProperty("artifactId"), p.getProperty("version"));
    }

    /**
     * Fall back to the jar manifest's {@code Implementation-Vendor-Id} / {@code Implementation-Title}
     * / {@code Implementation-Version}. All three must be present.
     */
    public static Optional<SkillCoordinate> fromManifest(Manifest m) {
        Attributes a = m.getMainAttributes();
        return of(
            a.getValue("Implementation-Vendor-Id"),
            a.getValue("Implementation-Title"),
            a.getValue("Implementation-Version"));
    }

    /** Compose this coordinate with a canonical name into a full {@code skill://} URI. */
    public SkillUri toUri(String canonicalName) {
        return new SkillUri(group, artifact, version, canonicalName);
    }

    private static Optional<SkillCoordinate> of(String group, String artifact, String version) {
        if (isBlank(group) || isBlank(artifact) || isBlank(version)) {
            return Optional.empty();
        }
        return Optional.of(new SkillCoordinate(group, artifact, version));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
