package com.huawei.ascend.middleware.memory.spi;

import java.util.List;
import java.util.Optional;

/**
 * Read-only view over a memory store.
 *
 * <p>Authority: ADR-0123. CQRS split — adapters MAY implement only
 * this side (e.g. a shared knowledge corpus mounted read-only).
 *
 * <p>Tenant scope per Rule R-C.c: every method carries {@code tenantId};
 * cross-tenant lookups return {@link Optional#empty()} / empty list.
 */
public interface MemoryReader<K, V> {

    /** Read one entry; empty when missing or cross-tenant. */
    Optional<V> read(String tenantId, K key);

    /**
     * Scan entries matching a query; ordering and cursor semantics
     * are impl-defined but MUST be stable for the same query.
     */
    List<MemoryEntry<K, V>> scan(String tenantId, MemoryQuery query);
}
