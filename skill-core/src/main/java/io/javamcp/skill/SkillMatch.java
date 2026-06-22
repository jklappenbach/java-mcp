package io.javamcp.skill;

import java.util.List;

/**
 * One ranked fuzzy match: the index key it matched, whether that key was a canonical name or a
 * title, the records it resolves to (every library version for a name; the one declaring record
 * for a title), and the edit distance (0 = exact).
 */
public record SkillMatch(String key, Source source, List<SkillRecord> records, int distance) {

    /** Where a matched key came from. */
    public enum Source {
        NAME,
        TITLE
    }
}
