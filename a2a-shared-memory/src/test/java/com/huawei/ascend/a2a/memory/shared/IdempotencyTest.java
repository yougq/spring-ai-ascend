package com.huawei.ascend.a2a.memory.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** Retry-safe writes: a repeated write with the same idempotency key never double-appends. */
class IdempotencyTest {

    private final SharedMemoryStore store = new InMemorySharedMemoryStore(() -> 1L);

    @Test
    void sameIdempotencyKeyDoesNotDoubleAppend() {
        SharedEntry first = store.append("bank", "c1", "risk", "C3", "risk-agent", "idem-1");
        SharedEntry retry = store.append("bank", "c1", "risk", "C3", "risk-agent", "idem-1");

        assertSame(first, retry, "retry returns the prior entry, not a new append");
        assertEquals(1, store.history("bank", "c1", "risk").size(), "exactly one version");
        assertEquals(1, first.version());
    }

    @Test
    void differentIdempotencyKeysAppendDistinctVersions() {
        store.append("bank", "c1", "risk", "C3", "risk-agent", "idem-1");
        store.append("bank", "c1", "risk", "C2", "risk-agent", "idem-2");
        assertEquals(2, store.history("bank", "c1", "risk").size(), "distinct keys => distinct versions");
        assertEquals("C2", store.latest("bank", "c1", "risk").orElseThrow().value());
    }

    @Test
    void nullIdempotencyKeyAlwaysAppends() {
        store.append("bank", "c1", "risk", "C3", "risk-agent");
        store.append("bank", "c1", "risk", "C3", "risk-agent");
        assertEquals(2, store.history("bank", "c1", "risk").size(), "no idem key => non-idempotent");
    }

    @Test
    void idempotentRetryStillHonoursOwnership() {
        store.append("bank", "c1", "risk", "C3", "risk-agent", "idem-a");
        // a different agent with a fresh idem key is still rejected (ownership before idem record)
        org.junit.jupiter.api.Assertions.assertThrows(OwnershipViolationException.class,
                () -> store.append("bank", "c1", "risk", "x", "intruder", "idem-b"));
    }
}
