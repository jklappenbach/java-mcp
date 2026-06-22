# java-mcp — execution plan (TDD)

Implements `docs/specs/java-mcp-spec.md` (v2): a Micronaut uber-JAR MCP server that
discovers, indexes, and fuzzy-searches **skills** authored in place under
`src/main/resources/META-INF/skills/` and shipped by the ordinary jar build — plus an
**authoring skill** delivered from an in-repo plugin-marketplace. **No build-time
packaging.** Behavioral + `skill://` parity with cajeta.

## Systems
- **Gradle (Kotlin DSL)**, **Java 21** toolchain; **`com.gradleup.shadow`** (uber jar).
- **Micronaut 4.x** — DI, HTTP controller, `micronaut-aws-api-gateway` Lambda handler.
- **Jackson** (or SnakeYAML) — front-matter YAML parsing in `skill-core`.
- **JDK only** for jar/zip/resource access (no compression lib; jars self-compress).
- **Gradle TestKit** — uber-jar / example integration tests.
- **Claude Code plugin-marketplace** convention (`.claude-plugin/marketplace.json` +
  `skills/<name>/SKILL.md`) for the authoring skill.

## Module layout
- `skill-core/` — framework-free: skill model + front-matter parser, path→name→URI
  mapping, coordinate extraction, aggregate index + hierarchy, fuzzy matcher.
- `server/` — Micronaut app: classpath discovery, MCP dispatch, HTTP + stdio, Lambda
  handler, uber jar.
- `plugin-marketplace/` — the in-repo marketplace + the authoring skill (not a build module).
- `examples/` — a sample skill-bearing library used for end-to-end tests.

## Deliverables
- A runtime library that aggregates skills across the classpath (path-as-identity,
  coordinate-from-jar, runtime-built index) and fuzzy-searches them.
- A Micronaut MCP server (HTTP `POST /mcp` + `--stdio`) exposing `searchSkills` /
  `listSkills` / `getSkills`, deployable as a JVM Lambda uber jar.
- An authoring skill + in-repo plugin-marketplace that guides level-by-level skill
  authoring and maintains per-node inventories.
- A cajeta-parity conformance suite (ported fuzzy fixtures).

> Legend: `- [ ]` not started · `- [x]` done (tests first + passing) · `- [~]`
> blocked. Units are dependency-ordered.

---

## 0. Scaffold & multi-module build
*Depends: —. Satisfies spec §1.5.*
- **TDD**
  - [x] 0.1.1 A trivial test in `skill-core` and `server` runs; `./gradlew build` green.
        *(`SmokeTest` in each module; both `:test` tasks ran green.)*
  - [x] 0.1.2 Java toolchain pinned to 21. *(both modules:
        `java.toolchain.languageVersion = 21`; built on JDK 21.0.11-amzn.)*
- **Coding**
  - [x] 0.2.1 Root `settings.gradle.kts` + Kotlin-DSL build: `skill-core` (`java-library`)
        and `server` (`io.micronaut.application` + `com.gradleup.shadow`); version catalog
        (`gradle/libs.versions.toml`). Wrapper pinned to Gradle 8.10.2 (Micronaut/Shadow
        compat); foojay resolver for toolchain provisioning.
  - [x] 0.2.2 `README.md` (author-in-place model + build/run outline).
- **Acceptance**
  - [x] 0.3.1 `./gradlew build` green with a Java 21 toolchain (`shadowJar` + both
        `:test` tasks; 18 tasks executed). Run via
        `JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.11-amzn` (Gradle 8.10.2 launcher
        needs JDK ≤22; ambient JDK is 25).

## 1. Skill model + front-matter parser  (skill-core)
*Depends: 0. Satisfies spec §2.1.1, §2.3.*
- **TDD**
  - [x] 1.1.1 Parsing a skill `.md` yields `title`, optional `description`, optional
        `inventory[]` (name+title), optional `references[]`, and a verbatim body.
  - [x] 1.1.2 Malformed YAML front-matter fails loud, naming the file; missing
        front-matter is allowed (title defaults to the leaf name).
- **Coding**
  - [x] 1.2.1 `SkillDoc` (title, description, inventory, references, body) + front-matter
        Markdown parser. No `id`, no required `applies-to` (path is identity).
        *(`SkillDoc` record + `InventoryEntry`; `FrontMatter` split; SnakeYAML header;
        `SkillParseException` always names the source. 8 tests green.)*
- **Acceptance**
  - [~] 1.3.1 Parser round-trips the example tree's files (unit 10 fixtures) without loss.
        *(blocked on unit 10 fixtures; verify when the example tree lands.)*

## 2. Path-as-identity: name, URI, coordinate  (skill-core)
*Depends: 0. Satisfies spec §2.1.3, §2.1.4, §4.1.2.*
- **TDD**
  - [x] 2.1.1 `META-INF/skills/com.acme.widgets/widgets/Widget/open.md` → canonical name
        `com.acme.widgets/widgets/Widget/open`; the library/package/class/method levels
        map per §2.1.4.
  - [x] 2.1.2 Coordinate extraction reads `META-INF/maven/<g>/<a>/pom.properties`, falls
        back to the manifest, and composes `skill://<g>:<a>@<v>/<name>`; round-trips via parse.
  - [x] 2.1.3 A path outside `META-INF/skills/` or a non-`.md` entry is rejected/ignored.
