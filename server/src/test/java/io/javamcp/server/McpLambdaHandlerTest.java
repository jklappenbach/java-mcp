package io.javamcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.javamcp.server.discovery.SkillDiscovery;
import io.javamcp.server.mcp.McpDispatcher;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit 8 — API Gateway proxy handler routes /mcp identically to local HTTP (plan §8.1.1, §8.3.1). */
class McpLambdaHandlerTest {

    private static final String TOOLS_LIST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

    @Test
    void apiGatewayEventYieldsMcpResponse() {
        McpLambdaHandler handler = new McpLambdaHandler();
        try {
            APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/mcp")
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(TOOLS_LIST);

            APIGatewayProxyResponseEvent resp = handler.handleRequest(req, null);

            assertEquals(200, resp.getStatusCode());
            assertTrue(resp.getBody().contains("searchSkills"), resp.getBody());

            // 8.3.1 — deployed-shape parity: the handler body equals the local dispatch core's output.
            String local = new McpDispatcher(SkillDiscovery.fromRoots(List.of()))
                .handleMessage(TOOLS_LIST).orElseThrow();
            assertEquals(local, resp.getBody());
        } finally {
            handler.close();
        }
    }
}
