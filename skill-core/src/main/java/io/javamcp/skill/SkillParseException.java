package io.javamcp.skill;

/** Thrown when a skill document cannot be parsed; the message always names the source. */
public class SkillParseException extends RuntimeException {
    public SkillParseException(String message) {
        super(message);
    }

    public SkillParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