- **Coding**
  - [x] 2.2.1 `SkillName` (path↔canonical name) + `SkillUri{group, artifact, version, name}`
        (`skill://<g>:<a>@<v>/<name>`) + a jar-coordinate reader.
        *(`SkillName.fromResourcePath` → `Optional` (ignore-not-error); `SkillUri` parse/format;
        `SkillCoordinate` pom.properties → manifest fallback → `toUri`. 17 tests green.)*
- **Acceptance**
  - [x] 2.2.2 A URI is reconstructable from path + jar coordinate alone (spec §2.2.2).

## 3. Aggregate index + hierarchy  (skill-core)
*Depends: 1, 2. Satisfies spec §2.3.3, §4.1.2, §4.1.4.*
- **TDD**
  - [x] 3.1.1 Building an index from a set of (coordinate, path, front-matter) yields a
        name→URI map and the parent→children hierarchy derived from the **tree**.
  - [x] 3.1.2 A node whose declared `inventory` omits an actual child produces a **drift
        warning**, but the child is still indexed (tree is authoritative).
  - [x] 3.1.3 Hierarchical query: a node name returns its own + descendant + nearest-
        ancestor-overview entries.
- **Coding**
  - [x] 3.2.1 `SkillIndex` built at runtime (names, hierarchy, titles); inventory-drift
        detection; hierarchical/prefix-inclusive query.
        *(`SkillSource`→`SkillRecord`; `byName`/`byUri`/`all`; `query` returns
        self+ancestors(nearest→farthest)+descendants; drift scoped per coordinate, both
        directions. 4 tests green.)*
- **Acceptance**
  - [x] 3.3.1 No shipped index file is required or read (spec §1.4). *(`build` takes
        in-memory `SkillSource`s; no file I/O in skill-core.)*

