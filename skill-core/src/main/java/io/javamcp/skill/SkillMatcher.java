package io.javamcp.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Typo-tolerant resolution of a query to index keys (canonical names <em>and</em> titles). A
 * trigram prefilter narrows the key set, then an optimal-string-alignment distance (Damerau–
 * Levenshtein restricted to adjacent transpositions, each cost 1) scores survivors — per segment
 * for names, token-wise for titles — under a length-scaled threshold.
 *
 * <p>This reimplements cajeta's {@code SkillMatcher} exactly: names split on {@code /} and
 * {@code .} and are compared case-sensitively; titles are tokenized on whitespace and compared
 * case-insensitively; the trigram prefilter lowercases. Results are ordered by ascending distance,
 * then NAME before TITLE, then key lexicographic.
 */
public final class SkillMatcher {

    private static final int INF = Integer.MAX_VALUE;

    private record Key(String text, SkillMatch.Source source, List<SkillRecord> records) {}

    private final List<Key> keys = new ArrayList<>();
    private final Map<String, List<Integer>> trigramIndex = new TreeMap<>();

    public SkillMatcher(SkillIndex index) {
        // One key per distinct canonical name (resolving to every version)…
        Set<String> names = new TreeSet<>();
        for (SkillRecord r : index.all()) {
            names.add(r.name());
        }
        for (String name : names) {
            keys.add(new Key(name, SkillMatch.Source.NAME, index.byName(name)));
        }
        // …and one per non-blank title (resolving to its declaring record).
        for (SkillRecord r : index.all()) {
            String title = r.doc().title();
            if (title != null && !title.isBlank()) {
                keys.add(new Key(title, SkillMatch.Source.TITLE, List.of(r)));
            }
        }
        for (int i = 0; i < keys.size(); i++) {
            for (String g : trigramsOf(keys.get(i).text())) {
                trigramIndex.computeIfAbsent(g, k -> new ArrayList<>()).add(i);
            }
        }
    }

    /** Rank index keys by closeness to {@code query}. When {@code exact}, only distance-0 keys. */
    public List<SkillMatch> match(String query, boolean exact) {
        List<SkillMatch> out = new ArrayList<>();
        for (int i : candidates(query)) {
            Key k = keys.get(i);
            int d = k.source() == SkillMatch.Source.NAME
                ? nameDistance(query, k.text())
                : titleDistance(query, k.text());
            if (d == INF || (exact && d != 0)) {
                continue;
            }
            out.add(new SkillMatch(k.text(), k.source(), k.records(), d));
        }
        out.sort(
            Comparator.comparingInt(SkillMatch::distance)
                .thenComparing(m -> m.source() == SkillMatch.Source.NAME ? 0 : 1)
                .thenComparing(SkillMatch::key)
                .thenComparing(m -> m.records().isEmpty() ? "" : m.records().get(0).uri().format()));
        return out;
    }

    /** Key indices sharing at least one trigram with {@code text}. */
    private Set<Integer> candidates(String text) {
        Set<Integer> hit = new HashSet<>();
        for (String g : trigramsOf(text)) {
            List<Integer> ids = trigramIndex.get(g);
            if (ids != null) {
                hit.addAll(ids);
            }
        }
        return hit;
    }

    // ---- distance ----

    /** Optimal string alignment distance (adjacent transpositions cost 1). Case-sensitive. */
    static int osa(String a, String b) {
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev2 = new int[m + 1];
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int best = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
                if (i > 1 && j > 1
                    && a.charAt(i - 1) == b.charAt(j - 2)
                    && a.charAt(i - 2) == b.charAt(j - 1)) {
                    best = Math.min(best, prev2[j - 2] + 1);
                }
                cur[j] = best;
            }
            int[] t = prev2;
            prev2 = prev;
            prev = cur;
            cur = t;
        }
        return prev[m];
    }

    /** Segment-aware name distance; INF on a segment-count mismatch or any over-allowance segment. */
    private static int nameDistance(String query, String key) {
        List<String> qs = segments(query);
        List<String> ks = segments(key);
        if (qs.size() != ks.size()) return INF;
        int total = 0;
        for (int i = 0; i < ks.size(); i++) {
            int d = osa(qs.get(i), ks.get(i));
            if (d > segAllowance(ks.get(i).length())) return INF;
            total += d;
        }
        return total;
    }

    /** Token-wise title distance (case-insensitive); INF beyond the length-scaled allowance. */
    private static int titleDistance(String query, String key) {
        List<String> qt = tokens(query);
        List<String> kt = tokens(key);
        int dist;
        if (qt.size() == kt.size() && !qt.isEmpty()) {
            dist = 0;
            for (int i = 0; i < kt.size(); i++) {
                dist += osa(qt.get(i), kt.get(i));
            }
        } else {
            dist = osa(lower(query), lower(key));
        }
        return dist > titleAllowance(key.length()) ? INF : dist;
    }

    private static int segAllowance(int len) {
        if (len <= 2) return 0;
        if (len <= 5) return 1;
        if (len <= 9) return 2;
        return len / 4;
    }

    private static int titleAllowance(int len) {
        return Math.max(1, len / 5);
    }

    // ---- tokenization ----

    /** Split a canonical name into segments on {@code /} and {@code .} (empty segments included). */
    private static List<String> segments(String name) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= name.length(); i++) {
            if (i == name.length() || name.charAt(i) == '/' || name.charAt(i) == '.') {
                out.add(name.substring(start, i));
                start = i + 1;
            }
        }
        return out;
    }

    /** Whitespace-split, lowercased tokens. */
    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            int start = i;
            while (i < n && !Character.isWhitespace(s.charAt(i))) i++;
            if (i > start) out.add(lower(s.substring(start, i)));
        }
        return out;
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    /** Sorted, de-duplicated lowercased trigrams; the whole string when shorter than 3. */
    private static List<String> trigramsOf(String text) {
        String s = lower(text);
        Set<String> out = new TreeSet<>();
        if (s.length() < 3) {
            if (!s.isEmpty()) out.add(s);
            return new ArrayList<>(out);
        }
        for (int i = 0; i + 3 <= s.length(); i++) {
            out.add(s.substring(i, i + 3));
        }
        return new ArrayList<>(out);
    }
}
