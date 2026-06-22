package io.javamcp.skill;

/** Thrown when a {@code skill://} URI is malformed. */
public class SkillUriException extends RuntimeException {
    public SkillUriException(String message) {
        super(message);
    }
}
