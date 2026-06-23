#!/usr/bin/env bash
# Smoke test: start the java-mcp HTTP server with the example library on the
# classpath, POST a fuzzy searchSkills to /mcp, and confirm it resolves the open skill.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# Gradle 8.10.2 (wrapper) runs on JDK <= 22; prefer the project's JDK 21 when present.
if [ -d "$HOME/.sdkman/candidates/java/21.0.11-amzn" ]; then
  export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.11-amzn"
fi
PORT="${PORT:-18080}"

"$ROOT/gradlew" -p "$ROOT" -q :server:shadowJar :examples:jar

SERVER_JAR=$(ls -t "$ROOT"/server/build/libs/*-all.jar | head -1)
EXAMPLE_JAR=$(ls "$ROOT"/examples/build/libs/*.jar | grep -v -- '-sources' | head -1)

"$JAVA_HOME/bin/java" -Dmicronaut.server.port="$PORT" \
  -cp "$SERVER_JAR:$EXAMPLE_JAR" io.javamcp.server.Application &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT

# wait for readiness (up to ~30s)
for _ in $(seq 1 60); do
  if curl -fsS -X POST "http://localhost:$PORT/mcp" -H 'Content-Type: application/json' \
       -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}' \
       >/dev/null 2>&1; then
    break
  fi
  sleep 0.5
done

OUT=$(curl -fsS -X POST "http://localhost:$PORT/mcp" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"searchSkills","arguments":{"name":"com.acme.widgets/widgets/Widget/opan"}}}')

echo "$OUT"
if echo "$OUT" | grep -q 'skill://com.acme:widgets@1.0.0/com.acme.widgets/widgets/Widget/open'; then
  echo "PASS: HTTP search resolved the typo to the open skill"
else
  echo "FAIL: open skill URI not found in response" >&2
  exit 1
fi
