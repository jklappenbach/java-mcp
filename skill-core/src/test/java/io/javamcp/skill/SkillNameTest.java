package io.javamcp.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit 2 — canonical name from a {@code META-INF/skills/} resource path (plan §2.1.1, §2.1.3). */
class SkillNameTest {

    // 2.1.1 — the four §2.1.4 levels map to their canonical names.
    @Test
    void mapsTheFourLevels() {
        assertEquals(
            "com.acme.widgets",
            SkillName.fromResourcePath("META-INF/skills/com.acme.widgets.md").orElseThrow().canonical());
        assertEquals(
            "com.acme.widgets/widgets",
            SkillName.fromResourcePath("META-INF/skills/com.acme.widgets/widgets.md").orElseThrow().canonical());
        assertEquals(
            "com.acme.widgets/widgets/Widget",
            SkillName.fromResourcePath("META-INF/skills/com.acme.widgets/widgets/Widget.md")
                .orElseThrow().canonical());

        SkillName method =
            SkillName.fromResourcePath("META-INF/skills/com.acme.widgets/widgets/Widget/open.md")
                .orElseThrow();
        assertEquals("com.acme.widgets/widgets/Widget/open", method.canonical());
        assertEquals(
            List.of("com.acme.widgets", "widgets", "Widget", "open"), method.segments());
        assertEquals("open", method.leaf());
    }

    // 2.1.1 — separators normalized; a leading slash tolerated.
    @Test
    void normalizesSeparators() {
        assertEquals(
            "com.acme.widgets/widgets/Widget",
            SkillName.fromResourcePath("/META-INF\\skills\\com.acme.widgets\\widgets\\Widget.md")
                .orElseThrow().canonical());
    }

    // 2.1.3 — a path outside META-INF/skills/ is ignored.
    @Test
    void ignoresPathsOutsidePrefix() {
        assertTrue(SkillName.fromResourcePath("META-INF/maven/com.acme/widgets/pom.properties").isEmpty());
        assertTrue(SkillName.fromResourcePath("com/acme/Widget.class").isEmpty());
        assertTrue(SkillName.fromResourcePath("META-INF/skills.md").isEmpty());
    }

    // 2.1.3 — a non-.md entry, the directory itself, and a bare-suffix entry are ignored.
    @Test
    void ignoresNonMarkdownAndDirectories() {
        assertTrue(SkillName.fromResourcePath("META-INF/skills/com.acme.widgets/widgets/Widget.txt").isEmpty());
        assertTrue(SkillName.fromResourcePath("META-INF/skills/").isEmpty());
        assertTrue(SkillName.fromResourcePath("META-INF/skills/com.acme.widgets/").isEmpty());
        assertTrue(SkillName.fromResourcePath("META-INF/skills/.md").isEmpty());
    }

    @Test
    void leafOfLibraryNodeIsTheWholeName() {
        Optional<SkillName> lib = SkillName.fromResourcePath("META-INF/skills/com.acme.widgets.md");
        assertEquals("com.acme.widgets", lib.orElseThrow().leaf());
        assertFalse(lib.orElseThrow().canonical().contains("/"));
    }
}
