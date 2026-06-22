package io.javamcp.server.mcp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javamcp.server.discovery.SkillDiscovery;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit 7 — controller-level gzip negotiation + notification handling, called directly (plan §7.1.2, §7.1.3). */
class McpControllerTest {

    private static McpController controller() {
        return new McpController(new McpDispatcher(SkillDiscovery.fromRoots(List.of())));
    }

    private static final String TOOLS_LIST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

    // 7.1.2 — Accept-Encoding: gzip → gzipped body + Content-Encoding header; decodes to the plain reply.
    @Test
    void acceptEncodingGzipCompressesResponse() throws IOException {
        HttpResponse<byte[]> r = controller().handle(
            TOOLS_LIST.getBytes(StandardCharsets.UTF_8), "", "gzip");

        assertEquals(HttpStatus.OK, r.getStatus());
        assertEquals("gzip", r.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
        String decoded = new String(Gzip.gunzip(r.body()), StandardCharsets.UTF_8);
        assertTrue(decoded.contains("searchSkills"), decoded);
    }

    // 7.1.2 — without Accept-Encoding the body is plain (no Content-Encoding).
    @Test
    void noAcceptEncodingLeavesBodyPlain() {
        HttpResponse<byte[]> r = controller().handle(TOOLS_LIST.getBytes(StandardCharsets.UTF_8), "", "");
        assertNull(r.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
        assertTrue(new String(r.body(), StandardCharsets.UTF_8).contains("searchSkills"));
    }

    // 7.1.2 — inbound Content-Encoding: gzip is gunzipped before dispatch.
    @Test
    void inboundGzipRequestIsDecoded() throws IOException {
        byte[] gz = Gzip.gzip(TOOLS_LIST.getBytes(StandardCharsets.UTF_8));
        HttpResponse<byte[]> r = controller().handle(gz, "gzip", "");
        assertEquals(HttpStatus.OK, r.getStatus());
        assertTrue(new String(r.body(), StandardCharsets.UTF_8).contains("searchSkills"));
    }

    // 7.1.2 — a malformed gzip request body fails loud with 400.
    @Test
    void malformedGzipRequestIs400() {
        HttpResponse<byte[]> r = controller().handle(
            "not gzip".getBytes(StandardCharsets.UTF_8), "gzip", "");
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatus());
    }

    // 7.1.3 — a notification → 202 Accepted, empty body.
    @Test
    void notificationIs202Empty() {
        HttpResponse<byte[]> r = controller().handle(
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}".getBytes(StandardCharsets.UTF_8),
            "", "");
        assertEquals(HttpStatus.ACCEPTED, r.getStatus());
        byte[] b = r.body();
        assertArrayEquals(new byte[0], b == null ? new byte[0] : b);
    }
}
