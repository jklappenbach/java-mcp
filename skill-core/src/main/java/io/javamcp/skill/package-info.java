/**
 * Framework-free core of java-mcp's skill discovery: the skill model and
 * front-matter parser, path&rarr;canonical-name&rarr;URI mapping, the aggregate
 * index, and the fuzzy matcher. Shared by the runtime server (and any future
 * tooling); deliberately free of Micronaut so it stays fast to test.
 */
package io.javamcp.skill;
