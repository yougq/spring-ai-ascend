package com.huawei.ascend.middleware.retrieval.spi;

import com.huawei.ascend.middleware.vector.spi.Document;

import java.util.List;

/**
 * Tenant-scoped retrieval primitive.
 *
 * <p>Authority: ADR-0124. Composes one or more vector stores
 * (and optionally keyword indices) into a single
 * {@code retrieve(...)} call.
 *
 * <p>SPI cross-package dependency: this SPI declares
 * {@link com.huawei.ascend.middleware.vector.spi.Document} as the
 * return element type, mirroring the orchestration-SPI pattern
 * (Orchestrator depends on engine.orchestration.spi siblings).
 */
public interface Retriever {

    /**
     * Retrieve top documents for a free-text query.
     *
     * @return ordered hits (most relevant first); never null,
     *         possibly empty.
     */
    List<Document> retrieve(String tenantId, String query, RetrievalOptions options);
}
