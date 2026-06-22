package io.javamcp.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * A parsed skill document: front-matter Markdown (a small YAML header plus a verbatim body).
 *
 * <p>The model carries no {@code id} and no required {@code applies-to} — a skill's identity is
 * its path under {@code META-INF/skills/} (resolved in unit 2). The header may declare a
 * {@code title}, {@code description}, an {@code inventory} of direct children (non-leaf nodes),
 * and {@code references} to related skills (leaf nodes). All are optional; when {@code title} is
 * absent it defaults to the file's leaf name.
 */
public record SkillDoc(
    String title,
    String description,
    List<InventoryEntry> inventory,
    List<String> references,
    String body) {

    /** One direct child listed in a non-leaf node's inventory: canonical name + title. */
    public record InventoryEntry(String name, String title) {}

    /**
     * Parse front-matter Markdown into a {@code SkillDoc}.
     *
     * @param source       the full document text
     * @param sourceName   a path or logical name, used only in error messages
     * @param defaultTitle the title to use when the header declares none (the file's leaf name)
     * @throws SkillParseException on malformed YAML, a non-mapping header, a wrong-typed field, or
     *     an unterminated front-matter fence — always naming {@code sourceName}.
     */
    public static SkillDoc parse(String source, String sourceName, String defaultTitle) {
        FrontMatter fm = FrontMatter.split(source, sourceName);

        Map<?, ?> header = fm.present() ? loadMapping(fm.header(), sourceName) : Map.of();

        String title = optString(header, "title", sourceName);
        if (title == null || title.isBlank()) {
            title = defaultTitle;
        }
        String description = optString(header, "description", sourceName);
        List<InventoryEntry> inventory = parseInventory(header.get("inventory"), sourceName);
        List<String> references = parseReferences(header.get("references"), sourceName);

        return new SkillDoc(title, description, inventory, references, fm.body());
    }

    private static Map<?, ?> loadMapping(String yaml, String sourceName) {
        Object loaded;
        try {
            loaded = new Yaml().load(yaml);
        } catch (RuntimeException e) {
            throw new SkillParseException(
                sourceName + ": malformed YAML front-matter: " + e.getMessage(), e);
        }
        if (loaded == null) {
            return Map.of();
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new SkillParseException(sourceName + ": front-matter is not a YAML mapping");
        }
        return map;
    }

    private static String optString(Map<?, ?> header, String field, String sourceName) {
        Object v = header.get(field);
        if (v == null) {
            return null;
        }
        if (!(v instanceof String s)) {
            throw new SkillParseException(
                sourceName + ": field '" + field + "' must be a string");
        }
        return s;
    }

    private static List<InventoryEntry> parseInventory(Object raw, String sourceName) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new SkillParseException(sourceName + ": field 'inventory' must be a list");
        }
        List<InventoryEntry> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> entry)) {
                throw new SkillParseException(
                    sourceName + ": each 'inventory' entry must be a mapping with 'name' and 'title'");
            }
            String name = requireString(entry.get("name"), "inventory[].name", sourceName);
            String title = requireString(entry.get("title"), "inventory[].title", sourceName);
            out.add(new InventoryEntry(name, title));
        }
        return List.copyOf(out);
    }

    private static List<String> parseReferences(Object raw, String sourceName) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new SkillParseException(sourceName + ": field 'references' must be a list");
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            out.add(requireString(item, "references[]", sourceName));
        }
        return List.copyOf(out);
    }

    private static String requireString(Object v, String field, String sourceName) {
        if (!(v instanceof String s)) {
            throw new SkillParseException(
                sourceName + ": field '" + field + "' must be a string");
        }
        return s;
    }
}
