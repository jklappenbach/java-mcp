package io.javamcp.server.authoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javamcp.skill.SkillDoc;
import io.javamcp.skill.SkillName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit 9 — the authoring skill's examples parse, and the marketplace manifests validate (plan §9.1.1, §9.1.2). */
class AuthoringSkillTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("plugin-marketplace/.claude-plugin/marketplace.json"))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "could not locate plugin-marketplace from " + Paths.get("").toAbsolutePath());
        return dir;
    }

    // 9.1.2 — marketplace.json + plugin.json have the fields that make the plugin discoverable.
    @Test
    void marketplaceManifestsValidate() throws IOException {
        Path root = repoRoot();
        JsonNode mp = MAPPER.readTree(root.resolve("plugin-marketplace/.claude-plugin/marketplace.json").toFile());

        assertFalse(mp.path("name").asText().isBlank());
        JsonNode plugins = mp.path("plugins");
        assertTrue(plugins.isArray() && plugins.size() >= 1);

        JsonNode plugin = plugins.get(0);
        assertFalse(plugin.path("name").asText().isBlank());
        assertFalse(plugin.path("description").asText().isBlank());
        String source = plugin.path("source").asText();
        assertFalse(source.isBlank());

        Path pluginDir = root.resolve("plugin-marketplace").resolve(source.replaceFirst("^\\./", ""));
        JsonNode pj = MAPPER.readTree(pluginDir.resolve(".claude-plugin/plugin.json").toFile());
        assertEquals("skill-authoring", pj.path("name").asText());

        Path skill = pluginDir.resolve("skills/authoring-java-mcp-skills/SKILL.md");
        assertTrue(Files.isRegularFile(skill), "authoring SKILL.md missing at " + skill);
        String head = Files.readString(skill);
        assertTrue(head.startsWith("---"), "SKILL.md must open with frontmatter");
        assertTrue(head.contains("name:") && head.contains("description:"), "SKILL.md needs name + description");
    }

    // 9.1.1 — every worked example in the SKILL.md parses cleanly with the unit-1 parser.
    @Test
    void documentedExamplesParse() throws IOException {
        Path skill = repoRoot()
            .resolve("plugin-marketplace/skill-authoring/skills/authoring-java-mcp-skills/SKILL.md");
        List<String> blocks = fencedBlocksWithFrontMatter(Files.readString(skill));

        assertTrue(blocks.size() >= 4, "expected the four level examples, got " + blocks.size());

        boolean sawInventory = false;
        boolean sawReferences = false;
        for (String block : blocks) {
            SkillDoc doc = SkillDoc.parse(block, "SKILL.md example", "example");
            assertFalse(doc.title().isBlank());
            sawInventory |= !doc.inventory().isEmpty();
            sawReferences |= !doc.references().isEmpty();
        }
        assertTrue(sawInventory, "a non-leaf example should declare an inventory");
        assertTrue(sawReferences, "the method example should declare references");
    }

    // 9.1.1 — the documented §2.1.4 path maps to the expected canonical name.
    @Test
    void documentedPathMapsToCanonicalName() {
        assertEquals(
            "com.acme.widgets/widgets/Widget/open",
            SkillName.fromResourcePath("META-INF/skills/com.acme.widgets/widgets/Widget/open.md")
                .orElseThrow().canonical());
    }

    /** Extract the contents of ``` fenced blocks whose body opens with a `---` frontmatter fence. */
    private static List<String> fencedBlocksWithFrontMatter(String md) {
        List<String> out = new ArrayList<>();
        StringBuilder current = null;
        for (String line : md.split("\n", -1)) {
            if (line.strip().startsWith("```")) {
                if (current == null) {
                    current = new StringBuilder();
                } else {
                    String body = current.toString();
                    if (body.stripLeading().startsWith("---")) {
                        out.add(body);
                    }
                    current = null;
                }
                continue;
            }
            if (current != null) {
                current.append(line).append('\n');
            }
        }
        return out;
    }
}
