package com.huawei.ascend.service.agent.spi;

import java.util.Objects;

/**
 * Opaque reference to a
 * {@link com.huawei.ascend.middleware.model.spi.ModelGateway}
 * by id.
 *
 * <p>Authority: ADR-0128 + ADR-0121.
 */
public record ModelRef(String modelGatewayId) {
    public ModelRef {
        Objects.requireNonNull(modelGatewayId, "modelGatewayId");
        if (modelGatewayId.isBlank()) {
            throw new IllegalArgumentException("modelGatewayId must be non-blank");
        }
    }
}
