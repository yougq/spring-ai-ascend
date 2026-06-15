package com.huawei.ascend.collab.core;

import com.huawei.ascend.collab.core.CoordinationEvent.Type;
import com.huawei.ascend.collab.core.WorkResult.Status;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;

/**
 * The multi-agent coordinator. Implements the collaboration protocol on top of a
 * transport-agnostic {@link Worker} set:
 *
 * <ul>
 *   <li><b>分发 distribution</b> — routes each sub-task to a worker that handles its capability;</li>
 *   <li><b>任务令牌 task token</b> — issues a {@link TaskToken} per dispatch and <b>令牌响应校验</b>
 *       verifies the worker echoes a valid, matching, unexpired token (else REJECT);</li>
 *   <li><b>hand-over</b> — a worker may hand the task to another capability; the coordinator re-dispatches;</li>
 *   <li><b>回收 reclaim</b> — on timeout/failure/validation-fail it reclaims and redispatches (to a different
 *       worker when possible) up to {@code maxAttempts};</li>
 *   <li><b>校验 validation</b> — a {@link ResultValidator} gates COMPLETED results.</li>
 * </ul>
 *
 * {@link #run} executes tasks sequentially; {@link #runConcurrent} distributes them in parallel
 * (each task is independent — its own token lineage, attempts, and event log). Deterministic given
 * the workers and a clock, which is what makes the eval harness reproducible.
 */
public final class Coordinator {

    private static final int HANDOVER_CAP = 6;

    private final List<Worker> workers;
    private final ResultValidator validator;
    private final String tenantId;
    private final LongSupplier clock;
    private final CollaborationObserver observer;
    private final Map<String, Integer> roundRobin = new LinkedHashMap<>();

    public Coordinator(List<Worker> workers, ResultValidator validator, String tenantId,
            LongSupplier clock, CollaborationObserver observer) {
        this.workers = List.copyOf(workers);
        this.validator = validator == null ? ResultValidator.nonEmptyOutput() : validator;
        this.tenantId = tenantId == null ? "default" : tenantId;
        this.clock = clock == null ? System::currentTimeMillis : clock;
        this.observer = observer == null ? CollaborationObserver.NOOP : observer;
    }

    public Coordinator(List<Worker> workers, ResultValidator validator, String tenantId, LongSupplier clock) {
        this(workers, validator, tenantId, clock, CollaborationObserver.NOOP);
    }

    public Coordinator(List<Worker> workers) {
        this(workers, ResultValidator.nonEmptyOutput(), "default", System::currentTimeMillis);
    }

    /** Sequential distribution. */
    public CollaborationResult run(List<SubTask> tasks) {
        Map<String, Status> outcomes = new LinkedHashMap<>();
        List<CoordinationEvent> log = new ArrayList<>();
        for (SubTask task : tasks) {
            TaskRun run = runOne(task);
            outcomes.put(task.id(), run.status());
            log.addAll(run.events());
        }
        return new CollaborationResult(outcomes, log);
    }

    /** Concurrent distribution: tasks run in parallel (capped at {@code parallelism}). */
    public CollaborationResult runConcurrent(List<SubTask> tasks, int parallelism) {
        int pool = Math.max(1, Math.min(parallelism, Math.max(1, tasks.size())));
        ExecutorService exec = Executors.newFixedThreadPool(pool);
        try {
            Map<String, Future<TaskRun>> futures = new LinkedHashMap<>();
            for (SubTask task : tasks) {
                futures.put(task.id(), exec.submit(() -> runOne(task)));
            }
            Map<String, Status> outcomes = new LinkedHashMap<>();
            List<CoordinationEvent> log = new ArrayList<>();
            futures.forEach((taskId, future) -> {
                try {
                    TaskRun run = future.get();
                    outcomes.put(taskId, run.status());
                    log.addAll(run.events());
                } catch (Exception e) {
                    outcomes.put(taskId, Status.FAILED);
                    log.add(CoordinationEvent.of(taskId, Type.FAIL, null, "coordinator error: " + e.getMessage()));
                }
            });
            return new CollaborationResult(outcomes, log);
        } finally {
            exec.shutdownNow();
        }
    }

    private record TaskRun(Status status, List<CoordinationEvent> events) {
    }

    private TaskRun runOne(SubTask task) {
        List<CoordinationEvent> log = new ArrayList<>();
        long started = now();
        Status status = drive(task, log);
        observer.onTaskCompleted(task.id(), status, now() - started);
        return new TaskRun(status, log);
    }

