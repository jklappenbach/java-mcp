package com.acme.widgets;

/** A tiny managed widget — the subject of this example library's skill tree. */
public final class Widget {

    private boolean open;

    /** Open the widget before use. Idempotent. */
    public void open() {
        open = true;
    }

    /** Close the widget when done. Idempotent. */
    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }
}
