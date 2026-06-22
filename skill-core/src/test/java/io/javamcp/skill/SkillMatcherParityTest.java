package io.javamcp.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit 4 — cajeta {@code SkillMatcher} parity (plan §4.1.2, §4.1.3, §4.3.1). The fixtures and
 * expected matches/distances/order are ported from {@code test/buildtool/skill/SkillMatcherTests.cpp}
 * in cajeta-two; java-mcp resolves to records (by canonical name) where cajeta resolved to skill ids.
 */
class SkillMatcherParityTest {

    private static final SkillCoordinate COORD = new SkillCoordinate("test", "fix", "1.0.0");

    private static SkillSource rec(String name, String title) {
        SkillDoc doc = new SkillDoc(title, null, List.of(), List.of(), "b");
        return new SkillSource(COORD, new SkillName(name), doc);
    }

    /**
     * The cajeta {@code sample()} index, expressed in java-mcp's path-as-identity model: each
     * canonical name is its own record. The title "Opening files" (cajeta's file-open skill, which
     * applied to both cajeta/io/File and cajeta/io/File.open) is attached to the File.open record.
     */
    private static SkillMatcher sample() {
        SkillIndex idx = SkillIndex.build(List.of(
            rec("cajeta/io/File", ""),
            rec("cajeta/io/File.open", "Opening files"),
            rec("cajeta/net/Socket", "Network sockets"),
            rec("cajeta/torch/nn/Linear", "Linear layers")));
        return new SkillMatcher(idx);
    }

    private static SkillMatch findKey(List<SkillMatch> v, String key) {
        return v.stream().filter(m -> m.key().equals(key)).findFirst().orElse(null);
    }

    // single-segment name typo resolves to the right name.
    @Test
    void singleSegmentTypoResolves() {
        SkillMatch m = findKey(sample().match("cajeta/io/Fiel.open", false), "cajeta/io/File.open");
        assertNotNull(m);
        assertEquals(SkillMatch.Source.NAME, m.source());
        assertEquals(1, m.distance());
        assertEquals("cajeta/io/File.open", m.records().get(0).name());
    }

    // adjacent transposition resolves with distance 1 (Damerau, not plain Levenshtein).
    @Test
    void transpositionIsDistanceOne() {
        SkillMatch m = findKey(sample().match("cajeta/io/Flie", false), "cajeta/io/File");
        assertNotNull(m);
        assertEquals(1, m.distance());
    }

    // a misspelled title resolves to that title.
    @Test
    void titleTypoResolves() {
        SkillMatch m = findKey(sample().match("Openin files", false), "Opening files");
        assertNotNull(m);
        assertEquals(SkillMatch.Source.TITLE, m.source());
        assertEquals("cajeta/io/File.open", m.records().get(0).name());
    }

    // segment-aware: a different segment count does not match.
    @Test
    void segmentAwareNoCrossSegment() {
        assertNull(findKey(sample().match("cajeta/io/Fileopen", false), "cajeta/io/File.open"));
    }

    // a query beyond the length-scaled threshold returns no garbage.
    @Test
    void thresholdRejectsGarbage() {
        List<SkillMatch> r = sample().match("cajeta/io/Xyzzyq", false);
        assertNull(findKey(r, "cajeta/io/File"));
        assertNull(findKey(r, "cajeta/io/File.open"));
    }

    // exact mode bypasses fuzzy.
    @Test
    void exactModeBypassesFuzzy() {
        assertTrue(sample().match("cajeta/io/Fiel.open", true).isEmpty());
        List<SkillMatch> r = sample().match("cajeta/io/File", true);
        assertFalse(r.isEmpty());
        assertEquals(0, r.get(0).distance());
        assertEquals("cajeta/io/File", r.get(0).key());
    }

    // ranking: exact outranks fuzzy; smaller distance first.
    @Test
    void rankingExactBeforeFuzzy() {
        SkillMatcher m = new SkillMatcher(SkillIndex.build(List.of(
            rec("cajeta/io/File", ""),
            rec("cajeta/io/Bile", ""))));
        List<SkillMatch> r = m.match("cajeta/io/File", false);
        assertTrue(r.size() >= 2);
        assertEquals("cajeta/io/File", r.get(0).key());
        assertEquals(0, r.get(0).distance());
        assertEquals(1, findKey(r, "cajeta/io/Bile").distance());
    }

    // ranking: a Name match outranks a Title match at equal distance.
    @Test
    void rankingNameBeforeTitleAtEqualDistance() {
        SkillMatcher m = new SkillMatcher(SkillIndex.build(List.of(
            rec("report", ""),
            rec("other/Thing", "report"))));
        List<SkillMatch> r = m.match("report", false);
        assertTrue(r.size() >= 2);
        assertEquals(0, r.get(0).distance());
        assertEquals(SkillMatch.Source.NAME, r.get(0).source());
    }

    // determinism: identical query → identical ranked output.
    @Test
    void deterministic() {
        SkillMatcher m = sample();
        List<SkillMatch> a = m.match("cajeta/io/Fiel.open", false);
        List<SkillMatch> b = m.match("cajeta/io/Fiel.open", false);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).key(), b.get(i).key());
            assertEquals(a.get(i).distance(), b.get(i).distance());
            assertEquals(a.get(i).source(), b.get(i).source());
        }
    }
}
