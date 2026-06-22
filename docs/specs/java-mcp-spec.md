# java-mcp — specification (SRD)

> Status: **DRAFT v2 — awaiting approval.** Revised after the design conversation
> that removed all build-time packaging in favor of an author-in-place skill tree.

---

## 1. Definition

### 1.1 Purpose & scope
`java-mcp` is a **Micronaut-based MCP (Model Context Protocol) server for the JVM
ecosystem**, deployable as a single **uber JAR** (runnable as an AWS Lambda on the JVM
runtime, or locally over stdio). It makes **skills** — short, hand-authored
implementation guides that travel *inside* a library's jar — **discoverable**: it
scans every jar on its classpath for an embedded skill tree, indexes it, and serves it
by canonical name through a typo-tolerant fuzzy search over the same three operations
cajeta exposes (`searchSkills` / `listSkills` / `getSkills`).

The repository delivers **two** things — and, deliberately, **no build tooling**
between them:
- **A runtime MCP server** that aggregates skills across the classpath and serves
  discovery over HTTP (Lambda / Streamable-HTTP) and stdio (local).
- **An authoring skill**, shipped from an **in-repo plugin-marketplace**, that guides a
  library author through identifying and writing their library's skills at each level
  (library → package → class/component → method).

### 1.2 Problem it solves
On the JVM there is no standard, offline way for an agent (or human) to ask "what
implementation guidance ships with this library?" Javadoc documents *symbols*, not
*how to use them well*, and isn't agent-queryable by fuzzy name. java-mcp lets any jar
carry curated skills — authored directly into `src/main/resources/META-INF/skills/`,
so the **ordinary jar build ships them with no extra step** — and makes the union of
all skills on a classpath searchable through one MCP endpoint, mirroring cajeta so the
two ecosystems behave identically.

### 1.3 In scope
- A skill **authoring format**: a directory tree where **the path defines the skill's
  identity (URI)**, authored under `src/main/resources/META-INF/skills/`.
- **Per-node inventories** so the tree is self-navigating.
- A **runtime discovery library**: classpath scan → path-derived names → aggregate
  index → fuzzy search → payload fetch.
- An **MCP server** exposing `searchSkills` / `listSkills` / `getSkills` over HTTP + stdio.
- An **authoring skill** + an **in-repo Claude Code plugin-marketplace** that ships it.
- An **uber JAR** build and an AWS **Lambda** entry point.

### 1.4 Non-goals
- **Any build-time packaging step.** Skills are ordinary resources; the standard jar
  build copies and compresses them. No Gradle/Maven packager plugin, no bundle, no
  pre-compression, no generated `index.json`.
- A remote skill **registry** (skills travel in jars only; discovery is offline).
- Publishing anything to Gradle Plugin Portal / Maven Central (later).
- GraalVM native-image (uber-JAR-on-JVM chosen; native is a future option).
- Auto-generating skill *content* (skills are hand-authored; the authoring skill guides,
  it does not invent guidance).

### 1.5 Constraints
- **Java 21** (newest LTS with an AWS Lambda managed JVM runtime).
- **Gradle (Kotlin DSL)** + Shadow for the uber jar; **Micronaut 4.x** for DI/HTTP/Lambda.
- **Behavioral parity with cajeta**: fuzzy-match algorithm, name semantics, and tool
  request/response shapes match cajeta's (and the `skill://` scheme is shared — see §7).
- **Offline**: discovery never touches the network; everything is read from the classpath.
- **No extra deps for I/O**: jar/zip access via the JDK; no compression library.

---

## 2. Skill model — author-in-place tree (path = identity)

### 2.1 Requirements
- 2.1.1 A skill is **front-matter Markdown**: a small YAML header (`title`, optional
  `description`, plus the inventory/references of §2.3) and a Markdown body (verbatim).
  There is **no `id` and no required `applies-to`** — the file's location *is* the binding.
