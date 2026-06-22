---
name: authoring-java-mcp-skills
description: >-
  Author java-mcp skills in place under src/main/resources/META-INF/skills/ as a
  path-as-identity tree (library → package → class/component → method). Use when adding
  or maintaining the implementation-guide skills that ship inside a library's jar and are
  served by the java-mcp MCP server. Covers naming, frontmatter, inventories, and references.
---

# Authoring java-mcp skills

A **skill** is a short, hand-written implementation guide for one part of a library. java-mcp
ships skills *inside the library's own jar* and serves them to agents by fuzzy name search. There
is **no build step and no plugin** to package them: you author Markdown files in place and the
ordinary `jar` / `shadowJar` build carries (and compresses) them.

## The one rule: the path is the identity

A skill's identity is its **path** under `src/main/resources/META-INF/skills/`, with `.md` removed
and separators normalized to `/`. The runtime composes the full URI from that path plus the jar's
Maven coordinate:

```
skill://<groupId>:<artifactId>@<version>/<canonical-name>
```

You never write an `id` or an `applies-to` — moving or renaming a file *is* how you change its
identity. Keep the path mirroring the code it documents.

## Tree layout — a node is a file beside a same-named folder

Levels, outermost first: **library → package(s) → class/component → method (leaf)**. Each node is a
`.md` file; its children live in a folder of the same name beside it.

```
src/main/resources/META-INF/skills/
  com.acme.widgets.md                 → library overview
  com.acme.widgets/
    widgets.md                        → package overview
    widgets/
      Widget.md                       → class skill
      Widget/
        open.md                       → method skill (leaf)
        close.md
```

So `…/skills/com.acme.widgets/widgets/Widget/open.md` has canonical name
`com.acme.widgets/widgets/Widget/open`.

## Frontmatter schema

YAML frontmatter (all fields optional) then a Markdown body (the agent-facing payload):

- `title` — a short label, also fuzzy-searched. Defaults to the file's leaf name if omitted.
- `description` — one longer line of summary.
- `inventory` — **non-leaf nodes only**: a list of the node's *direct children*, each
  `{ name: <child canonical name>, title: <child title> }`. Lets an agent drill down.
- `references` — **method/leaf nodes**: a list of related `skill://` URIs (cross-links, including
  into other libraries).

The runtime derives the true hierarchy from the **tree**, and **warns on drift** if a node's
declared `inventory` omits or invents a child — so keep inventories in step as you add/remove files.

## Level-by-level process

1. **Library overview** (`<groupId.artifactId-ish>.md` at the root): what the library is for, when
   to reach for it, the top packages. Its `inventory` lists the package nodes.
2. **Package overview**: the package's role; `inventory` lists the class/component skills under it.
3. **Class / component skill**: how to use the type — construction, lifecycle, gotchas; `inventory`
   lists the method skills.
4. **Method skill (leaf)**: the focused how-to for one operation; add `references` to related skills.

Only author the levels that earn their keep — a tiny library may stop at the class level.

## Maintaining inventories & references

After adding or removing a skill file, update the parent's `inventory` to match the folder, and add
`references` from a method to the skills an agent will want next. Run the server (or its discovery)
and confirm there are **no drift warnings**.

## Worked examples

Library overview — `com.acme.widgets.md`:

```markdown
---
title: Acme Widgets
description: Create, open, and close widgets.
inventory:
  - name: com.acme.widgets/widgets
    title: widgets package
---
Acme Widgets is a small toolkit for managing widget lifecycles. Start with the
`widgets` package.
```

Package overview — `com.acme.widgets/widgets.md`:

```markdown
---
title: widgets package
description: The core widget types.
inventory:
  - name: com.acme.widgets/widgets/Widget
    title: Widget
---
Holds the `Widget` type and its operations.
```

Class skill — `com.acme.widgets/widgets/Widget.md`:

```markdown
---
title: Widget
description: A managed, openable widget.
inventory:
  - name: com.acme.widgets/widgets/Widget/open
    title: Open the widget
  - name: com.acme.widgets/widgets/Widget/close
    title: Close the widget
---
Construct a `Widget`, then `open` it before use and `close` it when done.
```

Method skill (leaf) — `com.acme.widgets/widgets/Widget/open.md`:

```markdown
---
title: Open the widget
references:
  - skill://com.acme:widgets@1.0.0/com.acme.widgets/widgets/Widget/close
---
Call `open()` once before reading. Idempotent; pair every `open` with a `close`.
```
