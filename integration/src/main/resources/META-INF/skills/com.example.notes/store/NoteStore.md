---
title: NoteStore
description: An in-memory store keyed by note id.
inventory:
  - name: com.example.notes/store/NoteStore/save
    title: Save a note
  - name: com.example.notes/store/NoteStore/load
    title: Load a note
  - name: com.example.notes/store/NoteStore/delete
    title: Delete a note
---
Construct a `NoteStore`, then `save` notes and `load` / `delete` them by id. The store
overwrites on a repeated `save` of the same id.