- 2.1.2 Skills are authored **in place** under `src/main/resources/META-INF/skills/`,
  so the standard jar build ships them (compressed by the jar) at
  `META-INF/skills/…` with **no packaging step**.
- 2.1.3 **The path defines the URI.** A skill's **canonical name** is its path relative
  to `META-INF/skills/`, with the `.md` suffix removed and separators normalized to `/`.
  Its **URI** is `skill://<groupId>:<artifactId>@<version>/<canonical-name>`, where the
  coordinate is read at runtime from the owning jar's metadata
  (`META-INF/maven/<g>/<a>/pom.properties`, falling back to the manifest).
- 2.1.4 **Layout: a node is a file beside a same-named folder.** The file is the node's
  skill; the folder holds its children. Levels: library → package(s) → class/component →
  method (leaf). Example:
  ```
  src/main/resources/META-INF/skills/
    com.acme.widgets.md            → skill://…/com.acme.widgets            (library)
    com.acme.widgets/
      widgets.md                   → skill://…/com.acme.widgets/widgets    (package)
      widgets/
        Widget.md                  → skill://…/com.acme.widgets/widgets/Widget   (class)
        Widget/
          open.md                  → skill://…/com.acme.widgets/widgets/Widget/open  (method)
          close.md
  ```
- 2.1.5 An artifact with **no** skill tree ships nothing extra; its absence is never an error.
- 2.1.6 **Shade-safety is structural**: library names are unique, so distinct libraries'
  trees never collide when shaded into one uber jar. *(Two versions of the **same**
  library shaded together would collide on path — a rare, already-questionable build;
  noted, not engineered around.)*

### 2.2 Use cases
- 2.2.1 As a library author, when I create
  `src/main/resources/META-INF/skills/com.acme.widgets/widgets/Widget/open.md` and run
  my normal `jar`/`shadowJar`, then the jar carries it at the same path — no plugin, no config.
- 2.2.2 As the runtime, when I read that entry, then I derive its canonical name
  `com.acme.widgets/widgets/Widget/open` and URI from the path + the jar's coordinate alone.
- 2.2.3 As an author, when I rename or move a skill file, then its identity changes
  accordingly — there is no separate id to keep in sync.

### 2.3 Inventories & references
- 2.3.1 Every **non-leaf** node (library / package / class) carries, in its frontmatter,
  an **`inventory`** of its **direct children** — each child's canonical name + title —
  so fetching a node reveals exactly what is beneath it and supports drill-down.
- 2.3.2 A **method (leaf)** has no inventory but may carry a **`references`** list of
  related skill URIs (cross-links, including into other libraries).
- 2.3.3 Inventories are **authored/maintained by the authoring skill** (§3), not a build
  step. The runtime **derives the true hierarchy from the tree regardless** and may
  **warn** when a node's declared inventory drifts from its actual children.

#### 2.3.4 Use cases
- 2.3.4.1 As an agent, when I fetch a class skill, then its `inventory` lists its method
  skills (name + title), so I can drill into the one I need.
- 2.3.4.2 As an agent, when I fetch a method skill, then its `references` point me to
  related skills; following one is a normal `getSkills` call.
- 2.3.4.3 As a maintainer, when a node's `inventory` omits a child that exists in the
  tree, then discovery warns (drift), but still serves the child found in the tree.

---

## 3. Authoring skill + in-repo plugin-marketplace

### 3.1 Requirements
- 3.1.1 The repo hosts a **Claude Code plugin-marketplace** (a `.claude-plugin/
  marketplace.json` + a plugin whose `skills/<name>/SKILL.md` is the authoring guide),
  installable via `/plugin marketplace add <this repo>`.
- 3.1.2 The **authoring skill** guides a developer to **identify** which skills a library
  needs at each level (library overview → package overviews → class/component skills →
  method skills), and to **author** them into the §2.1.4 tree in the correct format.
- 3.1.3 The skill **generates and maintains the per-node inventories** (§2.3) as skills
  are added/removed, and helps wire method `references`.
