package io.javamcp.server;

import io.javamcp.server.discovery.SkillDiscovery;
import io.javamcp.server.mcp.McpDispatcher;
import io.javamcp.server.mcp.StdioTransport;
import io.micronaut.runtime.Micronaut;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * java-mcp server entry point. By default starts the HTTP transport ({@code POST /mcp}); passing
 * {@code --stdio} runs the newline-delimited stdio transport instead (for a local MCP client) and
 * never binds a port. The AWS Lambda deployment uses {@code McpLambdaHandler}, not this main.
 */
public final class Application {

    private Application() {
    }

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--stdio")) {
            runStdio(SkillDiscovery.fromSystemClasspath(), System.in, System.out);
        } else {
            Micronaut.run(Application.class, args);
        }
    }

    /** Run the stdio transport over the given streams; package-visible so it can be driven in tests. */
    static void runStdio(SkillDiscovery discovery, InputStream in, OutputStream out) {
        try {
            new StdioTransport(new McpDispatcher(discovery)).run(in, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
