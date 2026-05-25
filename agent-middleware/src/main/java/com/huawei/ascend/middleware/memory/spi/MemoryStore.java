package com.huawei.ascend.middleware.memory.spi;

/**
 * Tenant-scoped memory store, parameterized by key and value types.
 *
 * <p>Authority: ADR-0123. {@link MemoryStore} is the union of
 * {@link MemoryReader} and {@link MemoryWriter} for adapters that
 * support both sides; readers / writers may also be implemented
 * independently for split read/write deployments.
 *
 * <p>Every {@link MemoryStore} declares its {@link MemoryCategory}
 * so cross-category invariants (e.g. M3 requires
 * {@code embeddingModelVersion} per write) can be checked
 * uniformly.
 *
 * <p>SPI purity per Rule R-D.
 */
public interface MemoryStore<K, V> extends MemoryReader<K, V>, MemoryWriter<K, V> {

    /** ADR-0034 / ADR-0123 taxonomy category. */
    MemoryCategory category();
}
