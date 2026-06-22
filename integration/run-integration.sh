#!/usr/bin/env bash
# Build the example "notes" library jar and the java-mcp server uber jar, put the
# library on the classpath, confirm its skills are discoverable, and print the
# command to register the server with Claude Code.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Gradle 8.10.2 (wrapper) runs on JDK <= 22; prefer the project's JDK 21 when present.
if [ -d "$HOME/.sdkman/candidates/java/21.0.11-amzn" ]; then
  export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.11-amzn"
fi
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

echo "==> Building the integration library jar and the MCP server uber jar..."
"$ROOT/gradlew" -p "$ROOT" -q :integration:jar :server:shadowJar

SERVER_JAR=$(ls "$ROOT"/server/build/libs/*-all.jar | head -1)
INTEGRATION_JAR=$(ls "$ROOT"/integration/build/libs/*.jar | grep -v -- '-sources' | head -1)
CP="$SERVER_JAR:$INTEGRATION_JAR"

# Put the library on the classpath for any tools launched from this shell.
export CLASSPATH="$CP"

echo "==> Server jar:      $SERVER_JAR"
echo "==> Integration jar: $INTEGRATION_JAR"
echo "==> CLASSPATH now includes both jars."

echo "==> Launching the server over stdio to confirm the notes skills are discoverable..."
OUT=$(printf '%s\n%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"searchSkills","arguments":{"name":"com.example.notes/store/NoteStore/save"}}}' \
  | timeout 60 "$JAVA" -cp "$CP" io.javamcp.server.Application --stdio)

if echo "$OUT" | grep -q 'skill://com.example:notes@1.0.0/com.example.notes/store/NoteStore/save'; then
  echo "    OK: the notes library's skills are discoverable."
else
  echo "    FAIL: integration skill not found in the server response." >&2
  echo "$OUT" >&2
  exit 1
fi

REGISTER_CMD="claude mcp add notes-skills -- $JAVA -cp $CP io.javamcp.server.Application --stdio"

cat <<EOF

============================================================
 Build complete. The java-mcp server runs and the example
 "notes" library's skills are discoverable on the classpath.

 NEXT STEPS - register the server with Claude Code:

   1. Launch Claude Code:

        claude

   2. Have Claude install the MCP server (or run it yourself):

        $REGISTER_CMD

   3. Ask Claude to use it, e.g.:
        "search skills for com.example.notes/store/NoteStore/save"
        "get the skill at skill://com.example:notes@1.0.0/com.example.notes"

 The command spawns the server over stdio with both jars on
 the classpath, so Claude sees the notes library's skills.
============================================================
EOF
