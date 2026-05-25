package com.huawei.ascend.middleware.memory.spi;

/**
 * Closed memory category taxonomy (ADR-0034) / ADR-0123.
 *
 * <p>New categories require ADR amendment.
 */
public enum MemoryCategory {

    /** Run-scoped working memory; ephemeral; lives in RunContext. */
    M1_SHORT_TERM,

    /** Session memory; TTL-bounded; {@code TaskStateStore} is M2-adjacent. */
    M2_EPISODIC,

    /** Long-term semantic facts; persistent (vector-backed). */
    M3_SEMANTIC,

    /** Graph relationship memory; {@code GraphMemoryRepository} is M4. */
    M4_GRAPH,

    /** Knowledge index (RAG corpus); composes VectorStore / Retriever / EmbeddingModel. */
    M5_KNOWLEDGE,

    /** Ephemeral retrieved context for one reasoning step; not a backing store. */
    M6_RETRIEVED
}
