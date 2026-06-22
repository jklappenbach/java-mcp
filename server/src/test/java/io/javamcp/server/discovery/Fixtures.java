package io.javamcp.server.discovery;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.javamcp.skill.SkillCoordinate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Test helpers: synthetic skill-bearing jars and class directories. */
public final class Fixtures {

    private Fixtures() {}

    /** Front-matter for a node with a title and optional inventory of child canonical names. */
    public static String node(String title, String... childNames) {
        StringBuilder sb = new StringBuilder("---\ntitle: ").append(title).append('\n');
        if (childNames.length > 0) {
            sb.append("inventory:\n");
            for (String c : childNames) {
                sb.append("  - name: ").append(c).append("\n    title: ").append(c).append('\n');
            }
        }
        return sb.append("---\nbody\n").toString();
    }

    /** A correct 5-node acme tree (no inventory drift). Keys are canonical names → content. */
    public static Map<String, String> acmeTree() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("com.acme.widgets", node("Widgets", "com.acme.widgets/widgets"));
        m.put("com.acme.widgets/widgets", node("widgets package", "com.acme.widgets/widgets/Widget"));
        m.put("com.acme.widgets/widgets/Widget", node("Widget",
            "com.acme.widgets/widgets/Widget/open", "com.acme.widgets/widgets/Widget/close"));
        m.put("com.acme.widgets/widgets/Widget/open", node("Open the widget"));
        m.put("com.acme.widgets/widgets/Widget/close", node("Close the widget"));
        return m;
    }

    public static Path writeJar(Path jarPath, SkillCoordinate c, Map<String, String> skills) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            if (c != null) {
                put(jos, "META-INF/maven/" + c.group() + "/" + c.artifact() + "/pom.properties", pom(c));
            }
            for (Map.Entry<String, String> e : skills.entrySet()) {
                put(jos, "META-INF/skills/" + e.getKey() + ".md", e.getValue());
            }
        }
        return jarPath;
    }

    public static Path writeDir(Path root, SkillCoordinate c, Map<String, String> skills) throws IOException {
        if (c != null) {
            Path pom = root.resolve("META-INF/maven/" + c.group() + "/" + c.artifact() + "/pom.properties");
            Files.createDirectories(pom.getParent());
            Files.writeString(pom, pom(c));
        }
        for (Map.Entry<String, String> e : skills.entrySet()) {
            Path f = root.resolve("META-INF/skills/" + e.getKey() + ".md");
            Files.createDirectories(f.getParent());
            Files.writeString(f, e.getValue());
        }
        return root;
    }

    private static String pom(SkillCoordinate c) {
        return "groupId=" + c.group() + "\nartifactId=" + c.artifact() + "\nversion=" + c.version() + "\n";
    }

    private static void put(JarOutputStream jos, String name, String content) throws IOException {
        jos.putNextEntry(new JarEntry(name));
        jos.write(content.getBytes(UTF_8));
        jos.closeEntry();
    }
}
