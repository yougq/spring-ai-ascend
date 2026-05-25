package com.huawei.ascend.middleware.skill.spi;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped skill registry.
 *
 * <p>Authority: ADR-0127. Indexes by {@code (tenantId, skillKey)};
 * impls MUST return empty / refuse cross-tenant lookups.
 */
public interface SkillRegistry {

    /** Register a skill; calls {@link Skill#init(SkillContext)} synchronously. */
    void register(String tenantId, Skill skill);

    /**
     * Unregister; calls {@link Skill#teardown()} synchronously.
     * No-op if not present.
     */
    void unregister(String tenantId, String skillKey);

    /** Look up by key; empty when absent or cross-tenant. */
    Optional<Skill> find(String tenantId, String skillKey);

    /** List skills the tenant has registered; optionally filtered by kind. */
    List<Skill> list(String tenantId, SkillKind kindFilter);
}
