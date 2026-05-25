package com.huawei.ascend.middleware.memory.spi;

/**
 * Ownership boundary (ADR-0051).
 *
 * <p>C-side memory belongs to the customer's business ontology;
 * S-side memory belongs to the platform's run trajectory;
 * DELEGATED memory is platform-stored but C-owned by contract.
 */
public enum MemoryOwnership {
    C_SIDE,
    S_SIDE,
    DELEGATED
}
