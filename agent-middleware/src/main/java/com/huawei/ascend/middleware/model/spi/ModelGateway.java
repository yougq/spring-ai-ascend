package com.huawei.ascend.middleware.model.spi;

import java.util.Objects;

/**
 * Tenant-scoped LLM invocation boundary.
 *
 * <p>Authority: ADR-0121. The reference adapter
 * {@code SpringAiChatModelGateway} (Wave C1) wraps Spring AI's
 * {@code ChatModel} (ADR-0125).
 *
 * <p>Implementations:
 * <ul>
 *   <li>MUST validate {@link ModelInvocation#tenantId()} non-blank
 *       (Rule R-C.c).</li>
 *   <li>SHOULD route through {@code HookDispatcher.fire(BEFORE_LLM,
 *       invocation)} before the underlying provider call and
 *       {@code AFTER_LLM} after; hook outcomes (ADR-0073).</li>
 *   <li>MAY consult {@code ResilienceContract.resolve(tenant,
 *       "model:" + invocation.modelId())} for per-model capacity
 *       arbitration.</li>
 *   <li>MUST be thread-safe; the runtime invokes from virtual
 *       threads.</li>
 * </ul>
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-package siblings.
 */
public interface ModelGateway {

    /**
     * Invoke the model with the given invocation envelope.
     *
     * @param invocation tenant-scoped invocation; never null.
     * @return the model's response; never null.
     */
    ModelResponse invoke(ModelInvocation invocation);

    /**
     * Stable id identifying this gateway instance for registry
     * lookup and hook attribution. Default returns the
     * implementation class name; impls SHOULD override with a
     * meaningful stable id.
     */
    default String gatewayId() {
        return Objects.requireNonNullElse(getClass().getSimpleName(), "model-gateway");
    }
}
