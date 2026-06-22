package io.javamcp.skill;

/**
 * One discovered skill before indexing: the owning jar's coordinate, the skill's path-derived
 * name, and its parsed document. {@link SkillIndex#build} turns a set of these into an index.
 */
public record SkillSource(SkillCoordinate coordinate, SkillName name, SkillDoc doc) {

    /** The full {@code skill://} URI for this source. */
    public SkillUri uri() {
        return coordinate.toUri(name.canonical());
    }

    SkillRecord toRecord() {
        return new SkillRecord(uri(), doc);
    }
}
