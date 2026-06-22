package io.javamcp.server.mcp;

import io.javamcp.server.discovery.SkillDiscovery;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/** Wires discovery (scanned once from the running classpath) and the MCP dispatcher as beans. */
@Factory
public class McpFactory {

    @Singleton
    SkillDiscovery skillDiscovery() {
        return SkillDiscovery.fromSystemClasspath();
    }

    @Singleton
    McpDispatcher mcpDispatcher(SkillDiscovery discovery) {
        return new McpDispatcher(discovery);
    }
}
