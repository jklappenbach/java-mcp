package io.javamcp.server.mcp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Unit 7 — gzip round-trip + fail-loud on a bad stream (plan §7.1.2). */
class GzipTest {

    @Test
    void roundTrips() throws IOException {
        byte[] data = "{\"jsonrpc\":\"2.0\"}".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(data, Gzip.gunzip(Gzip.gzip(data)));
    }

    @Test
    void gunzipFailsLoudOnGarbage() {
        assertThrows(IOException.class, () -> Gzip.gunzip("not a gzip stream".getBytes(StandardCharsets.UTF_8)));
    }
}
