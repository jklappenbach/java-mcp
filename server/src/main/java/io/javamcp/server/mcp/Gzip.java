package io.javamcp.server.mcp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Minimal gzip helpers for the MCP HTTP transport. {@link #gunzip} fails loud on a bad stream. */
final class Gzip {

    private Gzip() {}

    static byte[] gzip(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(data);
        } catch (IOException e) {
            throw new IllegalStateException("gzip failed", e); // in-memory: cannot happen
        }
        return out.toByteArray();
    }

    static byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gz.readAllBytes();
        }
    }
}
