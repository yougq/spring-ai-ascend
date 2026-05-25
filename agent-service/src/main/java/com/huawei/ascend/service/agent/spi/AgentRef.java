package com.huawei.ascend.service.agent.spi;

import java.util.Objects;

/**
 * Opaque reference to an {@link Agent} by (tenantId, agentId).
 *
 * <p>Authority: ADR-0128. Used by {@code SkillKind.AGENT_AS_TOOL}
 * for agent-to-agent composition.
 */
public record AgentRef(String tenantId, String agentId) {
    public AgentRef {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(agentId, "agentId");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank");
        }
        if (agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must be non-blank");
        }
    }
}
