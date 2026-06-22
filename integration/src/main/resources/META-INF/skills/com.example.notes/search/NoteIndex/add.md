---
title: Add a note to the index
references:
  - skill://com.example:notes@1.0.0/com.example.notes/store/NoteStore/save
  - skill://com.example:notes@1.0.0/com.example.notes/search/NoteIndex/query
---
`add(note)` adds a note to the in-memory index so it can be found by `query`. Typically you
`save` to the store and `add` to the index together.
