package com.huawei.ascend.service.agent.spi;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped registry of {@link Agent} instances.
 *
 * <p>Authority: ADR-0128. Cross-tenant lookups return empty.
 */
public interface AgentRegistry {

    /** Register; idempotent for the same (tenantId, agentId). */
    void register(Agent agent);

    /** Unregister; no-op if absent. */
    void unregister(String tenantId, String agentId);

    /** Look up by id; empty when absent or cross-tenant. */
    Optional<Agent> find(String tenantId, String agentId);

    /** List all agents for a tenant. */
    List<Agent> list(String tenantId);
}
