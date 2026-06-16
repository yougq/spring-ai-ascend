package com.huawei.ascend.a2a.memory.experience;

import java.util.List;

/**
 * Backend SPI for the cross-run experience layer (the a2a-shared-memory design decision). Keyed by
 * {@code tenantId + signature}; never by user. In-process
 * {@link InMemoryExperienceStore} for eval; gRPC client to the closed engine in
 * production. Implementations MUST isolate per tenant. Lessons arrive already
 * PII-stripped (the kit redacts before record).
 */
public interface ExperienceStore {

    /** Persist distilled lessons under a tenant's signature. */
    void record(String tenantId, CollaborationSignature signature, List<Lesson> lessons);

    /** Recall the most relevant lessons for a signature, best match first. */
    List<Lesson> recall(String tenantId, CollaborationSignature signature, int topK);
}
