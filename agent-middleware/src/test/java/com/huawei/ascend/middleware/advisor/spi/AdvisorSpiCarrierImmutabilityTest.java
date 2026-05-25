package com.huawei.ascend.middleware.advisor.spi;

import com.huawei.ascend.middleware.model.spi.Message;
import com.huawei.ascend.middleware.model.spi.ModelInvocation;
import com.huawei.ascend.middleware.model.spi.ModelResponse;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Carrier immutability + tenant scoping contract for the ChatAdvisor SPI
 * (ADR-0132).
 *
 * <p>{@link AdvisedRequest} and {@link AdvisedResponse} both:
 * <ul>
 *   <li>reject null and blank {@code tenantId} (Rule R-C.c);</li>
 *   <li>defensively copy {@code advisorContext} on construction so that
 *       mutating the input map after construction does not affect the
 *       record; and</li>
 *   <li>expose an unmodifiable {@code advisorContext} view that throws
 *       {@code UnsupportedOperationException} on mutation attempts.</li>
 * </ul>
 */
class AdvisorSpiCarrierImmutabilityTest {

    private static ModelInvocation sampleInvocation() {
        return new ModelInvocation(
                "tenant",
                "model",
                List.of(new Message.UserMessage("hello")),
                List.of(),
                Map.of(),
                Map.of("traceId", "trace"));
    }

    private static ModelResponse sampleResponse() {
        return new ModelResponse("answer", List.of(), "stop", null, Map.of("provider", "openai"));
    }

    @Test
    void advisedRequestRejectsNullFields() {
        assertThatThrownBy(() -> new AdvisedRequest(null, sampleInvocation(), Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdvisedRequest("tenant", null, Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdvisedRequest("tenant", sampleInvocation(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void advisedRequestRejectsBlankTenantId() {
        assertThatThrownBy(() -> new AdvisedRequest("", sampleInvocation(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new AdvisedRequest("   ", sampleInvocation(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void advisedRequestCopiesAdvisorContextAndIsUnmodifiable() {
        Map<String, Object> advisorContext = new HashMap<>(Map.of("redaction", "pii"));

        AdvisedRequest request = new AdvisedRequest("tenant", sampleInvocation(), advisorContext);

        advisorContext.put("redaction", "mutated");
        advisorContext.put("added", "after");

        assertThat(request.advisorContext()).containsEntry("redaction", "pii");
        assertThat(request.advisorContext()).doesNotContainKey("added");
        assertThatThrownBy(() -> request.advisorContext().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void advisedResponseRejectsNullFields() {
        assertThatThrownBy(() -> new AdvisedResponse(null, sampleResponse(), Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdvisedResponse("tenant", null, Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdvisedResponse("tenant", sampleResponse(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void advisedResponseRejectsBlankTenantId() {
        assertThatThrownBy(() -> new AdvisedResponse("", sampleResponse(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new AdvisedResponse("\t", sampleResponse(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void advisedResponseCopiesAdvisorContextAndIsUnmodifiable() {
        Map<String, Object> advisorContext = new HashMap<>(Map.of("cost", 0.12));

        AdvisedResponse response = new AdvisedResponse("tenant", sampleResponse(), advisorContext);

        advisorContext.put("cost", 9.99);
        advisorContext.put("added", "after");

        assertThat(response.advisorContext()).containsEntry("cost", 0.12);
        assertThat(response.advisorContext()).doesNotContainKey("added");
        assertThatThrownBy(() -> response.advisorContext().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
