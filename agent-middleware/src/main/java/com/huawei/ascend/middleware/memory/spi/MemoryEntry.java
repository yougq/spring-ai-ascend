package com.huawei.ascend.middleware.memory.spi;

import java.util.Objects;

/**
 * One memory entry returned by {@link MemoryReader#scan(String, MemoryQuery)}.
 *
 * <p>Authority: ADR-0123.
 */
public record MemoryEntry<K, V>(K key, V value, MemoryMetadata metadata) {
    public MemoryEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(metadata, "metadata");
    }
}
