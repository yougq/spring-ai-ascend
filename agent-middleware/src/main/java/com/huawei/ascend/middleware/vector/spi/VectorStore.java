package com.huawei.ascend.middleware.vector.spi;

import java.util.List;

/**
 * Tenant-scoped vector storage with similarity search.
 *
 * <p>Authority: ADR-0124.
 *
 * <p>Implementations:
 * <ul>
 *   <li>MUST validate {@code tenantId} non-blank (Rule R-C.c).</li>
 *   <li>MUST scope every read/write to the calling tenant; cross-tenant
 *       reads return empty / cross-tenant writes throw.</li>
 *   <li>SHOULD route through {@code HookDispatcher.fire(BEFORE_MEMORY,
 *       ...)} and {@code AFTER_MEMORY}; hook outcomes (ADR-0073).</li>
 * </ul>
 *
 * <p>SPI purity per Rule R-D.
 */
public interface VectorStore {

    /**
     * Persist documents in the tenant-scoped store. Implementations
     * SHOULD embed any document whose {@link Document#embedding()}
     * is null using their configured {@code EmbeddingModel}.
     */
    void add(String tenantId, List<Document> documents);

    /**
     * Remove documents by id from the tenant-scoped store.
     *
     * @param documentIds non-null, may be empty.
     */
    void delete(String tenantId, List<String> documentIds);

    /**
     * Top-K similarity search over the tenant-scoped store.
     *
     * @return matches ordered by descending similarity; never null,
     *         possibly empty.
     */
    List<VectorMatch> similaritySearch(String tenantId, VectorQuery query);
}
