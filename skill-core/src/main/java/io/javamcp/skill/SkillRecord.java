package io.javamcp.skill;

/** An indexed skill: its full {@code skill://} URI and parsed document. */
public record SkillRecord(SkillUri uri, SkillDoc doc) {

    /** The canonical name (the URI's name part). */
    public String name() {
        return uri.name();
    }
}
