package io.javamcp.server.mcp;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javamcp.server.discovery.GetResult;
import io.javamcp.server.discovery.SkillDiscovery;
import io.javamcp.skill.SkillMatch;
import io.javamcp.skill.SkillRecord;
import java.util.List;
import java.util.Optional;

/**
 * Transport-agnostic MCP (JSON-RPC 2.0) dispatch over {@link SkillDiscovery}. Handles
 * {@code initialize} (echoing the client's {@code protocolVersion}), {@code tools/list}, and
 * {@code tools/call} for {@code searchSkills} / {@code listSkills} / {@code getSkills}. The same
 * instance backs both the stdio and HTTP transports, so their behavior is identical by construction.
 */
public final class McpDispatcher {

    private static final String DEFAULT_PROTOCOL = "2024-11-05";
    private static final String SERVER_NAME = "java-mcp";
    private static final String SERVER_VERSION = "0.1.0";

    private final ObjectMapper mapper;
    private final SkillDiscovery discovery;

    public McpDispatcher(SkillDiscovery discovery) {
        this(discovery, new ObjectMapper());
    }

    public McpDispatcher(SkillDiscovery discovery, ObjectMapper mapper) {
        this.discovery = discovery;
        this.mapper = mapper;
    }

    /**
     * Parse, dispatch, and serialize one JSON-RPC message. Returns empty for a notification (a
     * request with no {@code id}); a parse failure yields a {@code -32700} response with a null id.
     */
    public Optional<String> handleMessage(String json) {
        JsonNode req;
        try {
            req = mapper.readTree(json);
        } catch (JacksonException e) {
            return Optional.of(serialize(error(null, -32700, "Parse error")));
        }
        ObjectNode resp = dispatch(req);
        return resp == null ? Optional.empty() : Optional.of(serialize(resp));
    }

    /** Dispatch a parsed request; returns null when no response is owed (a notification). */
    public ObjectNode dispatch(JsonNode req) {
        boolean isNotification = req == null || !req.has("id");
        JsonNode id = req == null ? null : req.get("id");

        if (req == null || !req.isObject()) {
            return isNotification ? null : error(id, -32600, "Invalid Request");
        }
        JsonNode methodNode = req.get("method");
        if (methodNode == null || !methodNode.isTextual()) {
            return isNotification ? null : error(id, -32600, "Invalid Request");
        }
        String method = methodNode.asText();

        // A request without an id is a notification: process side effects (none here), never reply.
        if (isNotification) {
            return null;
        }

        return switch (method) {
            case "initialize" -> initialize(req, id);
            case "tools/list" -> toolsList(id);
            case "tools/call" -> toolsCall(req, id);
            default -> error(id, -32601, "Method not found");
        };
    }

    private ObjectNode initialize(JsonNode req, JsonNode id) {
        JsonNode params = req.get("params");
        String protocol = params != null && params.hasNonNull("protocolVersion")
            ? params.get("protocolVersion").asText()
            : DEFAULT_PROTOCOL;

        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", protocol);
        result.set("capabilities", mapper.createObjectNode().set("tools", mapper.createObjectNode()));
        ObjectNode info = result.putObject("serverInfo");
        info.put("name", SERVER_NAME);
        info.put("version", SERVER_VERSION);
        return ok(id, result);
    }

    private ObjectNode toolsList(JsonNode id) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");

        ObjectNode searchSchema = tool(tools, "searchSkills",
            "Search skills by canonical name or title (fuzzy, typo-tolerant).");
        ObjectNode searchProps = searchSchema.putObject("properties");
        searchProps.putObject("name").put("type", "string");
        searchProps.putObject("exact").put("type", "boolean");
        searchSchema.putArray("required").add("name");

        ObjectNode listSchema = tool(tools, "listSkills",
            "List available skills, optionally scoped to a canonical-name subtree.");
        listSchema.putObject("properties").putObject("scope").put("type", "string");

        ObjectNode getSchema = tool(tools, "getSkills",
            "Fetch skill payloads by skill:// URI.");
        ObjectNode uris = getSchema.putObject("properties").putObject("uris");
        uris.put("type", "array");
        uris.putObject("items").put("type", "string");
        getSchema.putArray("required").add("uris");