- 3.1.4 The skill is **format-authoritative**: it encodes the path-as-identity rule, the
  frontmatter schema, and the level conventions, so authored output is valid by construction.

### 3.2 Use cases
- 3.2.1 As a library author, when I install the marketplace and invoke the authoring
  skill, then it walks me level-by-level and proposes the skill set my library needs.
- 3.2.2 As an author, when I accept a proposed skill, then it is written to the correct
  path with valid frontmatter and the parent's inventory is updated.
- 3.2.3 As an author, when I add a method skill, then the skill offers to add relevant
  `references` and refreshes the enclosing class's inventory.

---

## 4. Runtime skill discovery (classpath scan, index, fuzzy search)

### 4.1 Requirements
- 4.1.1 At startup (or first query) discovery **enumerates every** `META-INF/skills/**.md`
  resource visible to the classloader — across all classpath jars and the uber jar's
  merged entries — via `ClassLoader.getResources` + jar walking.
- 4.1.2 For each entry it derives the **canonical name from the path** (§2.1.3) and the
  **coordinate from the owning jar's metadata**, reads the frontmatter (title +
  inventory/references), and merges everything into an **aggregate index** keyed by URI.
  *(The index is built at runtime; there is no shipped index file.)*
- 4.1.3 **Fuzzy search** matches a query against **both** canonical names and titles via a
  **trigram prefilter** then **segment-aware Damerau–Levenshtein (OSA)** distance, with
  cajeta's allowances (names split on `/` and `.`, per-segment ≤2→0 typos, ≤5→1, ≤9→2,
  else len/4; titles token-wise, max(1, len/5)). Ranking: ascending distance, then
  name-over-title, then lexicographic URI. `exact` disables fuzzy matching.
- 4.1.4 Results are **hierarchical / prefix-inclusive**: a name query surfaces the name's
  own skill, its descendants (via the tree / inventories), and the nearest-ancestor
  overview (cajeta §3.2 semantics).
- 4.1.5 When the **same library resolves at multiple versions** on the classpath, a bare
  query returns **all** matching versions, version-tagged in the URI — no silent pick.
- 4.1.6 **Get** resolves a `skill://` URI to its authored payload bytes by locating the
  owning entry; a bad/absent URI is a per-URI error, never a cascade failure.
- 4.1.7 Discovery is **offline** and **read-only**; a malformed entry or unreadable jar is
  skipped with a warning, not a fatal error (one bad jar cannot break discovery).

### 4.2 Use cases
- 4.2.1 As an agent, when I `searchSkills("com.acme.widgets/widgets/Widgt")` (typo), then
  I get the `Widget` URI reported against the matched name at distance 1.
- 4.2.2 As an agent, when I search a title fragment with a typo, then the title match
  resolves to the skill, ranked below any equal-distance name match.
- 4.2.3 As an agent in a classpath with `widgets@1.4.2` and `widgets@2.0.0`, when I search
  a bare name, then I get both, each version-tagged.
- 4.2.4 As an agent, when I `getSkills` a held URI, then I get the exact authored
  frontmatter+body bytes; when one of several URIs is bad, the others still return.
- 4.2.5 As an operator with one corrupt jar on the classpath, when discovery runs, then
  that jar is skipped with a warning and all others are served.

---

## 5. The MCP server (transports + tools)

### 5.1 Requirements
- 5.1.1 The server speaks **JSON-RPC 2.0** MCP (`initialize`, `tools/list`, `tools/call`,
  notifications), sharing one dispatch core across transports.