## 4. Fuzzy matcher + cajeta parity fixtures  (skill-core)
*Depends: 3. Satisfies spec §4.1.3, §7.1.1, §7.2.1.*
- **TDD**
  - [ ] 4.1.1 OSA distance unit tests (insert/delete/substitute/**transpose**=1).
  - [ ] 4.1.2 Ported cajeta `SkillMatcher` fixtures: segment typo → dist 1, transposition
        → 1, title typo, segment-count mismatch → no match, far-miss → no match — same
        matches/distances/order as cajeta (names split on `/` and `.`).
  - [ ] 4.1.3 `exact` bypasses fuzzy matching.
- **Coding**
  - [ ] 4.2.1 Trigram prefilter; segment-aware name distance (≤2→0, ≤5→1, ≤9→2, else
        len/4); token-wise title distance (max(1, len/5)); ranking (distance, name>title,
        lexicographic URI).
- **Acceptance**
  - [ ] 4.3.1 The ported fixture suite passes — Java matches cajeta's results (spec §7.2.1).

## 5. Runtime discovery  (server)
*Depends: 4. Satisfies spec §4.1.*
- **TDD**
  - [ ] 5.1.1 With a synthetic classpath (test jars/dirs carrying `META-INF/skills/**`),
        enumeration finds every skill entry and builds an aggregate index.
  - [ ] 5.1.2 `search` (fuzzy + exact), `list` (scoped/all), `get` (URI→payload) correct;
        get reports a per-URI error without failing siblings.
  - [ ] 5.1.3 Two versions of one library on the classpath → bare query returns both,
        version-tagged.
  - [ ] 5.1.4 A corrupt/unreadable jar is skipped with a warning; others still serve.
- **Coding**
  - [ ] 5.2.1 `SkillDiscovery`: `ClassLoader.getResources` + jar walk for
        `META-INF/skills/**.md`; per-entry name+coordinate; aggregate index over unit 3/4;
        search/list/get; offline; resilient to one bad jar.
- **Acceptance**
  - [ ] 5.3.1 Discovery is read-only, offline, and one bad jar cannot break it (spec §4.1.7).

## 6. MCP dispatch core + stdio transport  (server)
*Depends: 5. Satisfies spec §5.1.1, §5.1.3, §5.1.4, §5.1.5, §5.2.2.*
- **TDD**
  - [ ] 6.1.1 Golden JSON-RPC: `initialize` (result **echoes the client's
        `protocolVersion`**), `tools/list` (the three tools), and each tool call.
  - [ ] 6.1.2 Parse error → `-32700`; a notification (no id) yields no response.
  - [ ] 6.1.3 stdio framing: newline-delimited, multiple messages over one held-open
        stream — no blocking/over-read regression (cajeta lesson).
- **Coding**
  - [ ] 6.2.1 Transport-agnostic `dispatch` + `searchSkills`/`listSkills`/`getSkills`
        handlers over `SkillDiscovery`; a `StdioTransport` loop.
- **Acceptance**
  - [ ] 6.3.1 `--stdio` lifecycle matches the HTTP path for identical inputs (spec §5.2.2).

## 7. HTTP transport (Micronaut) + gzip  (server)
*Depends: 6. Satisfies spec §5.1.2, §5.1.6, §5.2.1, §5.2.4.*
- **TDD**
  - [ ] 7.1.1 `@MicronautTest`: `POST /mcp` returns the same JSON-RPC body as stdio; wrong
        method/path → proper HTTP + JSON-RPC errors.
  - [ ] 7.1.2 `Accept-Encoding: gzip` → gzip body + header decoding to the plain reply;
        inbound `Content-Encoding: gzip` gunzipped (fail-loud on bad gzip).
  - [ ] 7.1.3 A notification → HTTP 202, empty body.
- **Coding**
  - [ ] 7.2.1 A Micronaut `@Controller` `POST /mcp` over the dispatch core, gzip
        negotiation mirroring cajeta's MCP HTTP.
- **Acceptance**
  - [ ] 7.3.1 HTTP/stdio parity across lifecycle, tools, and errors (spec §5.2.1).

## 8. Uber JAR + Lambda handler + main  (server)
*Depends: 7. Satisfies spec §6.*
- **TDD**
  - [ ] 8.1.1 An API-Gateway proxy event driven through the Micronaut Lambda handler
        yields the expected `/mcp` JSON-RPC response (no real AWS).
  - [ ] 8.1.2 `shadowJar` builds a runnable uber jar; `java -jar … --stdio` answers
        `tools/list`; bundled dependency skill trees are discoverable in the uber jar.
- **Coding**
  - [ ] 8.2.1 Shadow config; the API-Gateway proxy handler over `/mcp`; a `main` (HTTP
        default, `--stdio` opt-in).
- **Acceptance**
  - [ ] 8.3.1 Deployed-shape parity: handler response == local HTTP response (spec §6.2.1).

## 9. Authoring skill + in-repo plugin-marketplace
*Depends: 1, 2 (format settled). Independent of the runtime; can land any time after the
format units. Satisfies spec §3.*
- **TDD**
  - [ ] 9.1.1 A validation harness: the authoring skill's documented format examples
        parse cleanly (unit 1 parser) and place files at the §2.1.4 paths.
  - [ ] 9.1.2 `.claude-plugin/marketplace.json` validates against the marketplace schema;
        the skill is discoverable via `/plugin marketplace add`.
- **Coding**
  - [ ] 9.2.1 `plugin-marketplace/` with `marketplace.json` + a plugin shipping
        `skills/<name>/SKILL.md`: level-by-level authoring guide (library→package→
        class/component→method), path-as-identity rules, frontmatter schema, and
        inventory/`references` maintenance.
- **Acceptance**
  - [ ] 9.3.1 Authoring the example library (unit 10) with the skill produces a tree that
        discovery indexes with no drift warnings.

## 10. Example library + end-to-end parity & docs
*Depends: 8, 9. Satisfies spec §7.2, §1.1.*
- **TDD**
  - [ ] 10.1.1 An `examples/` skill-bearing library (authored via the unit-9 skill), built
        and placed on the server's classpath, is discovered and fuzzy-searched end-to-end
        via the MCP tools — results consistent with cajeta semantics.
- **Coding**
  - [ ] 10.2.1 The example module + authored sample skill tree; `README.md` completed;
        a `test-stdio.sh` / `test-http.sh` smoke pair mirroring cajeta's.
- **Acceptance**
  - [ ] 10.3.1 A fresh clone can author (in place), build, run the server, and search
        skills — documented and demonstrated (spec §1.1).

---

## Decisions (locked during design, 2026-06-22)
- **No build-time packaging**: skills authored in place under
  `src/main/resources/META-INF/skills/`; the standard jar ships + compresses them.
- **Path defines identity**: canonical name = path under `META-INF/skills/` (minus
  `.md`); URI `skill://<groupId>:<artifactId>@<version>/<name>`; coordinate from jar
  metadata. Node = file beside same-named folder; methods are leaves.
- **Per-node inventories** (frontmatter) + method `references`; runtime derives the true
  hierarchy from the tree and warns on inventory drift.
- **Authoring skill** shipped from an in-repo Claude Code plugin-marketplace (no plugins).
- Java **21**; uber JAR on JVM; HTTP `POST /mcp` (gzip) + `--stdio`; matcher reimplemented
  to match cajeta exactly (`skill://` scheme shared).
- `agents/` is **tracked in this public repo**.

## Parked / discovered
- **P-JMCP-CAJETA-ALIGN** — the cajeta follow-on (rename `cja-skill://`→`skill://`, drop
  explicit compression, adopt the author-in-place tree); a separate spec/plan in cajeta-two,
  after java-mcp.
- **P-JMCP-NATIVE** — GraalVM native-image custom-runtime Lambda for cold-start.
- **P-JMCP-PUBLISH** — publish the marketplace/server artifacts for external consumption.
- **P-JMCP-SELFSKILLS** — opt-in in-program access to an artifact's own skills (cajeta §8).
- **P-JMCP-SSE** — MCP Streamable-HTTP SSE GET stream + sessions (server-initiated msgs).
