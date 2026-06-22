package io.javamcp.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit 3 — aggregate index + tree-derived hierarchy + inventory drift (plan §3). */
class SkillIndexTest {

    private static final SkillCoordinate ACME = new SkillCoordinate("com.acme", "widgets", "1.4.2");

    private static SkillSource node(SkillCoordinate coord, String name, String title, String... childNames) {
        List<SkillDoc.InventoryEntry> inv = new ArrayList<>();
        for (String c : childNames) {
            inv.add(new SkillDoc.InventoryEntry(c, c));
        }
        SkillDoc doc = new SkillDoc(title, null, List.copyOf(inv), List.of(), "body of " + name);
        return new SkillSource(coord, new SkillName(name), doc);
    }

    /** A correct 5-node tree for com.acme:widgets. */
    private static List<SkillSource> acmeTree() {
        return List.of(
            node(ACME, "com.acme.widgets", "Widgets", "com.acme.widgets/widgets"),
            node(ACME, "com.acme.widgets/widgets", "widgets package", "com.acme.widgets/widgets/Widget"),
            node(ACME, "com.acme.widgets/widgets/Widget", "Widget",
                "com.acme.widgets/widgets/Widget/open", "com.acme.widgets/widgets/Widget/close"),
            node(ACME, "com.acme.widgets/widgets/Widget/open", "Open"),
            node(ACME, "com.acme.widgets/widgets/Widget/close", "Close"));
    }

    // 3.1.1 — name→URI map + tree-derived hierarchy.
    @Test
    void buildsNameToUriAndHierarchy() {
        SkillIndex idx = SkillIndex.build(acmeTree());

        assertEquals(5, idx.all().size());
        assertTrue(idx.warnings().isEmpty(), idx.warnings().toString());

        SkillRecord open =
            idx.get(SkillUri.parse("skill://com.acme:widgets@1.4.2/com.acme.widgets/widgets/Widget/open"))
                .orElseThrow();
        assertEquals("com.acme.widgets/widgets/Widget/open", open.name());
        assertEquals("Open", open.doc().title());
    }

    // 3.1.3 — hierarchical query: self + descendants + ancestor overviews.
    @Test
    void hierarchicalQueryReturnsSelfDescendantsAndAncestors() {
        SkillIndex idx = SkillIndex.build(acmeTree());

        SkillIndex.HierarchicalResult r = idx.query("com.acme.widgets/widgets/Widget");

        assertEquals(List.of("com.acme.widgets/widgets/Widget"), names(r.self()));
        assertEquals(
            List.of("com.acme.widgets/widgets/Widget/close", "com.acme.widgets/widgets/Widget/open"),
            names(r.descendants()));
        // nearest ancestor first → farthest.
        assertEquals(
            List.of("com.acme.widgets/widgets", "com.acme.widgets"), names(r.ancestors()));
    }

    // 3.1.2 — declared inventory omits a real child → drift warning, child still indexed.
    @Test
    void inventoryDriftWarnsButTreeIsAuthoritative() {
        List<SkillSource> sources = new ArrayList<>(acmeTree());
        // Replace the Widget class node with one that omits 'close' from its inventory.
        sources.set(2, node(ACME, "com.acme.widgets/widgets/Widget", "Widget",
            "com.acme.widgets/widgets/Widget/open"));

        SkillIndex idx = SkillIndex.build(sources);

        assertFalse(idx.warnings().isEmpty());
        assertTrue(
            idx.warnings().stream().anyMatch(w ->
                w.contains("com.acme.widgets/widgets/Widget") && w.contains("close")),
            idx.warnings().toString());

        // close is still present in the tree-derived hierarchy.
        assertEquals(5, idx.all().size());
        assertEquals(
            List.of("com.acme.widgets/widgets/Widget/close", "com.acme.widgets/widgets/Widget/open"),
            names(idx.query("com.acme.widgets/widgets/Widget").descendants()));
    }

    // 5.1.3 groundwork — two versions of one library aggregate; a bare name binds both.
    @Test
    void twoVersionsBothIndexedUnderOneName() {
        SkillCoordinate v2 = new SkillCoordinate("com.acme", "widgets", "2.0.0");
        List<SkillSource> sources = new ArrayList<>(acmeTree());
        sources.add(node(v2, "com.acme.widgets", "Widgets v2"));

        SkillIndex idx = SkillIndex.build(sources);

        List<SkillRecord> lib = idx.byName("com.acme.widgets");
        assertEquals(2, lib.size());
        assertEquals(
            List.of("1.4.2", "2.0.0"),
            lib.stream().map(r -> r.uri().version()).sorted().toList());
    }

    private static List<String> names(List<SkillRecord> rs) {
        return rs.stream().map(SkillRecord::name).toList();
    }
}
