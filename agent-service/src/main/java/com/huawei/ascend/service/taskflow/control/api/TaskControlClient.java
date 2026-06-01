package com.huawei.ascend.service.taskflow.control.api;

import com.huawei.ascend.service.taskflow.control.TaskFailureCode;
import com.huawei.ascend.service.taskflow.control.TaskState;
import com.huawei.ascend.service.taskflow.control.WaitingReason;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Internal L4 task-control API.
 *
 * <p>This is not a Service Provider Interface. L1 calls runTask as the single
 * task intent entrypoint; runtime adapters call mark* methods to report state
 * intent. Runtime code must not publish directly to, or consume directly from,
 * the L3 queue.
 */
public interface TaskControlClient {

    /**
     * Single L1-facing entrypoint. TaskAction reserves RUN, RESUME_INPUT, and
     * CANCEL without adding separate handler methods.
     */
    CompletionStage<TaskResult> runTask(RunTaskCommand command);

    /**
     * Runtime-adapter state ingress. Implementations validate transitions and
     * update the L4 task snapshot.
     */
    CompletionStage<TaskResult> markRunning(MarkTaskCommand command);

    /**
     * Runtime-adapter state ingress. Implementations validate transitions and
     * update the L4 task snapshot.
     */
    CompletionStage<TaskResult> markWaiting(MarkTaskCommand command);

    /**
     * Runtime-adapter state ingress. Implementations validate transitions and
     * update the L4 task snapshot.
     */
    CompletionStage<TaskResult> markSucceeded(MarkTaskCommand command);

    /**
     * Runtime-adapter state ingress. Implementations validate transitions and
     * update the L4 task snapshot.
     */
    CompletionStage<TaskResult> markFailed(MarkTaskCommand command);

    /**
     * Runtime-adapter state ingress. Implementations validate transitions and
     * update the L4 task snapshot.
     */
    CompletionStage<TaskResult> markCancelled(MarkTaskCommand command);

    enum TaskAction {
        RUN,
        RESUME_INPUT,
        CANCEL
    }

    record RunTaskCommand(
            String tenantId,
            String sessionId,
            String taskId,
            String agentId,
            TaskAction action,
            Object input,
            String reason,
            String idempotencyKey,
            Map<String, Object> metadata) {

        public RunTaskCommand {
            tenantId = requireNonBlank(tenantId, "tenantId");
            sessionId = requireNonBlank(sessionId, "sessionId");
            action = Objects.requireNonNull(action, "action");
            if (action == TaskAction.CANCEL) {
                taskId = requireNonBlank(taskId, "taskId");
            } else {
                Objects.requireNonNull(input, "input");
            }
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    record MarkTaskCommand(
            String tenantId,
            String sessionId,
            String taskId,
            long expectedRevision,
            WaitingReason waitingReason,
            TaskFailureCode failureCode,
            Object detail,
            Map<String, Object> metadata) {

        public MarkTaskCommand {
            tenantId = requireNonBlank(tenantId, "tenantId");
            sessionId = requireNonBlank(sessionId, "sessionId");
            taskId = requireNonBlank(taskId, "taskId");
            if (expectedRevision < 1L) {
                throw new IllegalArgumentException("expectedRevision must be positive");
            }
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    record TaskResult(
            String tenantId,
            String sessionId,
            String taskId,
            TaskState state,
            long revision,
            boolean accepted,
            String message) {

        public TaskResult {
            tenantId = requireNonBlank(tenantId, "tenantId");
            sessionId = requireNonBlank(sessionId, "sessionId");
            taskId = requireNonBlank(taskId, "taskId");
            Objects.requireNonNull(state, "state");
            if (revision < 1L) {
                throw new IllegalArgumentException("revision must be positive");
            }
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
