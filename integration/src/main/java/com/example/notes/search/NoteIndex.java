package com.example.notes.search;

import com.example.notes.model.Note;
import java.util.ArrayList;
import java.util.List;

/** A trivial substring index over note titles and bodies. */
public final class NoteIndex {

    private final List<Note> notes = new ArrayList<>();

    /** Add a note to the index. */
    public void add(Note note) {
        notes.add(note);
    }

    /** Return the ids of notes whose title or body contains {@code text}. */
    public List<String> query(String text) {
        List<String> hits = new ArrayList<>();
        for (Note n : notes) {
            if (n.title().contains(text) || n.body().contains(text)) {
                hits.add(n.id());
            }
        }
        return hits;
    }
}
