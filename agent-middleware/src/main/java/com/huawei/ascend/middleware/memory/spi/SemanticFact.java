package com.huawei.ascend.middleware.memory.spi;

import java.util.Objects;

/**
 * Value type for M3 ({@link MemoryCategory#M3_SEMANTIC}) entries.
 *
 * <p>Authority: ADR-0123.
 *
 * @param subject   subject of the fact.
 * @param statement free-form statement asserting something about subject.
 * @param confidence in [0.0, 1.0]; provider-defined semantics.
 */
public record SemanticFact(String subject, String statement, double confidence) {
    public SemanticFact {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(statement, "statement");
    }
}
