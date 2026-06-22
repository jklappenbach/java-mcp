package io.javamcp.server.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javamcp.skill.SkillCoordinate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit 5 — classpath scanning (plan §5.1.1, §5.1.4). */
class ClasspathScannerTest {

    private static final SkillCoordinate ACME = new SkillCoordinate("com.acme", "widgets", "1.4.2");

    // 5.1.1 — a jar carrying META-INF/skills/** yields every entry with the jar's coordinate.
    @Test
    void scansJarEntriesWithCoordinate(@TempDir Path tmp) throws IOException {
        Path jar = Fixtures.writeJar(tmp.resolve("widgets.jar"), ACME, Fixtures.acmeTree());

        ScanResult r = ClasspathScanner.scan(List.of(jar));

        assertEquals(5, r.sources().size());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
        assertTrue(r.sources().stream()
            .anyMatch(s -> s.uri().format()
                .equals("skill://com.acme:widgets@1.4.2/com.acme.widgets/widgets/Widget/open")));
    }

    // 5.1.1 — a class directory is scanned the same way.
    @Test
    void scansDirectoryEntries(@TempDir Path tmp) throws IOException {
        Path dir = Fixtures.writeDir(tmp.resolve("classes"), ACME, Fixtures.acmeTree());

        ScanResult r = ClasspathScanner.scan(List.of(dir));

        assertEquals(5, r.sources().size());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }

    // 5.1.4 — a corrupt jar is skipped with a warning; a good root still serves.
    @Test
    void corruptJarSkippedOthersServe(@TempDir Path tmp) throws IOException {
        Path corrupt = tmp.resolve("broken.jar");
        Files.writeString(corrupt, "this is not a zip file");
        Path good = Fixtures.writeDir(tmp.resolve("classes"), ACME, Fixtures.acmeTree());

        ScanResult r = ClasspathScanner.scan(List.of(corrupt, good));

        assertEquals(5, r.sources().size());
        assertFalse(r.warnings().isEmpty());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("broken.jar")), r.warnings().toString());
    }

    // A root with skills but no coordinate is skipped with a warning (can't form URIs).
    @Test
    void rootWithoutCoordinateSkippedWithWarning(@TempDir Path tmp) throws IOException {
        Path dir = Fixtures.writeDir(tmp.resolve("classes"), null, Fixtures.acmeTree());

        ScanResult r = ClasspathScanner.scan(List.of(dir));

        assertTrue(r.sources().isEmpty());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("no coordinate")), r.warnings().toString());
    }
}
