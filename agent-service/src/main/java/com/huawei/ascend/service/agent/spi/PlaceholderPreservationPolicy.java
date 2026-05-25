package com.huawei.ascend.service.agent.spi;

/**
 * Placeholder preservation policy (ADR-0051).
 *
 * <p>{@code PRESERVE} — placeholders in inputs MUST be passed
 * through to outputs untouched (default; matches ADR-0051
 * first-class rule).
 *
 * <p>{@code WARN} — placeholders may be rewritten but a warning is
 * surfaced through the hook chain.
 *
 * <p>{@code REWRITE} — placeholders may be rewritten silently
 * (typically reserved for prototype workflows).
 */
public enum PlaceholderPreservationPolicy {
    PRESERVE,
    WARN,
    REWRITE
}
