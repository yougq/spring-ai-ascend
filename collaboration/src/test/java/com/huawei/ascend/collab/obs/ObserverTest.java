package com.huawei.ascend.collab.obs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.huawei.ascend.collab.core.CollaborationObserver;
import com.huawei.ascend.collab.core.CoordinationEvent;
import com.huawei.ascend.collab.core.CoordinationEvent.Type;
import com.huawei.ascend.collab.core.WorkResult.Status;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Composite fan-out (incl. fault isolation) and MDC leak-safety of the structured observer. */
class ObserverTest {

    private static final class Recording implements CollaborationObserver {
        final List<String> events = new ArrayList<>();
        final List<String> completions = new ArrayList<>();

        @Override
        public void onEvent(CoordinationEvent e) {
            events.add(e.type().name());
        }

        @Override
        public void onTaskCompleted(String taskId, Status status, long durationMs) {
            completions.add(taskId + ":" + status);
        }
    }

    @Test
    void compositeFansOutToEveryDelegate() {
        Recording a = new Recording();
        Recording b = new Recording();
        CompositeCollaborationObserver obs = CompositeCollaborationObserver.of(a, b);

        obs.onEvent(new CoordinationEvent("t1", Type.DISPATCH, "w", null));
        obs.onTaskCompleted("t1", Status.COMPLETED, 5);

        assertEquals(List.of("DISPATCH"), a.events);
        assertEquals(List.of("DISPATCH"), b.events);
        assertEquals(List.of("t1:COMPLETED"), a.completions);
        assertEquals(List.of("t1:COMPLETED"), b.completions);
    }

    @Test
    void aFailingDelegateDoesNotBreakTheOthers() {
        CollaborationObserver boom = new CollaborationObserver() {
            @Override
            public void onEvent(CoordinationEvent e) {
                throw new RuntimeException("boom");
            }

            @Override
            public void onTaskCompleted(String t, Status s, long d) {
                throw new RuntimeException("boom");
            }
        };
        Recording good = new Recording();
        CompositeCollaborationObserver obs = CompositeCollaborationObserver.of(boom, good);

        obs.onEvent(new CoordinationEvent("t1", Type.FAIL, "w", null));
        obs.onTaskCompleted("t1", Status.FAILED, 1);

        assertEquals(List.of("FAIL"), good.events, "the healthy delegate still observed");
        assertEquals(List.of("t1:FAILED"), good.completions);
    }

    @Test
    void structuredObserverLeavesNoMdcBehind() {
        Slf4jCollaborationObserver obs = new Slf4jCollaborationObserver();

        obs.onEvent(new CoordinationEvent("t1", Type.DISPATCH, "w", "detail"));
        obs.onTaskCompleted("t1", Status.COMPLETED, 7);

        assertNull(MDC.get("taskId"), "MDC taskId cleared");
        assertNull(MDC.get("event"), "MDC event cleared");
        assertNull(MDC.get("workerId"), "MDC workerId cleared");
        assertNull(MDC.get("outcome"), "MDC outcome cleared");
        assertNull(MDC.get("durationMs"), "MDC durationMs cleared");
    }
}
