package com.huawei.ascend.collab.a2a;

import com.huawei.ascend.collab.core.SubTask;
import com.huawei.ascend.collab.core.TaskToken;
import com.huawei.ascend.collab.core.WorkResult;
import com.huawei.ascend.collab.core.Worker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Bridges the collaboration engine to a real A2A agent: the {@link com.huawei.ascend.collab.core.Coordinator}
 * dispatches a {@link SubTask} to this worker, which sends it to a remote A2A
 * endpoint over the SDK {@link ClientTransport}, carrying the {@link TaskToken}
 * on the message metadata, and maps the returned {@link Task} state to a
 * {@link WorkResult}. The same Coordinator therefore orchestrates real A2A agents
 * (this worker) or, in the eval harness, deterministic in-memory workers.
 *
 * <p>Token echo: A2A correlates by {@code taskId}/{@code contextId} and the
 * endpoint is operator-configured, so on a correlated response this worker
 * re-presents the issued token (the metadata-carried token is the
 * idempotency/deadline credential). A token-aware remote may additionally
 * round-trip it in {@code task.metadata} for stronger proof.
 */
public final class A2aWorker implements Worker {

    /** Metadata keys the task token rides on, into {@code RequestContext.getMessage().metadata()}. */
    public static final String MK_TOKEN = "task.token.id";
    public static final String MK_TASK = "task.token.task";
    public static final String MK_IDEM = "task.token.idempotencyKey";
    public static final String MK_DEADLINE = "task.token.deadlineEpochMs";

    private final String id;
    private final Set<String> capabilities;
    private final ClientTransport transport;

    public A2aWorker(String id, Set<String> capabilities, String baseUrl) {
        this.id = id;
        this.capabilities = Set.copyOf(capabilities);
        this.transport = new JSONRPCTransport(baseUrl);
    }

    /** For tests/custom transports (e.g. a stub). */
    public A2aWorker(String id, Set<String> capabilities, ClientTransport transport) {
        this.id = id;
        this.capabilities = Set.copyOf(capabilities);
        this.transport = transport;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<String> capabilities() {
        return capabilities;
    }

    @Override
    public WorkResult execute(SubTask task, TaskToken token) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(MK_TOKEN, token.tokenId().toString());
            meta.put(MK_TASK, token.taskId());
            meta.put(MK_IDEM, token.idempotencyKey().toString());
            meta.put(MK_DEADLINE, token.deadlineEpochMs());

            Message message = Message.builder()
                    .role(Message.Role.ROLE_USER)
                    .messageId(UUID.randomUUID().toString())
                    .parts(List.<Part<?>>of(new TextPart(task.payload() == null ? "" : task.payload())))
                    .metadata(meta)
                    .build();
            MessageSendParams params = MessageSendParams.builder()
                    .message(message)
                    .metadata(meta)
                    .tenant(token.tenantId())
                    .build();
            ClientCallContext ctx = new ClientCallContext(Map.of(),
                    Map.of("X-Tenant-Id", token.tenantId()));

            EventKind event = transport.sendMessage(params, ctx);
            return map(task, token, event);
        } catch (Exception e) {
            return WorkResult.failed(task.id(), token, id, "a2a error: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    /** Reclaim a remote task (used by an orchestrator on timeout/reassignment). */
    public void cancelRemote(String remoteTaskId, String tenantId) {
        try {
            transport.cancelTask(new CancelTaskParams(remoteTaskId),
                    new ClientCallContext(Map.of(), Map.of("X-Tenant-Id", tenantId)));
        } catch (Exception ignored) {
            // best-effort reclaim
        }
    }

    private WorkResult map(SubTask task, TaskToken token, EventKind event) {
        if (event instanceof Task t) {
            TaskStatus status = t.status();
            TaskState state = status == null ? null : status.state();
            String text = status == null ? null : textOf(status.message());
            if (state == TaskState.TASK_STATE_COMPLETED) {
                return WorkResult.completed(task.id(), text == null || text.isBlank() ? "completed" : text, token, id);
            }
            if (state == TaskState.TASK_STATE_INPUT_REQUIRED || state == TaskState.TASK_STATE_AUTH_REQUIRED) {
                return new WorkResult(task.id(), WorkResult.Status.INPUT_REQUIRED, null, token, id, null,
                        "remote requires input");
            }
            if (state == TaskState.TASK_STATE_CANCELED) {
                return WorkResult.timeout(task.id(), token, id);
            }
            return WorkResult.failed(task.id(), token, id, "remote state " + state);
        }
        if (event instanceof Message m) {
            String text = textOf(m);
            return WorkResult.completed(task.id(), text == null || text.isBlank() ? "completed" : text, token, id);
        }
        return WorkResult.failed(task.id(), token, id, "unexpected event kind");
    }

    private static String textOf(Message message) {
        if (message == null || message.parts() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Part<?> p : message.parts()) {
            if (p instanceof TextPart tp) {
                sb.append(tp.text());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
