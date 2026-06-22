---
title: Create a note
references:
  - skill://com.example:notes@1.0.0/com.example.notes/store/NoteStore/save
---
`Note.create(title, body)` returns a new immutable note with a random id. There is no
public constructor — always use `create`. Save the result with `NoteStore.save`.
