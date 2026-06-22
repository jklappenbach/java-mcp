package com.example.notes.model;

import java.util.UUID;

/** An immutable note: a generated id plus a title and body. */
public final class Note {

    private final String id;
    private final String title;
    private final String body;

    private Note(String id, String title, String body) {
        this.id = id;
        this.title = title;
        this.body = body;
    }

    /** Create a note with a fresh random id. */
    public static Note create(String title, String body) {
        return new Note(UUID.randomUUID().toString(), title, body);
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }
}
