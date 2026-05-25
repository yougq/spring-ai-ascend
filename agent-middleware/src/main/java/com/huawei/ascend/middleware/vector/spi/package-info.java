/**
 * Vector Store SPI — tenant-scoped boundary for vector storage and
 * similarity search.
 *
 * <p>Authority: ADR-0124. Sibling packages:
 * {@link com.huawei.ascend.middleware.retrieval.spi} (composition layer),
 * {@link com.huawei.ascend.middleware.embedding.spi} (embedding
 * production).
 *
 * <p>Reference adapter ({@code SpringAiVectorStore}, lands in Wave
 * C1) decorates Spring AI's
 * {@code org.springframework.ai.vectorstore.VectorStore} per
 * ADR-0125.
 *
 * <p>Couples to {@link com.huawei.ascend.middleware.memory.spi.KnowledgeMemoryStore}
 * (M5 in the ADR-0034 / ADR-0123 taxonomy): a
 * {@code KnowledgeMemoryStore} typically composes a {@code VectorStore}
 * + {@code Retriever} + {@code EmbeddingModel}.
 *
 * <p>Threading model: blocking signatures backed by virtual threads.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-package siblings.
 */
package com.huawei.ascend.middleware.vector.spi;
