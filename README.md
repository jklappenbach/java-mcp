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

# (later units) run locally over HTTP, or stdio for a local MCP client:
#   java -jar server/build/libs/server-all.jar
#   java -jar server/build/libs/server-all.jar --stdio
```

## Layout
- `skill-core/` — framework-free skill model, path↔URI mapping, index, fuzzy matcher.
- `server/` — Micronaut app: classpath discovery, MCP dispatch, HTTP + stdio, Lambda handler.
- `plugin-marketplace/` — the in-repo marketplace + authoring skill *(later unit)*.
- `examples/` — a sample skill-bearing library for end-to-end tests *(later unit)*.
- `docs/specs/`, `agents/` — spec, plan, and work stacks (tracked here).
