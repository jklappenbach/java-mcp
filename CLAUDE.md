# CLAUDE.md — java-mcp

Project-level guidance for the **java-mcp** repository: a Micronaut-based, uber-JAR
MCP server for the JVM ecosystem that discovers, indexes, and fuzzy-searches **skills**
shipped inside classpath jars — the Java counterpart to cajeta's skill-discovery
subsystem. Deployable as an AWS Lambda (uber JAR on the JVM runtime).

@td-project-workflow.md

## Layout
- `docs/specs/` — specs (`docs/specs/<name>-spec.md`).
- `agents/` — plans + work stacks (**tracked in this repo**, not gitignored).
- Source skills are authored under `src/main/skills/*.md`; the build packagers embed
  them (compressed, coordinate-namespaced) under `META-INF/skills/` in the jar.

## Skill compatibility
The skill model deliberately mirrors cajeta's (front-matter Markdown, an
`index.json` schema, trigram + segment-aware Damerau–Levenshtein OSA fuzzy matching,
and `searchSkills`/`listSkills`/`getSkills` MCP tools). The URI scheme is Java-native:
`skill://<groupId>:<artifactId>@<version>/<skill-id>`. See `docs/specs/`.
