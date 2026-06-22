package io.javamcp.server.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javamcp.server.discovery.Fixtures;
import io.javamcp.server.discovery.SkillDiscovery;
import io.javamcp.skill.SkillCoordinate;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit 6 — MCP JSON-RPC dispatch (plan §6.1.1, §6.1.2). */
class McpDispatcherTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private McpDispatcher dispatcher;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        Path jar = Fixtures.writeJar(
            tmp.resolve("widgets.jar"),
            new SkillCoordinate("com.acme", "widgets", "1.4.2"),
            Fixtures.acmeTree());
        dispatcher = new McpDispatcher(SkillDiscovery.fromRoots(List.of(jar)), MAPPER);
    }

    private JsonNode call(String json) throws IOException {
        return MAPPER.readTree(dispatcher.handleMessage(json).orElseThrow());
    }

    // 6.1.1 — initialize echoes the client's protocolVersion.
    @Test
    void initializeEchoesProtocolVersion() throws IOException {
        JsonNode r = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
            + "\"params\":{\"protocolVersion\":\"2025-06-18\"}}");
        assertEquals("2.0", r.get("jsonrpc").asText());
        assertEquals(1, r.get("id").asInt());
        assertEquals("2025-06-18", r.get("result").get("protocolVersion").asText());
        assertEquals("java-mcp", r.get("result").get("serverInfo").get("name").asText());
    }

    @Test
    void initializeDefaultsProtocolVersionWhenAbsent() throws IOException {
        JsonNode r = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        assertEquals("2024-11-05", r.get("result").get("protocolVersion").asText());
    }

    // 6.1.1 — tools/list returns exactly the three skill tools.
    @Test
    void toolsListHasThreeTools() throws IOException {
        JsonNode r = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        JsonNode tools = r.get("result").get("tools");
        assertEquals(3, tools.size());
        List<String> names = List.of(
            tools.get(0).get("name").asText(),
            tools.get(1).get("name").asText(),
            tools.get(2).get("name").asText());
        assertTrue(names.containsAll(List.of("searchSkills", "listSkills", "getSkills")), names.toString());
    }

    // 6.1.1 — each tool call returns a text-content result.
    @Test
    void searchSkillsCall() throws IOException {
        JsonNode r = call("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"searchSkills\",\"arguments\":{\"name\":\"com.acme.widgets/widgets/Widget/opan\"}}}");
        String text = r.get("result").get("content").get(0).get("text").asText();
        JsonNode data = MAPPER.readTree(text);
        assertTrue(data.isArray() && data.size() >= 1);
        assertTrue(text.contains("com.acme.widgets/widgets/Widget/open"), text);
    }

    @Test
    void listSkillsCall() throws IOException {
        JsonNode r = call("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"listSkills\",\"arguments\":{}}}");
        JsonNode data = MAPPER.readTree(r.get("result").get("content").get(0).get("text").asText());
        assertEquals(5, data.size());
    }

    @Test
    void getSkillsCall() throws IOException {
        JsonNode r = call("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"getSkills\",\"arguments\":{\"uris\":"
            + "[\"skill://com.acme:widgets@1.4.2/com.acme.widgets/widgets/Widget/open\",\"bogus\"]}}}");
        JsonNode data = MAPPER.readTree(r.get("result").get("content").get(0).get("text").asText());
        assertEquals(2, data.size());
        assertTrue(data.get(0).get("ok").asBoolean());
        assertFalse(data.get(1).get("ok").asBoolean());
    }

    // 6.1.1 — missing required tool args → -32602.
    @Test
    void searchSkillsMissingNameIsInvalidParams() throws IOException {
        JsonNode r = call("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"searchSkills\",\"arguments\":{}}}");
        assertEquals(-32602, r.get("error").get("code").asInt());
    }

    // 6.1.2 — parse error → -32700, null id.
    @Test
    void parseErrorIsMinus32700() throws IOException {
        JsonNode r = call("{ this is not json ");
        assertEquals(-32700, r.get("error").get("code").asInt());
        assertTrue(r.get("id").isNull());
    }

    // 6.1.2 — unknown method (with id) → -32601.
    @Test
    void unknownMethodIsMinus32601() throws IOException {
        JsonNode r = call("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"no/such\"}");
        assertEquals(-32601, r.get("error").get("code").asInt());
    }

    // 6.1.2 — a notification (no id) yields no response.
    @Test
    void notificationYieldsNoResponse() {
        Optional<String> r = dispatcher.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        assertTrue(r.isEmpty());
    }
}
