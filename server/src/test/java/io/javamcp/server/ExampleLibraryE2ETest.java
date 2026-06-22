package io.javamcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javamcp.server.discovery.SkillDiscovery;
import io.javamcp.server.mcp.McpDispatcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Unit 10 — end-to-end: the authored {@code examples/} library jar is discovered and fuzzy-searched
 * through the MCP tools, with cajeta-consistent results and no inventory drift (plan §10.1.1, §9.3.1).
 */
class ExampleLibraryE2ETest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OPEN_URI =
        "skill://com.acme:widgets@1.0.0/com.acme.widgets/widgets/Widget/open";

    private static Path exampleJar() throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("examples/build/libs"))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "examples/build/libs not found (is :examples:jar built?)");
        try (Stream<Path> jars = Files.list(dir.resolve("examples/build/libs"))) {
            return jars.filter(p -> p.toString().endsWith(".jar"))
                .filter(p -> !p.toString().endsWith("-sources.jar"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no example jar built"));
        }
    }

    @Test
    void discoversAndSearchesAuthoredLibrary() throws IOException {
        SkillDiscovery discovery = SkillDiscovery.fromRoots(List.of(exampleJar()));

        // 9.3.1 — the authored tree indexes with no drift warnings.
        assertEquals(5, discovery.skillCount());
        assertTrue(discovery.warnings().isEmpty(), discovery.warnings().toString());

        McpDispatcher mcp = new McpDispatcher(discovery, MAPPER);

        // fuzzy: a one-typo method name resolves to the right URI (cajeta semantics).
        JsonNode search = toolData(mcp,
            "{\"name\":\"searchSkills\",\"arguments\":{\"name\":\"com.acme.widgets/widgets/Widget/opan\"}}");
        assertTrue(stream(search).anyMatch(n -> n.path("uri").asText().equals(OPEN_URI)),
            search.toString());

        // get: the URI returns the authored payload.
        JsonNode got = toolData(mcp,
            "{\"name\":\"getSkills\",\"arguments\":{\"uris\":[\"" + OPEN_URI + "\"]}}");
        assertTrue(got.get(0).get("ok").asBoolean());
        assertTrue(got.get(0).get("body").asText().contains("open()"), got.toString());

        // list scoped to the class returns the class + its two method skills.
        JsonNode list = toolData(mcp,
            "{\"name\":\"listSkills\",\"arguments\":{\"scope\":\"com.acme.widgets/widgets/Widget\"}}");
        assertEquals(3, list.size());
    }

    private static JsonNode toolData(McpDispatcher mcp, String params) throws IOException {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":" + params + "}";
        JsonNode resp = MAPPER.readTree(mcp.handleMessage(req).orElseThrow());
        return MAPPER.readTree(resp.get("result").get("content").get(0).get("text").asText());
    }

    private static Stream<JsonNode> stream(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false);
    }
}
