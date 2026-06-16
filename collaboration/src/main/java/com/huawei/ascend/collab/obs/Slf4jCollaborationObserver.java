package com.huawei.ascend.collab.obs;

import com.huawei.ascend.collab.core.CollaborationObserver;
import com.huawei.ascend.collab.core.CoordinationEvent;
import com.huawei.ascend.collab.core.WorkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Structured ops trail for the collaboration engine: every coordination decision and
 * each task completion is logged on the {@code collab} logger with MDC context
 * ({@code taskId}, {@code event}/{@code outcome}, {@code workerId}, {@code durationMs}),
 * so a log pipeline can index and correlate a whole collaboration by task id.
 *
 * <p>MDC keys are always removed in a {@code finally} block — a logging observer must not
 * leak context onto the worker/pool thread it runs on.
 */
public final class Slf4jCollaborationObserver implements CollaborationObserver {

    private static final Logger log = LoggerFactory.getLogger("collab");

    @Override
    public void onEvent(CoordinationEvent event) {
        MDC.put("taskId", event.taskId());
        MDC.put("event", event.type().name());
        if (event.workerId() != null) {
            MDC.put("workerId", event.workerId());
        }
        try {
            log.info("collab decision type={} task={} worker={} detail={}",
                    event.type(), event.taskId(), event.workerId(), event.detail());
        } finally {
            MDC.remove("taskId");
            MDC.remove("event");
            MDC.remove("workerId");
        }
    }

    @Override
    public void onTaskCompleted(String taskId, WorkResult.Status status, long durationMs) {
        MDC.put("taskId", taskId);
        MDC.put("outcome", status.name());
        MDC.put("durationMs", Long.toString(durationMs));
        try {
            log.info("collab task settled task={} outcome={} durationMs={}", taskId, status, durationMs);
        } finally {
            MDC.remove("taskId");
            MDC.remove("outcome");
            MDC.remove("durationMs");
        }
    }
}
