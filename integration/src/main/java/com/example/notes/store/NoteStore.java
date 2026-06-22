package com.example.notes.store;

import com.example.notes.model.Note;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** An in-memory note store keyed by note id. */
public final class NoteStore {

    private final Map<String, Note> byId = new LinkedHashMap<>();

    /** Save (or overwrite) a note. */
    public void save(Note note) {
        byId.put(note.id(), note);
    }

    /** Load a note by id, if present. */
    public Optional<Note> load(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Delete a note by id; returns whether one was removed. */
    public boolean delete(String id) {
        return byId.remove(id) != null;
    }
}
