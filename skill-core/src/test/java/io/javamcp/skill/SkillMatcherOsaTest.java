package io.javamcp.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit 4 — OSA edit distance (plan §4.1.1). */
class SkillMatcherOsaTest {

    @Test
    void identicalIsZero() {
        assertEquals(0, SkillMatcher.osa("File", "File"));
        assertEquals(0, SkillMatcher.osa("", ""));
    }

    @Test
    void insertDeleteSubstituteCostOne() {
        assertEquals(1, SkillMatcher.osa("Fil", "File"));   // insert
        assertEquals(1, SkillMatcher.osa("Files", "File"));  // delete
        assertEquals(1, SkillMatcher.osa("Bile", "File"));   // substitute
    }

    @Test
    void adjacentTranspositionCostOne() {
        assertEquals(1, SkillMatcher.osa("Flie", "File"));   // li↔il
        assertEquals(1, SkillMatcher.osa("Fiel", "File"));   // el↔le
    }

    @Test
    void emptyAgainstNonEmptyIsLength() {
        assertEquals(4, SkillMatcher.osa("", "File"));
        assertEquals(4, SkillMatcher.osa("File", ""));
    }
}
