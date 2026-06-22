---
title: NoteIndex
description: A substring index over note titles and bodies.
inventory:
  - name: com.example.notes/search/NoteIndex/add
    title: Add a note to the index
  - name: com.example.notes/search/NoteIndex/query
    title: Query the index
---
Construct a `NoteIndex`, `add` notes to it, then `query` with a substring to get back the
ids of matching notes. Pair it with a `NoteStore` to resolve ids back to notes.
