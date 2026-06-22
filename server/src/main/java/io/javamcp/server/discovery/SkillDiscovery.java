package io.javamcp.server.discovery;

import io.javamcp.skill.SkillIndex;
import io.javamcp.skill.SkillMatch;
import io.javamcp.skill.SkillMatcher;
import io.javamcp.skill.SkillRecord;
import io.javamcp.skill.SkillUri;
import io.javamcp.skill.SkillUriException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates skills discovered across the classpath into a single index + fuzzy matcher and serves
 * them via {@code search} / {@code list} / {@code get}. Built once from a fixed set of roots (or
 * the running classpath); read-only and offline.
 */
public final class SkillDiscovery {

    private final SkillIndex index;
    private final SkillMatcher matcher;
    private final List<String> warnings;

    private SkillDiscovery(SkillIndex index, List<String> warnings) {
        this.index = index;
        this.matcher = new SkillMatcher(index);
        this.warnings = warnings;
    }

    /** Build from an explicit list of classpath roots (jars or class directories). */
    public static SkillDiscovery fromRoots(List<Path> roots) {
        ScanResult scan = ClasspathScanner.scan(roots);
        SkillIndex index = SkillIndex.build(scan.sources());
        List<String> warnings = new ArrayList<>(scan.warnings());
        warnings.addAll(index.warnings());
        return new SkillDiscovery(index, List.copyOf(warnings));
    }

    /** Build from the running JVM's classpath ({@code java.class.path}) — the production default. */
    public static SkillDiscovery fromSystemClasspath() {
        String cp = System.getProperty("java.class.path", "");
        List<Path> roots = new ArrayList<>();
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            if (!entry.isBlank()) {
                roots.add(Paths.get(entry));
            }
        }
        return fromRoots(roots);
    }

    /**
     * Build from a class loader. A {@link URLClassLoader} contributes its URLs directly (robust:
     * scans every entry, independent of jar directory entries); otherwise roots are derived from
     * {@code getResources("META-INF/skills/")}.
     */
    public static SkillDiscovery fromClassLoader(ClassLoader cl) throws IOException {
        List<Path> roots = new ArrayList<>();
        if (cl instanceof URLClassLoader u) {
            for (URL url : u.getURLs()) {
                toPath(url).ifPresent(roots::add);
            }
        } else {
            Enumeration<URL> urls = cl.getResources("META-INF/skills/");
            for (URL url : Collections.list(urls)) {
                rootOfSkillsResource(url).ifPresent(roots::add);
            }
        }
        return fromRoots(roots);
    }

    /** Fuzzy (or {@code exact}) search over canonical names and titles. */
    public List<SkillMatch> search(String query, boolean exact) {
        return matcher.match(query, exact);
    }

    /**
     * List skills: everything when {@code scope} is null/blank, otherwise the subtree at
     * {@code scope} (the node plus its descendants), de-duplicated and ordered.
     */
    public List<SkillRecord> list(String scope) {
        if (scope == null || scope.isBlank()) {
            return index.all();
        }
        Map<String, SkillRecord> byUri = new LinkedHashMap<>();
        SkillIndex.HierarchicalResult r = index.query(scope);
        r.self().forEach(rec -> byUri.put(rec.uri().format(), rec));
        r.descendants().forEach(rec -> byUri.put(rec.uri().format(), rec));
        return List.copyOf(byUri.values());
    }

    /** Resolve URIs to payloads; an unparseable or unknown URI reports an error without failing siblings. */
    public List<GetResult> get(List<String> uris) {
        List<GetResult> out = new ArrayList<>(uris.size());
        for (String uri : uris) {
            try {
                SkillUri parsed = SkillUri.parse(uri);
                out.add(index.get(parsed)
                    .map(rec -> GetResult.ok(uri, rec))
                    .orElseGet(() -> GetResult.error(uri, "no skill found for '" + uri + "'")));
            } catch (SkillUriException e) {
                out.add(GetResult.error(uri, e.getMessage()));
            }
        }
        return out;
    }

    public List<String> warnings() {
        return warnings;
    }

    public SkillIndex index() {
        return index;
    }

    public int skillCount() {
        return index.all().size();
    }

    private static java.util.Optional<Path> toPath(URL url) {
        try {
            if ("file".equals(url.getProtocol())) {
                return java.util.Optional.of(Paths.get(url.toURI()));
            }
        } catch (Exception ignored) {
            // fall through
        }
        return java.util.Optional.empty();
    }

    /** From a {@code …/META-INF/skills/} resource URL, recover the owning jar or class-dir root. */
    private static java.util.Optional<Path> rootOfSkillsResource(URL url) {
        try {
            if ("jar".equals(url.getProtocol())) {
                java.net.JarURLConnection conn = (java.net.JarURLConnection) url.openConnection();
                return java.util.Optional.of(Paths.get(conn.getJarFileURL().toURI()));
            }
            if ("file".equals(url.getProtocol())) {
                Path skills = Paths.get(url.toURI());
                // strip the trailing META-INF/skills to reach the class-dir root
                return java.util.Optional.of(skills.getParent().getParent());
            }
        } catch (Exception ignored) {
            // unsupported URL form
        }
        return java.util.Optional.empty();
    }
}
