package io.javamcp.server.discovery;

import io.javamcp.skill.SkillCoordinate;
import io.javamcp.skill.SkillDoc;
import io.javamcp.skill.SkillName;
import io.javamcp.skill.SkillSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Reads skill trees out of classpath roots (jars and class directories). Each root yields its
 * owning coordinate (from {@code META-INF/maven/<g>/<a>/pom.properties}, falling back to the
 * manifest) and every {@code META-INF/skills/**.md} entry beneath it. Read-only and offline; a
 * root that cannot be read is skipped with a warning so one bad jar never breaks discovery.
 */
public final class ClasspathScanner {

    private static final String SKILLS_PREFIX = "META-INF/skills/";

    private ClasspathScanner() {}

    public static ScanResult scan(List<Path> roots) {
        List<SkillSource> sources = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Path root : roots) {
            try {
                if (!Files.exists(root)) {
                    continue;
                }
                if (Files.isDirectory(root)) {
                    scanDirectory(root, sources, warnings);
                } else {
                    scanJar(root, sources, warnings);
                }
            } catch (Exception e) {
                warnings.add("skipped unreadable classpath root '" + root + "': " + e);
            }
        }
        return new ScanResult(List.copyOf(sources), List.copyOf(warnings));
    }

    private static void scanJar(Path jarPath, List<SkillSource> sources, List<String> warnings)
        throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Optional<SkillCoordinate> coord = coordinateOf(jar);
            if (coord.isEmpty()) {
                warnings.add("no coordinate (pom.properties/manifest) for '" + jarPath + "'; skipping its skills");
                return;
            }
            jar.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> e.getName().startsWith(SKILLS_PREFIX) && e.getName().endsWith(".md"))
                .sorted(Comparator.comparing(java.util.zip.ZipEntry::getName))
                .forEach(e -> SkillName.fromResourcePath(e.getName()).ifPresent(name -> {
                    try (InputStream in = jarStream(jar, e)) {
                        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        sources.add(new SkillSource(
                            coord.get(), name, SkillDoc.parse(content, e.getName(), name.leaf())));
                    } catch (IOException io) {
                        warnings.add("skipped unreadable skill '" + e.getName() + "' in '" + jarPath + "': " + io);
                    }
                }));
        }
    }

    private static InputStream jarStream(JarFile jar, java.util.jar.JarEntry e) throws IOException {
        return jar.getInputStream(e);
    }

    private static void scanDirectory(Path root, List<SkillSource> sources, List<String> warnings)
        throws IOException {
        Path skillsDir = root.resolve(SKILLS_PREFIX);
        if (!Files.isDirectory(skillsDir)) {
            return;
        }
        Optional<SkillCoordinate> coord = coordinateOf(root);
        if (coord.isEmpty()) {
            warnings.add("no coordinate (pom.properties/manifest) under '" + root + "'; skipping its skills");
            return;
        }
        try (Stream<Path> walk = Files.walk(skillsDir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".md"))
                .sorted()
                .forEach(p -> {
                    String resourcePath = root.relativize(p).toString().replace('\\', '/');
                    SkillName.fromResourcePath(resourcePath).ifPresent(name -> {
                        try {
                            String content = Files.readString(p);
                            sources.add(new SkillSource(
                                coord.get(), name, SkillDoc.parse(content, resourcePath, name.leaf())));
                        } catch (IOException io) {
                            warnings.add("skipped unreadable skill '" + p + "': " + io);
                        }
                    });
                });
        }
    }

    /** Coordinate of a jar: the first {@code pom.properties}, else the manifest. */
    private static Optional<SkillCoordinate> coordinateOf(JarFile jar) throws IOException {
        TreeMap<String, java.util.jar.JarEntry> poms = new TreeMap<>();
        jar.stream()
            .filter(e -> e.getName().startsWith("META-INF/maven/") && e.getName().endsWith("/pom.properties"))
            .forEach(e -> poms.put(e.getName(), e));
        if (!poms.isEmpty()) {
            try (InputStream in = jar.getInputStream(poms.firstEntry().getValue())) {
                Properties p = new Properties();
                p.load(in);
                Optional<SkillCoordinate> c = SkillCoordinate.fromPomProperties(p);
                if (c.isPresent()) {
                    return c;
                }
            }
        }
        Manifest m = jar.getManifest();
        return m == null ? Optional.empty() : SkillCoordinate.fromManifest(m);
    }

    /** Coordinate of a class directory: the first {@code pom.properties}, else the manifest file. */
    private static Optional<SkillCoordinate> coordinateOf(Path root) throws IOException {
        Path mavenDir = root.resolve("META-INF/maven");
        if (Files.isDirectory(mavenDir)) {
            Optional<Path> pom;
            try (Stream<Path> walk = Files.walk(mavenDir)) {
                pom = walk.filter(p -> p.getFileName().toString().equals("pom.properties")).sorted().findFirst();
            }
            if (pom.isPresent()) {
                Properties p = new Properties();
                try (InputStream in = Files.newInputStream(pom.get())) {
                    p.load(in);
                }
                Optional<SkillCoordinate> c = SkillCoordinate.fromPomProperties(p);
                if (c.isPresent()) {
                    return c;
                }
            }
        }
        Path manifest = root.resolve("META-INF/MANIFEST.MF");
        if (Files.isRegularFile(manifest)) {
            try (InputStream in = Files.newInputStream(manifest)) {
                return SkillCoordinate.fromManifest(new Manifest(in));
            }
        }
        return Optional.empty();
    }
}
