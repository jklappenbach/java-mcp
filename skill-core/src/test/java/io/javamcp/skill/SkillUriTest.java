package io.javamcp.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit 2 — {@code skill://<g>:<a>@<v>/<name>} parse/format (plan §2.1.2). */
class SkillUriTest {

    @Test
    void parsesAndExposesParts() {
        SkillUri u = SkillUri.parse("skill://com.acme:widgets@1.4.2/com.acme.widgets/widgets/Widget/open");
        assertEquals("com.acme", u.group());
        assertEquals("widgets", u.artifact());
        assertEquals("1.4.2", u.version());
        assertEquals("com.acme.widgets/widgets/Widget/open", u.name());
    }

    @Test
    void formatRoundTripsParse() {
        String text = "skill://com.acme:widgets@1.4.2/com.acme.widgets/widgets/Widget/open";
        assertEquals(text, SkillUri.parse(text).format());

        SkillUri u = new SkillUri("io.javamcp", "core", "0.1.0", "io.javamcp.core");
        assertEquals(u, SkillUri.parse(u.format()));
    }

    @Test
    void rejectsWrongScheme() {
        SkillUriException e =
            assertThrows(SkillUriException.class, () -> SkillUri.parse("cja-skill://com.acme:widgets@1.0/x"));
        assertTrue(e.getMessage().contains("skill://"));
    }

    @Test
    void rejectsMissingVersion() {
        assertThrows(SkillUriException.class, () -> SkillUri.parse("skill://com.acme:widgets/name"));
    }

    @Test
    void rejectsMissingArtifactSeparator() {
        assertThrows(SkillUriException.class, () -> SkillUri.parse("skill://comacme@1.0/name"));
    }

    @Test
    void rejectsMissingName() {
        assertThrows(SkillUriException.class, () -> SkillUri.parse("skill://com.acme:widgets@1.0.0"));
        assertThrows(SkillUriException.class, () -> SkillUri.parse("skill://com.acme:widgets@1.0.0/"));
    }

    @Test
    void rejectsEmptyGroupOrArtifact() {
        assertThrows(SkillUriException.class, () -> SkillUri.parse("skill://:widgets@1.0/name"));
        assertThrows(SkillUriException.class, () -> SkillUri.parse("skill://com.acme:@1.0/name"));
    }
}
