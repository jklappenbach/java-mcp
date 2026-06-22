package io.javamcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javamcp.server.discovery.Fixtures;
import io.javamcp.server.discovery.SkillDiscovery;
import io.javamcp.skill.SkillCoordinate;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit 8 — the {@code --stdio} main path answers tools/list and finds bundled skills (plan §8.1.2). */
class ApplicationStdioTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void stdioMainAnswersToolsListAndDiscoversBundledSkills(@TempDir Path tmp) throws IOException {
        // A "bundled dependency" skill tree, as it would sit inside the uber jar.
        Path jar = Fixtures.writeJar(
            tmp.resolve("dep.jar"),
            new SkillCoordinate("com.acme", "widgets", "1.4.2"),
            Fixtures.acmeTree());
        SkillDiscovery discovery = SkillDiscovery.fromRoots(List.of(jar));

        String input = String.join("\n",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"searchSkills\",\"arguments\":{\"name\":\"com.acme.widgets/widgets/Widget/open\"}}}")
            + "\n";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Application.runStdio(discovery,
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), out);

        List<String> lines = out.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(2, lines.size());

        JsonNode toolsList = MAPPER.readTree(lines.get(0));
        assertEquals(3, toolsList.get("result").get("tools").size());

        // the bundled dependency's skill is discoverable end-to-end via the stdio transport
        String searchText = MAPPER.readTree(lines.get(1)).get("result").get("content").get(0).get("text").asText();
        assertTrue(searchText.contains("skill://com.acme:widgets@1.4.2/com.acme.widgets/widgets/Widget/open"),
            searchText);
    }
}
