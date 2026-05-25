package com.huawei.ascend.middleware.memory.spi;

/**
 * M3 semantic memory marker interface (ADR-0123).
 *
 * <p>Impls MUST set
 * {@link MemoryMetadata#embeddingModelVersion()} non-null on every
 * write so deterministic replay survives model upgrades.
 *
 * <p>{@link MemoryStore#category()} returns
 * {@link MemoryCategory#M3_SEMANTIC} by default.
 */
public interface SemanticMemoryStore extends MemoryStore<String, SemanticFact> {

    @Override
    default MemoryCategory category() {
        return MemoryCategory.M3_SEMANTIC;
    }
}
