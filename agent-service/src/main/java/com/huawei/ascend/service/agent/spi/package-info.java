/**
 * Agent SPI — first-class entity boundary for tenant-owned agents.
 *
 * <p>Authority: ADR-0128. Lives in {@code agent-service} because
 * {@link Agent} is the HTTP-edge-adjacent customer registration
 * surface — customers register {@link AgentDefinition} beans via
 * {@code @Bean} + {@code @ConfigurationProperties} per Rule R-A,
 * and invoke via {@link Agent#invoke(AgentInvocation)} or via
 * {@code Orchestrator.run(...)} for long-running work.
 *
 * <p>The runtime entity {@code Run} (in
 * {@code com.huawei.ascend.service.runtime.runs}) is the
 * EXECUTION instance, distinct from the agent's persistent
 * identity and configuration.
 *
 * <p>An {@link Agent} bundles cross-cutting middleware bindings:
 * <ul>
 *   <li>{@link ModelRef} — bound
 *       {@link com.huawei.ascend.middleware.model.spi.ModelGateway} id</li>
 *   <li>{@code Set<SkillRef>} — bound
 *       {@link com.huawei.ascend.middleware.skill.spi.Skill}s</li>
 *   <li>{@code Map<MemoryCategory, MemoryRef>} — per-category
 *       memory store bindings (middleware.memory.spi)</li>
 *   <li>optional {@code PlannerRef} — bound
 *       {@link com.huawei.ascend.engine.planner.spi.Planner}</li>
 *   <li>{@code systemPrompt}; {@link SafetyPolicy} (ADR-0051)</li>
 * </ul>
 *
 * <p>{@link AgentRegistry} indexes by {@code (tenantId, agentId)}.
 *
 * <p>SPI purity per Rule R-D: imports {@code java.*} + same-module
 * service SPI siblings + cross-module middleware/engine SPI
 * siblings only (agent-service is allowed to depend on
 * agent-middleware and agent-execution-engine per
 * {@code agent-service/module-metadata.yaml#allowed_dependencies}).
 */
package com.huawei.ascend.service.agent.spi;
