package io.javamcp.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

/** Unit 2 — coordinate from jar metadata + URI composition (plan §2.1.2, §2.2.2). */
class SkillCoordinateTest {

    @Test
    void readsFromPomProperties() {
        Properties p = new Properties();
        p.setProperty("groupId", "com.acme");
        p.setProperty("artifactId", "widgets");
        p.setProperty("version", "1.4.2");

        SkillCoordinate c = SkillCoordinate.fromPomProperties(p).orElseThrow();
        assertEquals(new SkillCoordinate("com.acme", "widgets", "1.4.2"), c);
    }

    @Test
    void incompletePomPropertiesYieldEmpty() {
        Properties p = new Properties();
        p.setProperty("groupId", "com.acme");
        // no artifactId/version
        assertTrue(SkillCoordinate.fromPomProperties(p).isEmpty());
    }

    @Test
    void fallsBackToManifest() {
        Manifest m = new Manifest();
        Attributes a = m.getMainAttributes();
        a.putValue("Implementation-Vendor-Id", "com.acme");
        a.putValue("Implementation-Title", "widgets");
        a.putValue("Implementation-Version", "1.4.2");

        SkillCoordinate c = SkillCoordinate.fromManifest(m).orElseThrow();
        assertEquals(new SkillCoordinate("com.acme", "widgets", "1.4.2"), c);
    }

    @Test
    void incompleteManifestYieldsEmpty() {
        Manifest m = new Manifest();
        m.getMainAttributes().putValue("Implementation-Version", "1.4.2");
        assertTrue(SkillCoordinate.fromManifest(m).isEmpty());
    }

    // 2.1.2 / 2.2.2 — URI reconstructable from path + coordinate alone, round-trips parse.
    @Test
    void composesUriFromPathAndCoordinate() {
        SkillName name =
            SkillName.fromResourcePath("META-INF/skills/com.acme.widgets/widgets/Widget/open.md")
                .orElseThrow();
        SkillCoordinate coord = new SkillCoordinate("com.acme", "widgets", "1.4.2");

        SkillUri uri = coord.toUri(name.canonical());
        assertEquals(
            "skill://com.acme:widgets@1.4.2/com.acme.widgets/widgets/Widget/open", uri.format());
        assertEquals(uri, SkillUri.parse(uri.format()));
    }
}