        return ok(id, result);
    }

    /** Add a tool descriptor and return its (empty) {@code inputSchema} object for the caller to fill. */
    private ObjectNode tool(ArrayNode tools, String name, String description) {
        ObjectNode t = tools.addObject();
        t.put("name", name);
        t.put("description", description);
        ObjectNode schema = t.putObject("inputSchema");
        schema.put("type", "object");
        return schema;
    }

    private ObjectNode toolsCall(JsonNode req, JsonNode id) {
        JsonNode params = req.get("params");
        if (params == null || !params.isObject()) {
            return error(id, -32602, "tools/call requires params");
        }
        JsonNode nameNode = params.get("name");
        if (nameNode == null || !nameNode.isTextual()) {
            return error(id, -32602, "tools/call requires a string 'name'");
        }
        JsonNode args = params.get("arguments");
        ObjectNode arguments = args != null && args.isObject() ? (ObjectNode) args : mapper.createObjectNode();

        return switch (nameNode.asText()) {
            case "searchSkills" -> searchSkills(arguments, id);
            case "listSkills" -> listSkills(arguments, id);
            case "getSkills" -> getSkills(arguments, id);
            default -> error(id, -32602, "unknown tool '" + nameNode.asText() + "'");
        };
    }

    private ObjectNode searchSkills(ObjectNode args, JsonNode id) {
        JsonNode name = args.get("name");
        if (name == null || !name.isTextual()) {
            return error(id, -32602, "searchSkills requires a string 'name'");
        }
        boolean exact = args.path("exact").asBoolean(false);

        ArrayNode data = mapper.createArrayNode();
        for (SkillMatch m : discovery.search(name.asText(), exact)) {
            for (SkillRecord r : m.records()) {
                ObjectNode o = data.addObject();
                o.put("uri", r.uri().format());
                o.put("name", r.name());
                o.put("title", r.doc().title());
                o.put("source", m.source().name().toLowerCase());
                o.put("distance", m.distance());
            }
        }
        return toolResult(id, data);
    }

    private ObjectNode listSkills(ObjectNode args, JsonNode id) {
        String scope = args.path("scope").asText(null);
        ArrayNode data = mapper.createArrayNode();
        for (SkillRecord r : discovery.list(scope)) {
            ObjectNode o = data.addObject();
            o.put("uri", r.uri().format());
            o.put("name", r.name());
            o.put("title", r.doc().title());
        }
        return toolResult(id, data);
    }

    private ObjectNode getSkills(ObjectNode args, JsonNode id) {
        JsonNode uris = args.get("uris");
        if (uris == null || !uris.isArray()) {
            return error(id, -32602, "getSkills requires an array 'uris'");
        }
        List<String> list = new java.util.ArrayList<>();
        for (JsonNode u : uris) {
            if (u.isTextual()) {
                list.add(u.asText());
            }
        }
        ArrayNode data = mapper.createArrayNode();
        for (GetResult g : discovery.get(list)) {
            ObjectNode o = data.addObject();
            o.put("uri", g.uri());
            o.put("ok", g.isOk());
            if (g.isOk()) {
                o.put("title", g.record().doc().title());
                o.put("body", g.record().doc().body());
            } else {
                o.put("error", g.error());
            }
        }
        return toolResult(id, data);
    }

    /** Wrap structured data as an MCP tool result: a single text-content block of JSON. */
    private ObjectNode toolResult(JsonNode id, JsonNode data) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text", serialize(data));
        return ok(id, result);
    }

    private ObjectNode head(JsonNode id) {
        ObjectNode o = mapper.createObjectNode();
        o.put("jsonrpc", "2.0");
        o.set("id", id == null ? mapper.nullNode() : id);
        return o;
    }

    private ObjectNode ok(JsonNode id, JsonNode result) {
        ObjectNode o = head(id);
        o.set("result", result);
        return o;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode o = head(id);
        ObjectNode err = o.putObject("error");
        err.put("code", code);
        err.put("message", message);
        return o;
    }

    private String serialize(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new IllegalStateException("failed to serialize JSON-RPC response", e);
        }
    }
}
