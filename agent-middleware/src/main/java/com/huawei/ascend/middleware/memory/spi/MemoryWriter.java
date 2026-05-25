package com.huawei.ascend.middleware.memory.spi;

/**
 * Write-only view over a memory store.
 *
 * <p>Authority: ADR-0123. CQRS split — adapters MAY implement only
 * this side.
 *
 * <p>Tenant scope per Rule R-C.c: every method carries {@code tenantId};
 * cross-tenant writes throw {@link SecurityException}.
 */
public interface MemoryWriter<K, V> {

    /**
     * Persist (or overwrite) one entry.
     *
     * @param metadata MUST satisfy category-specific invariants
     *                 (e.g. M3 / M5 require non-null
     *                 {@code embeddingModelVersion}).
     */
    void write(String tenantId, K key, V value, MemoryMetadata metadata);

    /** Delete by key; no-op when absent. */
    void delete(String tenantId, K key);
}
