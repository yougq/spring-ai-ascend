package com.huawei.ascend.middleware.memory.spi;

import com.huawei.ascend.middleware.model.spi.Message;

import java.time.Instant;
import java.util.Objects;

/**
 * One ordered conversation turn: the {@link Message} plus the
 * wall-clock instant at which the platform observed it.
 *
 * <p>Authority: ADR-0133.
 *
 * @param message    the turn message; never null.
 * @param observedAt server-side observation timestamp; never null.
 * @param tokenCount implementation-tokenizer count for this turn's
 *                   content; non-negative.
 */
public record ConversationTurn(Message message, Instant observedAt, int tokenCount) {

    public ConversationTurn {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(observedAt, "observedAt");
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must be non-negative");
        }
    }
}
