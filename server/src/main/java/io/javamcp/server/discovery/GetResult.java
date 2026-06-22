package io.javamcp.server.discovery;

import io.javamcp.skill.SkillRecord;

/** The result of resolving one URI in a {@code getSkills} call: either the record or an error. */
public record GetResult(String uri, SkillRecord record, String error) {

    public static GetResult ok(String uri, SkillRecord record) {
        return new GetResult(uri, record, null);
    }

    public static GetResult error(String uri, String error) {
        return new GetResult(uri, null, error);
    }

    public boolean isOk() {
        return record != null;
    }
}
