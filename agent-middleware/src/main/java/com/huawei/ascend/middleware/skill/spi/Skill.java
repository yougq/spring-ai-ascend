package com.huawei.ascend.middleware.skill.spi;

/**
 * Tenant-scoped skill: the unified abstraction for tools, builtins,
 * agent-as-tool composition, MCP servers, and sandboxed code per
 * ADR-0122 / ADR-0127.
 *
 * <p>Implementations:
 * <ul>
 *   <li>MUST validate {@code tenantId} non-blank in
 *       {@link #execute(SkillInvocation)} (Rule R-C.c).</li>
 *   <li>MUST be thread-safe; the runtime invokes from virtual
 *       threads.</li>
 *   <li>UNTRUSTED_* kinds MUST be invoked through
 *       the sandbox executor SPI (Rule R-L) — the runtime enforces
 *       this at the call site, not in the skill.</li>
 *   <li>{@link #suspend(SkillSuspensionState)} MAY throw
 *       {@link UnsupportedOperationException} for {@link SkillKind#TOOL}
 *       and other kinds whose semantics do not include suspension.</li>
 * </ul>
 *
 * <p>SPI purity per Rule R-D.
 */
public interface Skill {

    /**
     * Stable identifier within the owning tenant; indexes
     * {@link SkillRegistry} and the existing
     * {@code SkillCapacityRegistry}.
     */
    String skillKey();

    /** Discriminator (TOOL / BUILTIN / AGENT_AS_TOOL / MCP_SERVER / UNTRUSTED_*). */
    SkillKind kind();

    /**
     * Called once when the skill is registered. Implementations
     * SHOULD treat this as a no-op if there is no per-tenant setup.
     */
    default void init(SkillContext ctx) {
        // no-op by default
    }

    /**
     * Execute the skill against an invocation envelope.
     *
     * @param invocation tenant-scoped invocation; never null.
     * @return the result; never null.
     */
    SkillResult execute(SkillInvocation invocation);

    /**
     * Capture suspension state for long-running skills.
     *
     * @throws UnsupportedOperationException if this skill does not
     *         support suspension (typical for TOOL kind).
     */
    default void suspend(SkillSuspensionState state) {
        throw new UnsupportedOperationException(
                "skill " + skillKey() + " (kind " + kind() + ") does not support suspension");
    }

    /** Called once when the skill is unregistered. */
    default void teardown() {
        // no-op by default
    }
}
