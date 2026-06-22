package io.javamcp.server.mcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Newline-delimited JSON-RPC over a byte stream. Reads one message per line and writes one response
 * line (notifications produce none). Uses {@link BufferedReader#readLine} so it blocks only until a
 * full line is available and never over-reads past it — the regression that hung cajeta's stdio MCP.
 */
public final class StdioTransport {

    private final McpDispatcher dispatcher;

    public StdioTransport(McpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void run(InputStream in, OutputStream out) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            Optional<String> response = dispatcher.handleMessage(line);
            if (response.isPresent()) {
                writer.write(response.get());
                writer.write('\n');
                writer.flush();
            }
        }
    }
}
