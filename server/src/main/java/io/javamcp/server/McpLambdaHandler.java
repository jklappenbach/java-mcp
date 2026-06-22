package io.javamcp.server;

import io.micronaut.function.aws.proxy.payload1.ApiGatewayProxyRequestEventFunction;

/**
 * AWS Lambda entry point: routes API Gateway (REST / payload v1) proxy events through the same
 * Micronaut router that serves {@code POST /mcp} locally, so the deployed response is identical to
 * the local HTTP response. Configured as the Lambda handler class; no per-request wiring needed.
 */
public class McpLambdaHandler extends ApiGatewayProxyRequestEventFunction {
}
