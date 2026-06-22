---
title: Example Notes
description: Create notes, store them, and search them.
inventory:
  - name: com.example.notes/model
    title: model package
  - name: com.example.notes/store
    title: store package
  - name: com.example.notes/search
    title: search package
---
Example Notes is a tiny library for working with notes. Create a `Note` (the `model`
package), persist it with a `NoteStore` (the `store` package), and find it again with a
`NoteIndex` (the `search` package). A typical flow is: create → save → index → query.
