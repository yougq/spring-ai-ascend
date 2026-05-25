package com.huawei.ascend.middleware.memory.spi;

import com.huawei.ascend.middleware.model.spi.Message;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationMemoryCarrierImmutabilityTest {

    @Test
    void conversationTurnRejectsNullMessage() {
        assertThatThrownBy(() -> new ConversationTurn(null, Instant.EPOCH, 0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("message");
    }

    @Test
    void conversationTurnRejectsNullObservedAt() {
        Message message = new Message.UserMessage("hello");

        assertThatThrownBy(() -> new ConversationTurn(message, null, 0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("observedAt");
    }

    @Test
    void conversationTurnRejectsNegativeTokenCount() {
        Message message = new Message.UserMessage("hello");

        assertThatThrownBy(() -> new ConversationTurn(message, Instant.EPOCH, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenCount");
    }

    @Test
    void conversationTurnAcceptsZeroTokenCount() {
        Message message = new Message.UserMessage("hello");

        ConversationTurn turn = new ConversationTurn(message, Instant.EPOCH, 0);

        assertThat(turn.message()).isSameAs(message);
        assertThat(turn.observedAt()).isEqualTo(Instant.EPOCH);
        assertThat(turn.tokenCount()).isZero();
    }
}
