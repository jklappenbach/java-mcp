---
title: Load a note
references:
  - skill://com.example:notes@1.0.0/com.example.notes/store/NoteStore/save
---
`load(id)` returns an `Optional<Note>` — empty when no note has that id. Use it to resolve
ids returned by `NoteIndex.query` back into notes.
