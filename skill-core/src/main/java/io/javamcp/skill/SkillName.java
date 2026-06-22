package io.javamcp.skill;

import java.util.List;
import java.util.Optional;

/**
 * A skill's canonical name — its path under {@code META-INF/skills/}, with the {@code .md} suffix
 * removed and separators normalized to {@code /}. The path <em>is</em> the identity (§2.1.3); the
 * coordinate (group/artifact/version) comes from the owning jar and is composed into a
 * {@link SkillUri} via {@link SkillCoordinate#toUri}.
 */
public record SkillName(String canonical) {

    private static final String PREFIX = "META-INF/skills/";
    private static final String SUFFIX = ".md";

    /**
     * Derive the canonical name from a classpath resource path. Returns empty (ignore, never an
     * error) for a path outside {@code META-INF/skills/}, a non-{@code .md} entry, or a directory.
     */
    public static Optional<SkillName> fromResourcePath(String path) {
        if (path == null) {
            return Optional.empty();
        }
        String p = path.replace('\\', '/');
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (!p.startsWith(PREFIX) || !p.endsWith(SUFFIX)) {
            return Optional.empty();
        }
        String canonical = p.substring(PREFIX.length(), p.length() - SUFFIX.length());
        if (canonical.isEmpty() || canonical.endsWith("/")) {
            return Optional.empty();
        }
        return Optional.of(new SkillName(canonical));
    }

    /** The {@code /}-separated segments (library, package(s), class, method). */
    public List<String> segments() {
        return List.of(canonical.split("/"));
    }

    /** The last path segment — the node's own name (used as the default skill title). */
    public String leaf() {
        int slash = canonical.lastIndexOf('/');
        return slash < 0 ? canonical : canonical.substring(slash + 1);
    }
}
