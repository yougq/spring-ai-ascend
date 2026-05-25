package com.huawei.ascend.middleware.advisor.spi;

import com.huawei.ascend.middleware.model.spi.ModelInvocation;

import java.util.Map;
import java.util.Objects;

/**
 * Tenant-scoped advised request envelope passed along the
 * {@link ChatAdvisor} chain.
 *
 * <p>Authority: ADR-0132, schema at
 * {@code docs/contracts/chat-advisor.v1.yaml}.
 *
 * @param tenantId        owning tenant (Rule R-C.c); MUST be non-blank.
 * @param invocation      the underlying tenant-scoped model invocation
 *                        that downstream advisors / the terminal
 *                        {@code ModelGateway.invoke} call will receive.
 *                        Never null.
 * @param advisorContext  cross-advisor scratch map (e.g. PII
 *                        annotations, retrieval-augmentation traces,
 *                        cache lookup keys). The map is mutable across
 *                        chain calls but immutable per envelope:
 *                        advisors propagate state by constructing a new
 *                        {@code AdvisedRequest} for the next chain step.
 *                        Never null, may be empty.
 */
public record AdvisedRequest(
        String tenantId,
        ModelInvocation invocation,
        Map<String, Object> advisorContext) {

    public AdvisedRequest {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(advisorContext, "advisorContext");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank (Rule R-C.c)");
        }
        advisorContext = Map.copyOf(advisorContext);
    }
}
