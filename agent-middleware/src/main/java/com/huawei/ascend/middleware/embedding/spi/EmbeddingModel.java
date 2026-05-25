package com.huawei.ascend.middleware.embedding.spi;

import java.util.List;

/**
 * Tenant-scoped text embedding boundary.
 *
 * <p>Authority: ADR-0124. The {@link #modelVersion()} return value
 * is recorded in {@code MemoryMetadata.embeddingModelVersion} for
 * every M3 / M5 write (ADR-0123) so deterministic replay survives
 * model upgrades.
 */
public interface EmbeddingModel {

    /**
     * Embed a single text into a fixed-dimension vector.
     */
    Embedding embed(String tenantId, String text);

    /**
     * Batch-embed a list of texts; ordering matches the input list.
     *
     * @return same-size list of embeddings; never null.
     */
    List<Embedding> embedAll(String tenantId, List<String> texts);

    /**
     * Stable version string of the underlying embedding model
     * (e.g. {@code "openai/text-embedding-3-small@2024-01"}).
     */
    String modelVersion();

    /**
     * Output dimensionality of the embedding vectors produced by
     * this model. Stable for the lifetime of one
     * {@link #modelVersion()}.
     */
    int dimensionality();
}
