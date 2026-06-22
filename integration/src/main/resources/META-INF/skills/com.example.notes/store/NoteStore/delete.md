---
title: Delete a note
references:
  - skill://com.example:notes@1.0.0/com.example.notes/store/NoteStore/load
---
`delete(id)` removes the note with that id and returns whether one was removed. Safe to
call for an absent id (returns false).
