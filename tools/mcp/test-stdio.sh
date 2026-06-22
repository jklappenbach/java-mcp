#!/usr/bin/env bash
# Smoke test: run the java-mcp server over stdio with the example library on the
# classpath, and confirm a one-typo fuzzy search resolves to the open skill.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# Gradle 8.10.2 (wrapper) runs on JDK <= 22; prefer the project's JDK 21 when present.
if [ -d "$HOME/.sdkman/candidates/java/21.0.11-amzn" ]; then
  export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.11-amzn"
fi

"$ROOT/gradlew" -p "$ROOT" -q :server:shadowJar :examples:jar

SERVER_JAR=$(ls "$ROOT"/server/build/libs/*-all.jar | head -1)
EXAMPLE_JAR=$(ls "$ROOT"/examples/build/libs/*.jar | grep -v -- '-sources' | head -1)

INIT='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}'
SEARCH='{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"searchSkills","arguments":{"name":"com.acme.widgets/widgets/Widget/opan"}}}'

OUT=$(printf '%s\n%s\n' "$INIT" "$SEARCH" \
  | timeout 60 "$JAVA_HOME/bin/java" -cp "$SERVER_JAR:$EXAMPLE_JAR" io.javamcp.server.Application --stdio)

echo "$OUT"
if echo "$OUT" | grep -q 'skill://com.acme:widgets@1.0.0/com.acme.widgets/widgets/Widget/open'; then
  echo "PASS: stdio search resolved the typo to the open skill"
else
  echo "FAIL: open skill URI not found in response" >&2
  exit 1
fi
