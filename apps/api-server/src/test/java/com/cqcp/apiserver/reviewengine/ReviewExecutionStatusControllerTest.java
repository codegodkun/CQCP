package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewExecutionStatusModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewExecutionStatusController.class)
@Import(ReviewExecutionStatusExceptionHandler.class)
class ReviewExecutionStatusControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ReviewExecutionStatusService service;

    private static final String TASK_ID = "task-001";
    private static final String EXEC_ID = "exec-001";

    private ReviewExecutionStatusResponse validResponse() {
        var model = new ReviewModelSummary(
                "MVP_DEMO_MOCK", "MOCK", "cqcp-demo-mock", "mock-local", "v1");
        return new ReviewExecutionStatusResponse(
                TASK_ID, EXEC_ID, "QUEUED", "QUEUED", false, false,
                "/results/task-001", model,
                Instant.parse("2026-07-26T10:00:00Z"),
                Instant.parse("2026-07-26T10:01:00Z"),
                false, null);
    }

    // ── AC1: 200 OK ──

    @Test
    void ac1_returns200WithAllFields() throws Exception {
        when(service.findStatus(any(), any())).thenReturn(validResponse());

        mockMvc.perform(get("/api/review/tasks/{taskId}/executions/{executionId}", TASK_ID, EXEC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(TASK_ID))
                .andExpect(jsonPath("$.executionId").value(EXEC_ID))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.currentStage").value("QUEUED"))
                .andExpect(jsonPath("$.terminal").value(false))
                .andExpect(jsonPath("$.snapshotAvailable").value(false))
                .andExpect(jsonPath("$.resultUrl").value("/results/task-001"))
                .andExpect(jsonPath("$.reviewModel.modelProfileCode").value("MVP_DEMO_MOCK"))
                .andExpect(jsonPath("$.reviewModel.providerType").value("MOCK"))
                .andExpect(jsonPath("$.reviewModel.modelName").value("cqcp-demo-mock"))
                .andExpect(jsonPath("$.reviewModel.endpointAlias").value("mock-local"))
                .andExpect(jsonPath("$.reviewModel.modelConfigVersion").value("v1"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString());
    }

    // ── AC5: 404 ──

    @Test
    void ac5_notFound_returns404() throws Exception {
        when(service.findStatus(any(), any())).thenThrow(new ExecutionNotFoundException());

        mockMvc.perform(get("/api/review/tasks/{taskId}/executions/{executionId}", TASK_ID, EXEC_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_EXECUTION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("未找到指定审核执行"))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.retryable").doesNotExist())
                .andExpect(jsonPath("$.operatorActionRequired").doesNotExist());
    }

    // ── AC11: JSON timestamp ──

    @Test
    void ac11_timestampsAreIso8601InJson() throws Exception {
        when(service.findStatus(any(), any())).thenReturn(validResponse());

        mockMvc.perform(get("/api/review/tasks/{taskId}/executions/{executionId}", TASK_ID, EXEC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value("2026-07-26T10:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-26T10:01:00Z"));
    }

    // ── AC12: field allowlist ──

    @Test
    void ac12_success_exactTopLevelAndReviewModelKeys() throws Exception {
        when(service.findStatus(any(), any())).thenReturn(validResponse());

        var body = mockMvc.perform(
                        get("/api/review/tasks/{taskId}/executions/{executionId}", TASK_ID, EXEC_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        var json = objectMapper.readTree(body);
        // Exact top-level key set (supersededReason omitted because it is null)
        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "taskId", "executionId", "status", "currentStage", "terminal",
                "snapshotAvailable", "resultUrl", "reviewModel",
                "createdAt", "updatedAt", "superseded");
        // Exact reviewModel key set
        assertThat(fieldNames(json.get("reviewModel"))).containsExactlyInAnyOrder(
                "modelProfileCode", "providerType", "modelName",
                "endpointAlias", "modelConfigVersion");
    }

    @Test
    void ac12_404_exactCodeAndMessageKeys() throws Exception {
        when(service.findStatus(any(), any())).thenThrow(new ExecutionNotFoundException());

        var body = mockMvc.perform(
                        get("/api/review/tasks/{taskId}/executions/{executionId}", TASK_ID, EXEC_ID))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        var json = objectMapper.readTree(body);
        assertThat(fieldNames(json)).containsExactlyInAnyOrder("code", "message");
        assertThat(json.get("code").asText()).isEqualTo("REVIEW_EXECUTION_NOT_FOUND");
        assertThat(json.get("message").asText()).isEqualTo("未找到指定审核执行");
    }

    // ── 500 must not leak exception content ──

    @Test
    void internalError_500DoesNotLeakSentinelOrExceptionContent() throws Exception {
        var sentinel = "SENSITIVE_SENTINEL_jdbc:postgresql://secret-host/db?password=hunter2";
        when(service.findStatus(any(), any())).thenThrow(new IllegalStateException(sentinel));

        var body = mockMvc.perform(
                        get("/api/review/tasks/{taskId}/executions/{executionId}", TASK_ID, EXEC_ID))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        var json = objectMapper.readTree(body);
        // Exact safe shape and fixed wording
        assertThat(fieldNames(json)).containsExactlyInAnyOrder("code", "message");
        assertThat(json.get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(json.get("message").asText()).isEqualTo("审核执行状态查询失败");
        // No sentinel, exception text, or stack trace anywhere in the response
        assertThat(body)
                .doesNotContain("SENSITIVE_SENTINEL")
                .doesNotContain("hunter2")
                .doesNotContain("secret-host")
                .doesNotContain("IllegalStateException")
                .doesNotContain("stackTrace")
                .doesNotContain("exception");
    }

    private static List<String> fieldNames(JsonNode node) {
        var names = new ArrayList<String>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
