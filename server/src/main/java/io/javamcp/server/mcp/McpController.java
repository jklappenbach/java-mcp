package io.javamcp.server.mcp;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * MCP HTTP transport: a single {@code POST /mcp} over the shared {@link McpDispatcher}. Gunzips a
 * {@code Content-Encoding: gzip} request body (fail-loud), gzips the reply when the client offers
 * {@code Accept-Encoding: gzip}, and answers a notification with {@code 202 Accepted} and no body —
 * mirroring cajeta's MCP HTTP behavior.
 */
@Controller("/mcp")
public class McpController {

    private final McpDispatcher dispatcher;

    public McpController(McpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Post(consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<byte[]> handle(
        @Body byte[] body,
        @Header(value = HttpHeaders.CONTENT_ENCODING, defaultValue = "") String contentEncoding,
        @Header(value = HttpHeaders.ACCEPT_ENCODING, defaultValue = "") String acceptEncoding) {

        byte[] raw = body == null ? new byte[0] : body;
        if (containsGzip(contentEncoding)) {
            try {
                raw = Gzip.gunzip(raw);
            } catch (IOException e) {
                return HttpResponse.<byte[]>status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON_TYPE)
                    .body(("{\"error\":\"malformed gzip request body: " + e.getMessage() + "\"}")
                        .getBytes(StandardCharsets.UTF_8));
            }
        }

        Optional<String> response = dispatcher.handleMessage(new String(raw, StandardCharsets.UTF_8));
        if (response.isEmpty()) {
            return HttpResponse.<byte[]>status(HttpStatus.ACCEPTED); // notification: no body
        }

        byte[] out = response.get().getBytes(StandardCharsets.UTF_8);
        MutableHttpResponse<byte[]> ok =
            HttpResponse.<byte[]>ok().contentType(MediaType.APPLICATION_JSON_TYPE);
        if (containsGzip(acceptEncoding)) {
            out = Gzip.gzip(out);
            ok = ok.header(HttpHeaders.CONTENT_ENCODING, "gzip");
        }
        return ok.body(out);
    }

    private static boolean containsGzip(String header) {
        return header != null && header.toLowerCase(Locale.ROOT).contains("gzip");
    }
}
