package com.cqcp.apiserver.reviewengine;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskResultQueryController.class)
@Import(TaskResultQueryExceptionHandler.class)
class TaskResultQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskResultQueryService service;

    @Test
    void returnsSnapshotWhenResultExists() throws Exception {
        when(service.getResult("task-001")).thenReturn(snapshot());

        mockMvc.perform(get("/api/v1/tasks/task-001/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("task-001"))
                .andExpect(jsonPath("$.executionId").value("execution-001"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void returnsNotFoundWhenTaskDoesNotExist() throws Exception {
        when(service.getResult("missing-task")).thenThrow(new TaskResultNotFoundException("missing-task"));

        mockMvc.perform(get("/api/v1/tasks/missing-task/result"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("TASK_RESULT_NOT_FOUND"));
    }

    @Test
    void returnsConflictWhenTaskResultNotReady() throws Exception {
        when(service.getResult("task-001")).thenThrow(new TaskResultNotReadyException("task-001"));

        mockMvc.perform(get("/api/v1/tasks/task-001/result"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("TASK_RESULT_NOT_READY"));
    }

    private ReviewResultSnapshot snapshot() {
        return new ReviewResultSnapshot(
                "task-001",
                "execution-001",
                null,
                null,
                SnapshotStatus.SUCCESS,
                new ReviewSummary(1, 0, 0, 0, 0, 1),
                new ReviewCompleteness(
                        ReviewCoverageStatus.FULL_REVIEWED,
                        1,
                        1,
                        0,
                        BigDecimal.ONE,
                        ConfidenceLevel.HIGH),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of("contractTotalAmount", "1130"),
                List.of(),
                List.of(),
                "contract-type-v1",
                "ruleset-v1",
                "budget-v1",
                "model-v1",
                "parser-v1",
                "prompt-v1",
                "schema-v1",
                "pattern-v1",
                "lexicon-v1",
                "selector-v1",
                Instant.parse("2026-06-13T10:00:00Z"));
    }
}
