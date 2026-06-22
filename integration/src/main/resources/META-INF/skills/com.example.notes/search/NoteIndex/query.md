---
title: Query the index
references:
  - skill://com.example:notes@1.0.0/com.example.notes/search/NoteIndex/add
  - skill://com.example:notes@1.0.0/com.example.notes/store/NoteStore/load
---
`query(text)` returns the ids of notes whose title or body contains `text` (a plain
substring match, case-sensitive). Resolve the ids with `NoteStore.load`.
