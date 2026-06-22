package io.javamcp.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit 1 — skill model + front-matter parser (plan §1.1). */
class SkillDocTest {

    // 1.1.1 — full front-matter yields title, description, inventory[], references[], verbatim body.
    @Test
    void parsesFullFrontMatter() {
        String src =
            "---\n"
            + "title: Widget\n"
            + "description: A widget class.\n"
            + "inventory:\n"
            + "  - name: com.acme.widgets/widgets/Widget/open\n"
            + "    title: Open the widget\n"
            + "  - name: com.acme.widgets/widgets/Widget/close\n"
            + "    title: Close the widget\n"
            + "references:\n"
            + "  - skill://com.acme:widgets@1.0.0/com.acme.widgets/widgets\n"
            + "---\n"
            + "# Widget\n\nBody text.\n";

        SkillDoc doc = SkillDoc.parse(src, "Widget.md", "Widget");

        assertEquals("Widget", doc.title());
        assertEquals("A widget class.", doc.description());
        assertEquals(
            List.of(
                new SkillDoc.InventoryEntry("com.acme.widgets/widgets/Widget/open", "Open the widget"),
                new SkillDoc.InventoryEntry("com.acme.widgets/widgets/Widget/close", "Close the widget")),
            doc.inventory());
        assertEquals(
            List.of("skill://com.acme:widgets@1.0.0/com.acme.widgets/widgets"),
            doc.references());
        assertEquals("# Widget\n\nBody text.\n", doc.body());
    }

    // 1.1.1 — optional fields absent: empty/null defaults, body still verbatim.
    @Test
    void optionalFieldsDefaultWhenAbsent() {
        String src = "---\ntitle: Just a title\n---\nbody only\n";

        SkillDoc doc = SkillDoc.parse(src, "x.md", "x");

        assertEquals("Just a title", doc.title());
        assertNull(doc.description());
        assertTrue(doc.inventory().isEmpty());
        assertTrue(doc.references().isEmpty());
        assertEquals("body only\n", doc.body());
    }

    // 1.1.2 — missing front-matter is allowed; title defaults to the leaf name; whole input is the body.
    @Test
    void missingFrontMatterDefaultsTitleToLeafName() {
        String src = "# Heading\n\nNo front matter here.\n";

        SkillDoc doc = SkillDoc.parse(src, "open.md", "open");

        assertEquals("open", doc.title());
        assertNull(doc.description());
        assertTrue(doc.inventory().isEmpty());
        assertEquals(src, doc.body());
    }

    // 1.1.2 — front-matter present but no title key → also defaults to the leaf name.
    @Test
    void blankTitleDefaultsToLeafName() {
        String src = "---\ndescription: has a desc but no title\n---\nbody\n";

        SkillDoc doc = SkillDoc.parse(src, "close.md", "close");

        assertEquals("close", doc.title());
        assertEquals("has a desc but no title", doc.description());
    }

    // 1.1.2 — malformed YAML fails loud, naming the file.
    @Test
    void malformedYamlFailsLoudWithFileName() {
        String src = "---\ntitle: [unclosed\n---\nbody\n";

        SkillParseException ex =
            assertThrows(SkillParseException.class, () -> SkillDoc.parse(src, "broken.md", "broken"));
        assertTrue(ex.getMessage().contains("broken.md"), ex.getMessage());
    }

    // 1.1.2 — a front-matter mapping with a wrong-typed field fails loud, naming file + field.
    @Test
    void wrongTypedFieldFailsLoud() {
        String src = "---\ntitle:\n  - not a string\n---\nbody\n";

        SkillParseException ex =
            assertThrows(SkillParseException.class, () -> SkillDoc.parse(src, "weird.md", "weird"));
        assertTrue(ex.getMessage().contains("weird.md"), ex.getMessage());
        assertTrue(ex.getMessage().contains("title"), ex.getMessage());
    }

    // Body byte-for-byte: an opening fence with no close is an error (naming the file).
    @Test
    void unterminatedFrontMatterFailsLoud() {
        String src = "---\ntitle: x\nbody with no closing fence\n";

        SkillParseException ex =
            assertThrows(SkillParseException.class, () -> SkillDoc.parse(src, "noclose.md", "noclose"));
        assertTrue(ex.getMessage().contains("noclose.md"), ex.getMessage());
    }

    // Body preserved byte-for-byte including a leading BOM stripped only for fence detection.
    @Test
    void bomBeforeFenceStillParses() {
        String src = "﻿---\ntitle: T\n---\nbody\n";

        SkillDoc doc = SkillDoc.parse(src, "bom.md", "bom");

        assertEquals("T", doc.title());
        assertEquals("body\n", doc.body());
    }
}
