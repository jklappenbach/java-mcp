package io.javamcp.skill;

/**
 * A parsed skill URI: {@code skill://<group>:<artifact>@<version>/<name>}, where {@code name} is a
 * canonical name that may itself contain {@code /} separators. The coordinate pins the owning
 * library version, so a held URI is a stable identity for {@code getSkills}.
 */
public record SkillUri(String group, String artifact, String version, String name) {

    public static final String SCHEME = "skill://";

    /** Parse a {@code skill://<g>:<a>@<v>/<name>} URI; throws {@link SkillUriException} if malformed. */
    public static SkillUri parse(String text) {
        if (text == null || !text.startsWith(SCHEME)) {
            throw new SkillUriException("skill URI: expected scheme '" + SCHEME + "' in '" + text + "'");
        }
        String rest = text.substring(SCHEME.length());

        int slash = rest.indexOf('/');
        if (slash < 0 || slash == rest.length() - 1) {
            throw new SkillUriException("skill URI: missing name in '" + text + "'");
        }
        String coord = rest.substring(0, slash);
        String name = rest.substring(slash + 1);

        int at = coord.indexOf('@');
        if (at < 0 || at == coord.length() - 1) {
            throw new SkillUriException(
                "skill URI: missing version (expected '<group>:<artifact>@<version>') in '" + text + "'");
        }
        String ga = coord.substring(0, at);
        String version = coord.substring(at + 1);

        int colon = ga.indexOf(':');
        if (colon < 0) {
            throw new SkillUriException(
                "skill URI: missing ':' (expected '<group>:<artifact>') in '" + text + "'");
        }
        String group = ga.substring(0, colon);
        String artifact = ga.substring(colon + 1);
        if (group.isEmpty() || artifact.isEmpty()) {
            throw new SkillUriException("skill URI: empty group or artifact in '" + text + "'");
        }

        return new SkillUri(group, artifact, version, name);
    }

    /** Render to canonical {@code skill://…} form (round-trips {@link #parse}). */
    public String format() {
        return SCHEME + group + ":" + artifact + "@" + version + "/" + name;
    }
}
