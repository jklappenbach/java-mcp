package io.javamcp.server.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/** Unit 7 — HTTP transport end-to-end over Micronaut (plan §7.1.1, §7.1.3, §7.3.1). */
@MicronautTest
class McpHttpTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    McpDispatcher dispatcher;

    // 7.1.1 / 7.3.1 — POST /mcp returns the same JSON-RPC body the stdio dispatch core produces.
    @Test
    void postReturnsSameBodyAsDispatch() {
        String msg = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        String httpBody = client.toBlocking().retrieve(
            HttpRequest.POST("/mcp", msg).contentType(MediaType.APPLICATION_JSON));
        assertEquals(dispatcher.handleMessage(msg).orElseThrow(), httpBody);
        assertTrue(httpBody.contains("searchSkills"));
    }

    // 7.1.3 — a notification → HTTP 202, no body.
    @Test
    void notificationReturns202() {
        var resp = client.toBlocking().exchange(
            HttpRequest.POST("/mcp", "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")
                .contentType(MediaType.APPLICATION_JSON),
            byte[].class);
        assertEquals(HttpStatus.ACCEPTED, resp.getStatus());
    }

    // 7.1.1 — a wrong path → 404.
    @Test
    void wrongPathIs404() {
        HttpClientResponseException e = org.junit.jupiter.api.Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                HttpRequest.POST("/nope", "{}").contentType(MediaType.APPLICATION_JSON)));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    // 7.1.1 — a wrong method on /mcp → 405.
    @Test
    void wrongMethodIs405() {
        HttpClientResponseException e = org.junit.jupiter.api.Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.GET("/mcp")));
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, e.getStatus());
    }
}
