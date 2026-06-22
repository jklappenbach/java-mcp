package io.javamcp.server.discovery;

import io.javamcp.skill.SkillSource;
import java.util.List;

/** The outcome of scanning a set of classpath roots: the skills found and any non-fatal warnings. */
public record ScanResult(List<SkillSource> sources, List<String> warnings) {}