    private Status drive(SubTask task, List<CoordinationEvent> log) {
        UUID idemKey = UUID.randomUUID();      // stable across this task's redispatch lineage
        String capability = task.capability();
        String lastWorkerId = null;
        int attempts = 0;
        int handovers = 0;

        while (true) {
            Worker w = pick(capability, lastWorkerId);
            if (w == null) {
                add(log, task.id(), Type.NO_WORKER, null, "capability=" + capability);
                return Status.FAILED;
            }
            TaskToken token = TaskToken.issue(task.id(), capability, w.id(), tenantId, idemKey, task.ttlMs(), now());
            add(log, task.id(), Type.DISPATCH, w.id(), "capability=" + capability + " token=" + token.tokenId());
            lastWorkerId = w.id();

            WorkResult r;
            try {
                r = w.execute(task, token);
            } catch (Exception e) {
                r = WorkResult.failed(task.id(), token, w.id(), "exception: " + e.getClass().getSimpleName());
            }

            if (!tokenMatches(token, r.echoedToken())) {
                add(log, task.id(), Type.TOKEN_REJECT, w.id(), "invalid/absent token echo");
                if (++attempts < task.maxAttempts()) {
                    add(log, task.id(), Type.RECLAIM, w.id(), "after token reject");
                    continue;
                }
                add(log, task.id(), Type.FAIL, w.id(), "token rejected, attempts exhausted");
                return Status.REJECTED;
            }

            switch (r.status()) {
                case COMPLETED -> {
                    if (validator.isValid(task, r)) {
                        add(log, task.id(), Type.VALIDATE_OK, w.id(), null);
                        add(log, task.id(), Type.COMPLETE, w.id(), null);
                        return Status.COMPLETED;
                    }
                    add(log, task.id(), Type.VALIDATE_FAIL, w.id(), "result rejected by validator");
                    if (++attempts < task.maxAttempts()) {
                        add(log, task.id(), Type.RECLAIM, w.id(), "after validate fail");
                        continue;
                    }
                    add(log, task.id(), Type.FAIL, w.id(), "validation failed, attempts exhausted");
                    return Status.FAILED;
                }
                case HANDED_OVER -> {
                    if (r.handoverCapability() == null || ++handovers > HANDOVER_CAP) {
                        add(log, task.id(), Type.FAIL, w.id(), "hand-over loop or no target");
                        return Status.FAILED;
                    }
                    add(log, task.id(), Type.HANDOVER, w.id(), "to=" + r.handoverCapability());
                    capability = r.handoverCapability();
                    lastWorkerId = null;
                }
                case TIMEOUT, FAILED -> {
                    add(log, task.id(), Type.RECLAIM, w.id(),
                            r.status() + (r.detail() == null ? "" : ": " + r.detail()));
                    if (++attempts < task.maxAttempts()) {
                        continue;
                    }
                    add(log, task.id(), Type.FAIL, w.id(), "attempts exhausted");
                    return r.status();
                }
                case INPUT_REQUIRED -> {
                    add(log, task.id(), Type.INPUT_REQUIRED, w.id(), "needs human input");
                    return Status.INPUT_REQUIRED;
                }
                case REJECTED -> {
                    add(log, task.id(), Type.FAIL, w.id(), "worker reported rejected");
                    return Status.REJECTED;
                }
                default -> {
                    return Status.FAILED;
                }
            }
        }
    }

    private void add(List<CoordinationEvent> log, String taskId, Type type, String workerId, String detail) {
        CoordinationEvent e = CoordinationEvent.of(taskId, type, workerId, detail);
        log.add(e);
        observer.onEvent(e);
    }

    private void emit(List<CoordinationEvent> log, Void ignored) {
        // events are emitted to the observer inline in add(); placeholder kept intentionally minimal
    }

    /** Round-robin among workers handling the capability, preferring one != excludeId for retry diversity. */
    private synchronized Worker pick(String capability, String excludeId) {
        List<Worker> candidates = new ArrayList<>();
        for (Worker w : workers) {
            if (w.handles(capability)) {
                candidates.add(w);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (excludeId != null && candidates.size() > 1) {
            candidates.removeIf(w -> w.id().equals(excludeId));
        }
        int idx = roundRobin.merge(capability, 1, Integer::sum) - 1;
        return candidates.get(Math.floorMod(idx, candidates.size()));
    }

    private boolean tokenMatches(TaskToken issued, TaskToken echoed) {
        if (echoed == null) {
            return false;
        }
        return issued.tokenId().equals(echoed.tokenId())
                && issued.taskId().equals(echoed.taskId())
                && issued.idempotencyKey().equals(echoed.idempotencyKey())
                && !echoed.isExpiredAt(now());
    }

    private long now() {
        return clock.getAsLong();
    }
}
