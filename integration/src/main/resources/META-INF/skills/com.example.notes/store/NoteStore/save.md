---
title: Save a note
references:
  - skill://com.example:notes@1.0.0/com.example.notes/model/Note/create
  - skill://com.example:notes@1.0.0/com.example.notes/store/NoteStore/load
---
`save(note)` stores the note under its `id()`, overwriting any existing note with that id.
Create the note with `Note.create` first; read it back with `load`.
