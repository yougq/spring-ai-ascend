package com.huawei.ascend.middleware.memory.spi;

import com.huawei.ascend.middleware.model.spi.Message;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped windowed conversation memory: a {@link MemoryStore}
 * variant whose key is a conversation id and whose value is an
 * ordered list of {@link Message} turns, with explicit token-budget
 * pruning and summarisation hooks.
 *
 * <p>Authority: ADR-0133 (extends ADR-0123 unified Memory SPI).
 *
 * <p>Category defaults to {@link MemoryCategory#M2_EPISODIC}.
 *
 * <p>Implementations MUST:
 * <ul>
 *   <li>preserve insertion order across {@link #addMessages};</li>
 *   <li>return at most {@code maxTokenBudget} worth of messages from
 *       {@link #getMessagesUpToBudget}, dropping OLDEST first (FIFO);</li>
 *   <li>treat {@link #summariseAndCompact} as a no-op when the
 *       conversation has fewer than {@code keepLastN} turns;</li>
 *   <li>fail closed on blank {@code tenantId} or {@code conversationId}.</li>
 * </ul>
 *
 * <p>Token counting: implementations choose the tokenizer (provider
 * native, GPT-2, Llama, ...); the platform does not standardise the
 * counter at L0. Documented in the implementation, not the SPI.
 *
 * <p>SPI purity per Rule R-D.
 */
public interface ConversationMemory extends MemoryStore<String, ConversationTurn> {

    /** Default category for ConversationMemory implementations. */
    @Override
    default MemoryCategory category() {
        return MemoryCategory.M2_EPISODIC;
    }

    /**
     * Append messages to the conversation.
     *
     * @param tenantId        owning tenant (Rule R-C.c); non-blank.
     * @param conversationId  conversation identifier; non-blank.
     * @param messages        ordered list of messages to append;
     *                        never null, may be empty (no-op).
     */
    void addMessages(String tenantId, String conversationId, List<Message> messages);

    /**
     * Return the suffix of the conversation that fits within
     * {@code maxTokenBudget} tokens, computed by the implementation's
     * tokenizer. Oldest turns are dropped first.
     *
     * @param tenantId        owning tenant; non-blank.
     * @param conversationId  conversation identifier; non-blank.
     * @param maxTokenBudget  upper bound on the returned message
     *                        token sum; MUST be positive.
     * @return ordered list of messages whose total token count is
     *         &le; {@code maxTokenBudget}; never null, may be empty.
     */
    List<Message> getMessagesUpToBudget(String tenantId, String conversationId, int maxTokenBudget);

    /**
     * Replace the OLDEST {@code totalTurns - keepLastN} turns of the
     * conversation with a single synthetic AssistantMessage summary
     * produced by the implementation. Implementations MAY call a
     * ModelGateway to generate the summary; that detail is opaque to
     * the SPI surface.
     *
     * @param tenantId        owning tenant; non-blank.
     * @param conversationId  conversation identifier; non-blank.
     * @param keepLastN       number of trailing turns to preserve
     *                        verbatim; MUST be non-negative.
     * @return the summary message inserted at the head of the kept
     *         window, or {@link Optional#empty()} when no compaction
     *         happened (conversation had &le; keepLastN turns).
     */
    Optional<Message> summariseAndCompact(String tenantId, String conversationId, int keepLastN);
}
