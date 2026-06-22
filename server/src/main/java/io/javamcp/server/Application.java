package io.javamcp.server;

import io.micronaut.runtime.Micronaut;

/**
 * java-mcp server entry point. By default starts the HTTP transport (the
 * Streamable-HTTP {@code POST /mcp} endpoint / Lambda route); a {@code --stdio}
 * mode for local MCP clients lands in a later unit. Scaffold form for unit 0.
 */
public final class Application {

    private Application() {
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
