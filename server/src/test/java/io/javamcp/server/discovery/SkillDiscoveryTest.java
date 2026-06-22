package io.javamcp.server.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javamcp.skill.SkillCoordinate;
import io.javamcp.skill.SkillMatch;
import io.javamcp.skill.SkillRecord;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit 5 — aggregate discovery: search / list / get over a synthetic classpath (plan §5.1.2, §5.1.3). */
class SkillDiscoveryTest {

    private static final SkillCoordinate ACME = new SkillCoordinate("com.acme", "widgets", "1.4.2");

    // 5.1.1 — discovery over a URLClassLoader finds and indexes every skill.
    @Test
    void discoversViaClassLoader(@TempDir Path tmp) throws IOException {
        Path jar = Fixtures.writeJar(tmp.resolve("widgets.jar"), ACME, Fixtures.acmeTree());
        SkillDiscovery d = discovery(jar);

        assertEquals(5, d.skillCount());
        assertTrue(d.warnings().isEmpty(), d.warnings().toString());
    }

    // 5.1.2 — search (exact + fuzzy), list (scoped/all), get (URI→payload, per-URI error).
    @Test
    void searchListGet(@TempDir Path tmp) throws IOException {
        Path jar = Fixtures.writeJar(tmp.resolve("widgets.jar"), ACME, Fixtures.acmeTree());
        SkillDiscovery d = discovery(jar);

        String openName = "com.acme.widgets/widgets/Widget/open";

        // exact: only the exact name; fuzzy: a typo still resolves.
        assertFalse(d.search(openName, true).isEmpty());
        assertTrue(d.search("com.acme.widgets/widgets/Widget/opan", true).isEmpty());
        assertTrue(d.search("com.acme.widgets/widgets/Widget/opan", false).stream()
            .anyMatch(m -> m.records().stream().anyMatch(r -> r.name().equals(openName))));

        // list: all vs scoped subtree (Widget + open + close).
        assertEquals(5, d.list(null).size());
        assertEquals(
            List.of(
                "com.acme.widgets/widgets/Widget",
                "com.acme.widgets/widgets/Widget/close",
                "com.acme.widgets/widgets/Widget/open"),
            d.list("com.acme.widgets/widgets/Widget").stream().map(SkillRecord::name).toList());

        // get: a valid URI yields a payload; a bogus and an unparseable URI error without failing siblings.
        String openUri = "skill://com.acme:widgets@1.4.2/" + openName;
        List<GetResult> got = d.get(List.of(openUri, "skill://x:y@1.0/no/such/skill", "not-a-uri"));
        assertEquals(3, got.size());
        assertTrue(got.get(0).isOk());
        assertEquals("body\n", got.get(0).record().doc().body());
        assertFalse(got.get(1).isOk());
        assertFalse(got.get(2).isOk());
    }

    // 5.1.3 — two versions of one library aggregate; a bare exact query returns both, version-tagged.
    @Test
    void twoVersionsBothServed(@TempDir Path tmp) throws IOException {
        Path v1 = Fixtures.writeJar(tmp.resolve("widgets-1.jar"), ACME, Fixtures.acmeTree());
        Path v2 = Fixtures.writeJar(
            tmp.resolve("widgets-2.jar"),
            new SkillCoordinate("com.acme", "widgets", "2.0.0"),
            Fixtures.acmeTree());

        SkillDiscovery d = discovery(v1, v2);

        List<SkillMatch> hits = d.search("com.acme.widgets", true);
        assertEquals(1, hits.size()); // one NAME key…
        assertEquals(
            List.of("1.4.2", "2.0.0"),
            hits.get(0).records().stream().map(r -> r.uri().version()).sorted().toList()); // …two versions
    }

    private static SkillDiscovery discovery(Path... roots) throws IOException {
        URL[] urls = new URL[roots.length];
        for (int i = 0; i < roots.length; i++) {
            urls[i] = roots[i].toUri().toURL();
        }
        try (URLClassLoader cl = new URLClassLoader(urls, null)) {
            return SkillDiscovery.fromClassLoader(cl);
        }
    }
}
