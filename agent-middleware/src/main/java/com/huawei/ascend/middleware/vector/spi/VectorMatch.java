package com.huawei.ascend.middleware.vector.spi;

import java.util.Objects;

/**
 * One similarity-search hit.
 *
 * <p>Authority: ADR-0124.
 *
 * @param document the matched document.
 * @param score    similarity score in [0.0, 1.0]; provider-defined
 *                 metric (cosine / dot-product / euclidean).
 */
public record VectorMatch(Document document, double score) {
    public VectorMatch {
        Objects.requireNonNull(document, "document");
    }
}
