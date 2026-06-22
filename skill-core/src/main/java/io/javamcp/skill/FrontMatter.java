package io.javamcp.skill;

/**
 * Splits a front-matter Markdown document into its YAML header and Markdown body.
 *
 * <p>A leading {@code ---} line (after an optional UTF-8 BOM) opens the YAML header, which
 * runs to the next line that is exactly {@code ---} or {@code ...}; the body is everything
 * after that closing fence, preserved verbatim. With no leading fence, the whole input is the
 * body. An opening {@code ---} with no closing fence is an error.
 */
record FrontMatter(boolean present, String header, String body) {

    private static final char BOM = '﻿';

    static FrontMatter split(String source, String sourceName) {
        int start = (!source.isEmpty() && source.charAt(0) == BOM) ? 1 : 0;

        int firstEnd = lineEnd(source, start);
        if (!lineContent(source, start, firstEnd).equals("---")) {
            return new FrontMatter(false, "", source);
        }

        int headerStart = next(source, firstEnd);
        int pos = headerStart;
        while (pos < source.length()) {
            int end = lineEnd(source, pos);
            String content = lineContent(source, pos, end);
            if (content.equals("---") || content.equals("...")) {
                String header = source.substring(headerStart, pos);
                String body = source.substring(next(source, end));
                return new FrontMatter(true, header, body);
            }
            pos = next(source, end);
        }
        throw new SkillParseException(
            sourceName + ": opening '---' front-matter fence has no closing fence");
    }

    /** Index of the line's terminator ('\n' or end of string) starting at {@code pos}. */
    private static int lineEnd(String s, int pos) {
        int nl = s.indexOf('\n', pos);
        return nl == -1 ? s.length() : nl;
    }

    /** Index just past the '\n' at {@code end} (or end of string). */
    private static int next(String s, int end) {
        return end < s.length() ? end + 1 : end;
    }

    /** Line text in [pos, end) with any trailing '\r' removed (so CRLF and LF fences compare equal). */
    private static String lineContent(String s, int pos, int end) {
        if (end > pos && s.charAt(end - 1) == '\r') {
            end--;
        }
        return s.substring(pos, end);
    }
}
