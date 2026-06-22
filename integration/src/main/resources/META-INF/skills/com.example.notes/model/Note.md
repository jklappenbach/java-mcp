---
title: Note
description: An immutable note with a generated id, title, and body.
inventory:
  - name: com.example.notes/model/Note/create
    title: Create a note
---
`Note` is immutable. Build one with the static `create` factory (which assigns a random
id), then read `id()`, `title()`, and `body()`. Pass notes to `NoteStore` and `NoteIndex`.