- 5.1.2 **HTTP transport** (primary, Lambda): a `POST /mcp` Streamable-HTTP endpoint (one
  JSON-RPC message in, one response out) via a Micronaut controller; gzip-negotiated
  through `Accept-Encoding`/`Content-Encoding` (parity with cajeta's MCP HTTP).
- 5.1.3 **stdio transport** (local dev): newline-delimited JSON-RPC over stdin/stdout,
  selected by `--stdio`, standard MCP framing.
- 5.1.4 **Tools** (parity with cajeta), each delegating to the discovery library:
  - `searchSkills` → `{name, version?, from?, exact?}` → `{results:[{uri, matchedName, …}]}`
  - `listSkills`   → `{scope?, version?}`             → `{skills:[{uri, names, title}]}`
  - `getSkills`    → `{uris:[…]}`                     → `{skills:[{uri, payload, error}]}`
- 5.1.5 The `initialize` response **echoes the client's requested `protocolVersion`** (a
  hardcoded version breaks strict clients — a lesson carried over from cajeta).
- 5.1.6 A notification (no `id`) yields no response body (HTTP 202 / stdio silence).

### 5.2 Use cases
- 5.2.1 As an MCP client over HTTP, when I POST `initialize`, then I get a valid result
  echoing my protocol version and advertising the three tools.
- 5.2.2 As a local developer, when I run the uber jar with `--stdio` and pipe `tools/list`,
  then I get the three tools — identical results to the HTTP path.
- 5.2.3 As an MCP client, when I `tools/call searchSkills`, then the response matches the
  discovery library's results for the same query.
- 5.2.4 As a gzip-capable HTTP client, when I send `Accept-Encoding: gzip`, then the body
  is gzip-encoded and decodes to the identical plain JSON-RPC reply.

---

## 6. Lambda packaging & uber JAR

### 6.1 Requirements
- 6.1.1 `./gradlew shadowJar` produces one runnable uber jar bundling the server, the
  Micronaut runtime, discovery, and the skill trees of any skill-bearing dependencies
  (merged shade-safe per §2.1.6).
- 6.1.2 The jar exposes an **AWS Lambda handler** (Micronaut API-Gateway proxy over the
  `/mcp` route) deployable to the managed JVM runtime, **and** a `main` that runs HTTP
  locally or `--stdio`.
- 6.1.3 Handler wiring is validated by an integration test that drives an API-Gateway
  proxy event through the handler to a skills response (no real AWS).

### 6.2 Use cases
- 6.2.1 As an operator, when I deploy the uber jar to a JVM Lambda behind API Gateway and
  POST an MCP request, then I get the same response as the local HTTP server.
- 6.2.2 As a developer, when I run `java -jar java-mcp-all.jar`, the HTTP server starts;
  with `--stdio` it serves an MCP client over stdin/stdout.

---

## 7. Behavioral parity with cajeta (cross-cutting)

### 7.1 Requirements
- 7.1.1 The fuzzy matcher reproduces cajeta's results: same trigram prefilter, OSA
  distances, segment/title allowances, and ranking — verified by a corpus of
  query→expected-match fixtures ported from cajeta's SkillMatcher tests.
- 7.1.2 The tool request/response field names match cajeta's MCP tools.
- 7.1.3 The URI scheme is **`skill://`** — shared with cajeta (cajeta migrates
  `cja-skill://` → `skill://` in the follow-on). Only the coordinate syntax differs
  (`<groupId>:<artifactId>` for Java vs cajeta's library token).

### 7.2 Use cases
- 7.2.1 As a maintainer, when I run the ported fuzzy-match fixtures, then Java produces
  the same matches/distances/order cajeta does.
- 7.2.2 As an agent fluent in cajeta's skill API, when I use java-mcp's tools, then the
  request/response shapes and `skill://` URIs are the ones I already know.

---

## 8. Follow-on (out of scope here, tracked for after this ships)
- 8.1 **cajeta alignment** (in the cajeta-two repo, its own spec/plan): rename
  `cja-skill://` → `skill://`; drop any explicit pre-compression (the `.cja` archive
  already compresses); adopt the author-in-place tree (path-as-identity + inventories)
  from a project-root `skills/` directory, replacing flat `skills/*.md` + `applies-to` +
  build-time `index.json`. In-program access to an archive's own skills stays
  **discovery-only by default**, opt-in API only if a real use case arises.
