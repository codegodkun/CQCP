package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewExecutionStatusModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewExecutionStatusServiceTest {

    @Mock private ReviewExecutionStatusRepository repository;
    @InjectMocks private ReviewExecutionStatusService service;

    private static final String TASK_ID = "task-001";
    private static final String EXEC_ID = "exec-001";
    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-07-26T10:01:00Z");

    private ExecutionStatusRow validRow(String status, String stage, String providerType) {
        return new ExecutionStatusRow(TASK_ID, "/results/" + TASK_ID, EXEC_ID,
                status, stage, "MVP_DEMO_MOCK", providerType, "cqcp-demo-mock",
                "mock-local", "model-config-v1", NOW, UPDATED,
                null, null, null);
    }

    private ExecutionStatusRow validRow() {
        return validRow("QUEUED", "QUEUED", "MOCK");
    }

    // ── AC2: 13 internal statuses mapped ──

    @Test
    void ac2_all13StatusesMapped() {
        var cases = new String[][]{
            {"CREATED", "QUEUED"}, {"QUEUED", "QUEUED"},
            {"PARSING", "PROCESSING"}, {"INDEXING", "PROCESSING"},
            {"PLANNING", "PROCESSING"}, {"BUILDING_EVIDENCE", "PROCESSING"},
            {"REVIEWING_RULES", "PROCESSING"}, {"REVIEWING_MODEL", "PROCESSING"},
            {"COMPOSING", "PROCESSING"},
            {"SUCCESS", "SUCCESS"}, {"PARTIAL_SUCCESS", "PARTIAL_SUCCESS"},
            {"FAILED", "FAILED"}, {"CANCELLED", "FAILED"},
        };
        for (var c : cases) {
            assertThat(ReviewExecutionStatusService.mapPublicStatus(c[0])).isEqualTo(c[1]);
        }
    }

    @Test
    void ac2_unknownStatusThrows() {
        assertThatThrownBy(() -> ReviewExecutionStatusService.mapPublicStatus("BOGUS"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ac2_nullStatusThrows() {
        assertThatThrownBy(() -> ReviewExecutionStatusService.mapPublicStatus(null))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── AC3: currentStage passthrough ──

    @Test
    void ac3_currentStageReturnsAsIs() {
        when(repository.findStatus(TASK_ID, EXEC_ID))
                .thenReturn(Optional.of(validRow("FAILED", "PARSING", "MOCK")));
        var result = service.findStatus(TASK_ID, EXEC_ID);
        assertThat(result.currentStage()).isEqualTo("PARSING");
        assertThat(result.status()).isEqualTo("FAILED");
    }

    @Test
    void ac3_currentStagePassthrough_all13Stages() {
        var allStages = new String[]{
            "CREATED", "QUEUED", "PARSING", "INDEXING", "PLANNING",
            "BUILDING_EVIDENCE", "REVIEWING_RULES", "REVIEWING_MODEL", "COMPOSING",
            "SUCCESS", "PARTIAL_SUCCESS", "FAILED", "CANCELLED",
        };
        for (var stage : allStages) {
            when(repository.findStatus(TASK_ID, EXEC_ID))
                    .thenReturn(Optional.of(validRow("QUEUED", stage, "MOCK")));
            var result = service.findStatus(TASK_ID, EXEC_ID);
            assertThat(result.currentStage())
                    .as("currentStage passthrough for %s", stage)
                    .isEqualTo(stage);
            // Public status stays independent of currentStage
            assertThat(result.status()).isEqualTo("QUEUED");
        }
    }

    @Test
    void unknownCurrentStageFailsClosed() {
        when(repository.findStatus(TASK_ID, EXEC_ID))
                .thenReturn(Optional.of(validRow("QUEUED", "BOGUS", "MOCK")));
        assertThatThrownBy(() -> service.findStatus(TASK_ID, EXEC_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullCurrentStageFailsClosed() {
        when(repository.findStatus(TASK_ID, EXEC_ID))
                .thenReturn(Optional.of(validRow("QUEUED", null, "MOCK")));
        assertThatThrownBy(() -> service.findStatus(TASK_ID, EXEC_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── AC4: terminal ──

    @Test
    void ac4_terminalOnlyForTerminalStatuses() {
        // All 13 internal statuses: exactly the 4 terminal ones are true.
        var terminalStatuses = new String[]{"SUCCESS", "PARTIAL_SUCCESS", "FAILED", "CANCELLED"};
        var nonTerminalStatuses = new String[]{
            "CREATED", "QUEUED", "PARSING", "INDEXING", "PLANNING",
            "BUILDING_EVIDENCE", "REVIEWING_RULES", "REVIEWING_MODEL", "COMPOSING",
        };
        for (var s : terminalStatuses) {
            assertThat(ReviewExecutionStatusService.isTerminal(s))
                    .as("terminal for %s", s).isTrue();
        }
        for (var s : nonTerminalStatuses) {
            assertThat(ReviewExecutionStatusService.isTerminal(s))
                    .as("terminal for %s", s).isFalse();
        }
    }

    // ── AC9: superseded reason ──

    @Test
    void ac9_supersededReasonValidEnum() {
        var row = validRow();
        row = new ExecutionStatusRow(row.taskId(), row.resultUrl(), row.executionId(),
                row.status(), row.currentStage(), row.modelProfileCode(), row.providerType(),
                row.modelName(), row.endpointAlias(), row.modelConfigVersion(),
                row.createdAt(), row.updatedAt(),
                row.executionId(), "superseded-exec", "MANUAL_RERUN");
        when(repository.findStatus(TASK_ID, EXEC_ID)).thenReturn(Optional.of(row));
        var result = service.findStatus(TASK_ID, EXEC_ID);
        assertThat(result.superseded()).isTrue();
        assertThat(result.supersededReason()).isEqualTo("MANUAL_RERUN");
    }

    @Test
    void ac9_supersededReasonInvalidThrows() {
        var row = validRow();
        row = new ExecutionStatusRow(row.taskId(), row.resultUrl(), row.executionId(),
                row.status(), row.currentStage(), row.modelProfileCode(), row.providerType(),
                row.modelName(), row.endpointAlias(), row.modelConfigVersion(),
                row.createdAt(), row.updatedAt(),
                row.executionId(), "superseded-exec", "BOGUS_REASON");
        when(repository.findStatus(TASK_ID, EXEC_ID)).thenReturn(Optional.of(row));
        assertThatThrownBy(() -> service.findStatus(TASK_ID, EXEC_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ac9_noSuperseded_returnsFalse() {
        when(repository.findStatus(TASK_ID, EXEC_ID)).thenReturn(Optional.of(validRow()));
        var result = service.findStatus(TASK_ID, EXEC_ID);
        assertThat(result.superseded()).isFalse();
        assertThat(result.supersededReason()).isNull();
    }

    // ── AC10: reviewModel ──

    @Test
    void ac10_reviewModelFieldsMatchExecution() {
        when(repository.findStatus(TASK_ID, EXEC_ID)).thenReturn(Optional.of(validRow()));
        var result = service.findStatus(TASK_ID, EXEC_ID);
        assertThat(result.reviewModel().modelProfileCode()).isEqualTo("MVP_DEMO_MOCK");
        assertThat(result.reviewModel().providerType()).isEqualTo("MOCK");
        assertThat(result.reviewModel().modelName()).isEqualTo("cqcp-demo-mock");
        assertThat(result.reviewModel().endpointAlias()).isEqualTo("mock-local");
        assertThat(result.reviewModel().modelConfigVersion()).isEqualTo("model-config-v1");
    }

    @Test
    void unknownProviderTypeFailsClosed() {
        when(repository.findStatus(TASK_ID, EXEC_ID))
                .thenReturn(Optional.of(validRow("QUEUED", "QUEUED", "OPENAI")));
        assertThatThrownBy(() -> service.findStatus(TASK_ID, EXEC_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullRequiredFieldFailsClosed() {
        var badRow = new ExecutionStatusRow(TASK_ID, "/results/" + TASK_ID, EXEC_ID,
                "QUEUED", "QUEUED", "MVP_DEMO_MOCK", "MOCK", null,
                "mock-local", "model-config-v1", NOW, UPDATED, null, null, null);
        when(repository.findStatus(TASK_ID, EXEC_ID)).thenReturn(Optional.of(badRow));
        assertThatThrownBy(() -> service.findStatus(TASK_ID, EXEC_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── AC11: Instant mapping ──

    @Test
    void ac11_timestampsPreserved() {
        when(repository.findStatus(TASK_ID, EXEC_ID)).thenReturn(Optional.of(validRow()));
        var result = service.findStatus(TASK_ID, EXEC_ID);
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.updatedAt()).isEqualTo(UPDATED);
    }

    // ── snapshotAvailable ──

    @Test
    void snapshotAvailableTrueWhenSnapshotExists() {
        var row = validRow();
        row = new ExecutionStatusRow(row.taskId(), row.resultUrl(), row.executionId(),
                row.status(), row.currentStage(), row.modelProfileCode(), row.providerType(),
                row.modelName(), row.endpointAlias(), row.modelConfigVersion(),
                row.createdAt(), row.updatedAt(),
                EXEC_ID, null, null);
        when(repository.findStatus(TASK_ID, EXEC_ID)).thenReturn(Optional.of(row));
        var result = service.findStatus(TASK_ID, EXEC_ID);
        assertThat(result.snapshotAvailable()).isTrue();
    }

    @Test
    void snapshotAvailableFalseWhenNoSnapshot() {
        when(repository.findStatus(TASK_ID, EXEC_ID)).thenReturn(Optional.of(validRow()));
        var result = service.findStatus(TASK_ID, EXEC_ID);
        assertThat(result.snapshotAvailable()).isFalse();
    }

    // ── not found ──

    @Test
    void notFoundThrowsException() {
        when(repository.findStatus(TASK_ID, EXEC_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findStatus(TASK_ID, EXEC_ID))
                .isInstanceOf(ExecutionNotFoundException.class);
    }

    // ── resultUrl ──

    @Test
    void resultUrlDirect() {
        when(repository.findStatus(TASK_ID, EXEC_ID)).thenReturn(Optional.of(validRow()));
        var result = service.findStatus(TASK_ID, EXEC_ID);
        assertThat(result.resultUrl()).isEqualTo("/results/" + TASK_ID);
    }
}
