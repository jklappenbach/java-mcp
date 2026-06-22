package io.javamcp.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * An aggregate index over skills discovered across the classpath. The hierarchy is derived from
 * the <em>tree</em> (a node's children are the names exactly one path segment deeper) — the
 * authored {@code inventory} is only cross-checked for drift, never trusted as the source of
 * truth. Nothing is read from or written to a shipped index file (§3.3.1); the index is built at
 * runtime from {@link SkillSource}s.
 */
public final class SkillIndex {

    /** Result of a hierarchical query: the node, its ancestor overviews, and its descendants. */
    public record HierarchicalResult(
        List<SkillRecord> self, List<SkillRecord> ancestors, List<SkillRecord> descendants) {}

    /** Order records by canonical name, then version, then full URI (deterministic). */
    private static final Comparator<SkillRecord> BY_NAME_THEN_VERSION =
        Comparator.comparing(SkillRecord::name)
            .thenComparing(r -> r.uri().version())
            .thenComparing(r -> r.uri().format());

    private final List<SkillRecord> all;
    private final Map<String, List<SkillRecord>> byName;
    private final Map<String, SkillRecord> byUri;
    private final List<String> warnings;

    private SkillIndex(
        List<SkillRecord> all,
        Map<String, List<SkillRecord>> byName,
        Map<String, SkillRecord> byUri,
        List<String> warnings) {
        this.all = all;
        this.byName = byName;
        this.byUri = byUri;
        this.warnings = warnings;
    }

    public static SkillIndex build(List<SkillSource> sources) {
        List<SkillRecord> all = new ArrayList<>(sources.size());
        Map<String, List<SkillRecord>> byName = new TreeMap<>();
        Map<String, SkillRecord> byUri = new LinkedHashMap<>();
        for (SkillSource s : sources) {
            SkillRecord r = s.toRecord();
            all.add(r);
            byName.computeIfAbsent(r.name(), k -> new ArrayList<>()).add(r);
            byUri.put(r.uri().format(), r);
        }
        all.sort(BY_NAME_THEN_VERSION);
        byName.values().forEach(list -> list.sort(BY_NAME_THEN_VERSION));

        List<String> warnings = detectDrift(sources);
        return new SkillIndex(List.copyOf(all), byName, byUri, List.copyOf(warnings));
    }

    /** All indexed records, sorted by name then version. */
    public List<SkillRecord> all() {
        return all;
    }

    /** Records bound to an exact canonical name (every version), sorted; empty if none. */
    public List<SkillRecord> byName(String name) {
        return byName.getOrDefault(name, List.of());
    }

    /** The record for a full URI, if present. */
    public Optional<SkillRecord> get(SkillUri uri) {
        return Optional.ofNullable(byUri.get(uri.format()));
    }

    /** Inventory-drift warnings collected at build time (empty when every node is consistent). */
    public List<String> warnings() {
        return warnings;
    }

    /**
     * Hierarchical query: the node(s) named {@code name}, their descendants (prefix-inclusive), and
     * their ancestor overview skills (nearest first). Descendants/ancestors aggregate across every
     * library version on the classpath.
     */
    public HierarchicalResult query(String name) {
        List<SkillRecord> self = new ArrayList<>(byName(name));

        String prefix = name + "/";
        List<SkillRecord> descendants =
            all.stream().filter(r -> r.name().startsWith(prefix)).sorted(BY_NAME_THEN_VERSION).toList();

        List<SkillRecord> ancestors = new ArrayList<>();
        for (String anc : ancestorNames(name)) {
            ancestors.addAll(byName(anc)); // nearest → farthest
        }

        return new HierarchicalResult(List.copyOf(self), List.copyOf(ancestors), descendants);
    }

    /** Names of every ancestor of {@code name}, nearest first (e.g. a/b/c → [a/b, a]). */
    private static List<String> ancestorNames(String name) {
        List<String> out = new ArrayList<>();
        int slash = name.lastIndexOf('/');
        while (slash >= 0) {
            name = name.substring(0, slash);
            out.add(name);
            slash = name.lastIndexOf('/');
        }
        return out;
    }

    /**
     * Cross-check each node's declared {@code inventory} against the children actually present in
     * its tree (within the same library coordinate). Drift in either direction warns; the tree is
     * authoritative regardless.
     */
    private static List<String> detectDrift(List<SkillSource> sources) {
        // Names present per coordinate, so versions don't cross-pollute child sets.
        Map<String, Set<String>> namesByCoord = new LinkedHashMap<>();
        for (SkillSource s : sources) {
            namesByCoord
                .computeIfAbsent(coordKey(s.coordinate()), k -> new LinkedHashSet<>())
                .add(s.name().canonical());
        }

        List<String> warnings = new ArrayList<>();
        for (SkillSource s : sources) {
            String node = s.name().canonical();
            Set<String> siblings = namesByCoord.get(coordKey(s.coordinate()));

            Set<String> actualChildren = new LinkedHashSet<>();
            String childPrefix = node + "/";
            for (String other : siblings) {
                if (other.startsWith(childPrefix) && other.indexOf('/', childPrefix.length()) < 0) {
                    actualChildren.add(other);
                }
            }

            Set<String> declared = new LinkedHashSet<>();
            for (SkillDoc.InventoryEntry e : s.doc().inventory()) {
                declared.add(e.name());
            }

            for (String child : actualChildren) {
                if (!declared.contains(child)) {
                    warnings.add("inventory drift: node '" + node + "' omits child '" + child + "'");
                }
            }
            for (String d : declared) {
                if (!actualChildren.contains(d)) {
                    warnings.add(
                        "inventory drift: node '" + node + "' lists '" + d + "' which is not in the tree");
                }
            }
        }
        return warnings;
    }

    private static String coordKey(SkillCoordinate c) {
        return c.group() + ":" + c.artifact() + "@" + c.version();
    }
}
