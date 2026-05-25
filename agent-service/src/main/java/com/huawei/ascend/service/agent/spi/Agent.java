package com.huawei.ascend.service.agent.spi;

import com.huawei.ascend.middleware.memory.spi.MemoryCategory;
import com.huawei.ascend.middleware.memory.spi.MemoryRef;
import com.huawei.ascend.engine.planner.spi.PlannerRef;
import com.huawei.ascend.middleware.skill.spi.SkillRef;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tenant-scoped agent — the first-class entity that composes a
 * model, a set of skills, per-category memory bindings, an optional
 * planner, a system prompt, and a safety policy.
 *
 * <p>Authority: ADR-0128.
 *
 * <p>Synchronous {@link #invoke(AgentInvocation)} handles short
 * request/response loops. Long-running agent work goes through
 * {@code Orchestrator.run(...)} with an {@code ExecutorDefinition}
 * derived from the agent's bindings (see
 * {@code AgentExecutorDefinitionFactory}, Wave C1).
 *
 * <p>SPI purity per Rule R-D: imports java.* + same-module
 * middleware SPI siblings only.
 */
public interface Agent {

    /** Stable per-tenant identity. */
    String agentId();

    /** Owning tenant (Rule R-C.c). */
    String tenantId();

    /** Declarative shape; the bindings live here. */
    AgentDefinition definition();

    /** Synchronous request/response. */
    AgentResponse invoke(AgentInvocation invocation);

    /* Convenience accessors delegating to definition(). */

    default ModelRef modelBinding() {
        return definition().modelBinding();
    }

    default Set<SkillRef> toolBindings() {
        return definition().toolBindings();
    }

    default Map<MemoryCategory, MemoryRef> memoryBindings() {
        return definition().memoryBindings();
    }

    default Optional<PlannerRef> plannerBinding() {
        return definition().plannerBinding();
    }

    default String systemPrompt() {
        return definition().systemPrompt();
    }

    default SafetyPolicy safetyPolicy() {
        return definition().safetyPolicy();
    }
}
