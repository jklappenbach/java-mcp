#!/usr/bin/env bash
# Run the java-mcp server in HTTP mode with the example "notes" library on the
# classpath, listening on a fixed port. Leave this running (a terminal, tmux, or
# a service) so Claude Code can connect to it over HTTP.
#
#   Register once with:
#     claude mcp add --transport http notes-skills http://localhost:8765/mcp
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -d "$HOME/.sdkman/candidates/java/21.0.11-amzn" ]; then
  export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.11-amzn"
fi
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
PORT="${PORT:-8765}"

"$ROOT/gradlew" -p "$ROOT" -q :integration:jar :server:shadowJar
SERVER_JAR=$(ls "$ROOT"/server/build/libs/*-all.jar | head -1)
INTEGRATION_JAR=$(ls "$ROOT"/integration/build/libs/*.jar | grep -v -- '-sources' | head -1)

echo "Starting java-mcp HTTP server on port $PORT (POST http://localhost:$PORT/mcp)"
echo "Notes library on classpath: $INTEGRATION_JAR"
exec "$JAVA" -Dmicronaut.server.port="$PORT" \
  -cp "$SERVER_JAR:$INTEGRATION_JAR" io.javamcp.server.Application
