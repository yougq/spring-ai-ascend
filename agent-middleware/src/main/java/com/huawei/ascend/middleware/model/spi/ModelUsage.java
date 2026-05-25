package com.huawei.ascend.middleware.model.spi;

/**
 * Token usage reported by the model provider.
 *
 * <p>Authority: ADR-0121. Fed into the W3 cost-attribution
 * pipeline; nullable when the provider does not report usage.
 *
 * @param promptTokens     tokens in the prompt; -1 if unknown.
 * @param completionTokens tokens in the completion; -1 if unknown.
 * @param totalTokens      total tokens; -1 if unknown.
 */
public record ModelUsage(int promptTokens, int completionTokens, int totalTokens) {
}
