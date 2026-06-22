# java-mcp

A **Micronaut** uber-JAR **MCP (Model Context Protocol) server** for the JVM ecosystem.
It discovers **skills** — short, hand-authored implementation guides that travel
*inside* a library's jar — by scanning every jar on its classpath, indexes them, and
serves them by canonical name through a typo-tolerant fuzzy search. It is the JVM
counterpart to cajeta's skill-discovery subsystem and shares the `skill://` URI scheme.

> Status: **early development.** See `docs/specs/java-mcp-spec.md` (requirements) and
> `agents/java-mcp/java-mcp-plan.md` (TDD plan).

## How skills work — no build tooling

Skills are authored **in place** under `src/main/resources/META-INF/skills/`, and the
**path defines the skill's identity**. The ordinary `jar`/`shadowJar` build copies and
compresses them — there is **no packaging step, no plugin**.

```
src/main/resources/META-INF/skills/
  com.acme.widgets.md            → skill://com.acme:widgets@1.4.2/com.acme.widgets        (library)
  com.acme.widgets/
    widgets.md                   → skill://com.acme:widgets@1.4.2/com.acme.widgets/widgets (package)
    widgets/
      Widget.md                  → .../com.acme.widgets/widgets/Widget                     (class)
      Widget/
        open.md                  → .../com.acme.widgets/widgets/Widget/open                (method)
```

A node is a *file beside a same-named folder*; methods are leaf files. Non-leaf skills
carry an `inventory` of their children in frontmatter; methods carry optional
`references`. The runtime derives the hierarchy from the tree and warns on drift. The
coordinate in the URI is read from the owning jar's metadata.

An **authoring skill** (shipped from the in-repo plugin-marketplace) guides library
authors through identifying and writing these skills level-by-level.

## Build & run

```bash
# Gradle runs on JDK 21 (the Lambda-managed-runtime target); the toolchain resolves it.
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.11-amzn" ./gradlew build

# run locally over HTTP (POST /mcp), or stdio for a local MCP client:
java -jar server/build/libs/server-0.1.0-SNAPSHOT-all.jar            # HTTP
java -jar server/build/libs/server-0.1.0-SNAPSHOT-all.jar --stdio    # stdio
```

The server discovers skills from **its runtime classpath**, so put the skill-bearing
libraries you want served alongside it:

```bash
java -cp "server-…-all.jar:my-lib.jar" io.javamcp.server.Application --stdio
```

The Lambda deployment uses `io.javamcp.server.McpLambdaHandler` (API Gateway proxy, v1).

## Try it end-to-end

The `examples/` module is a sample library (`com.acme:widgets`) whose skill tree is authored
in place. Build it, put it on the server's classpath, and fuzzy-search through the MCP tools:

```bash
tools/mcp/test-stdio.sh   # builds, runs over stdio, asserts a typo'd search resolves
tools/mcp/test-http.sh    # same over HTTP POST /mcp
```

Both send `searchSkills` for `com.acme.widgets/widgets/Widget/opan` (a typo) and get back
`skill://com.acme:widgets@1.0.0/com.acme.widgets/widgets/Widget/open` at edit distance 1.

To author skills for your own library, install the in-repo marketplace and use the
authoring skill: `/plugin marketplace add <this repo>/plugin-marketplace`.

## The skill-first trigger

The server returns an MCP `instructions` string in its `initialize` result — the handshake's
only server-spoken message — telling the agent to **call `searchSkills` before writing or
editing code against a library**. Hosts surface `instructions` to varying degrees, so for
hard enforcement also pin it in the consuming project's `CLAUDE.md`:

```md
When the java-mcp server is connected, before writing or editing JVM code that uses a
library/package/class/method, first call `searchSkills` with its name; if a skill matches,
`getSkills` it and follow it.
```

## Layout
- `skill-core/` — framework-free skill model, path↔URI mapping, index, fuzzy matcher.
- `server/` — Micronaut app: classpath discovery, MCP dispatch, HTTP + stdio, Lambda handler.
- `plugin-marketplace/` — the in-repo marketplace + the `skill-authoring` plugin.
- `examples/` — a sample skill-bearing library (`com.acme:widgets`) for end-to-end tests.
- `integration/` — a fuller demo library (`com.example:notes`) + `run-integration.sh` (build
  + stdio registration) and `run-http-server.sh` (long-running HTTP server for
  `claude mcp add --transport http notes-skills http://localhost:8765/mcp`).
- `tools/mcp/` — `test-stdio.sh` / `test-http.sh` smoke scripts.
- `docs/specs/`, `agents/` — spec, plan, and work stacks (tracked here).
