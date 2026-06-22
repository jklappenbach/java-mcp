package io.javamcp.server.mcp;

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

/** Unit 6 — newline-delimited stdio framing (plan §6.1.3, §6.3.1). */
class StdioTransportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static McpDispatcher dispatcher(Path tmp) throws IOException {
        Path jar = Fixtures.writeJar(
            tmp.resolve("widgets.jar"),
            new SkillCoordinate("com.acme", "widgets", "1.4.2"),
            Fixtures.acmeTree());
        return new McpDispatcher(SkillDiscovery.fromRoots(List.of(jar)), MAPPER);
    }

    // 6.1.3 — multiple messages over one stream produce ordered responses; a notification produces none.
    @Test
    void multipleMessagesOverOneStream(@TempDir Path tmp) throws IOException {
        String input = String.join("\n",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}") + "\n";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new StdioTransport(dispatcher(tmp)).run(
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), out);

        List<String> lines = out.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(2, lines.size()); // the notification produced no line
        JsonNode first = MAPPER.readTree(lines.get(0));
        JsonNode second = MAPPER.readTree(lines.get(1));
        assertEquals(1, first.get("id").asInt());
        assertEquals("2025-06-18", first.get("result").get("protocolVersion").asText());
        assertEquals(2, second.get("id").asInt());
        assertEquals(3, second.get("result").get("tools").size());
    }

    // 6.3.1 (groundwork) — stdio relays the dispatch core verbatim: same input → same body.
    @Test
    void stdioBodyMatchesDirectDispatch(@TempDir Path tmp) throws IOException {
        McpDispatcher d = dispatcher(tmp);
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/list\"}";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new StdioTransport(d).run(
            new ByteArrayInputStream((msg + "\n").getBytes(StandardCharsets.UTF_8)), out);

        String stdioLine = out.toString(StandardCharsets.UTF_8).strip();
        assertEquals(d.handleMessage(msg).orElseThrow(), stdioLine);
        assertTrue(stdioLine.contains("searchSkills"));
    }
}
