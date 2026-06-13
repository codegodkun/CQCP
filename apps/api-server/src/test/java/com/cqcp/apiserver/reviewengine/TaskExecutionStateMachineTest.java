package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskExecutionStateMachineTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-13T10:00:00Z"), ZoneOffset.UTC);

    private final MinimalReviewEngine engine = new MinimalReviewEngine();
    private final ResultComposer composer = new ResultComposer();

    @Test
    void executesSuccessPathAndPersistsSnapshotAndStageLogs() {
        var persistence = new InMemoryTaskExecutionPersistence();
        var stateMachine = new TaskExecutionStateMachine(engine, composer, FIXED_CLOCK);

        var result = stateMachine.execute(successRequest(), persistence);

        assertThat(result.execution().status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.execution().currentStage()).isEqualTo("SUCCESS");
        assertThat(result.snapshot().status()).isEqualTo(SnapshotStatus.SUCCESS);
        assertThat(persistence.snapshots).hasSize(1);
        assertThat(persistence.snapshots.getFirst().findings()).isEmpty();
        assertThat(persistence.stageLogs)
                .extracting(TaskStageLogEntry::stageName, TaskStageLogEntry::eventType, TaskStageLogEntry::summaryStatus)
                .containsExactly(
                        tuple("REVIEWING_RULES", "STARTED", "RUNNING"),
                        tuple("REVIEWING_RULES", "COMPLETED", "SUCCESS"),
                        tuple("COMPOSING", "STARTED", "RUNNING"),
                        tuple("COMPOSING", "COMPLETED", "SUCCESS"));
    }

    @Test
    void executesPartialSuccessAndKeepsSysDiagnosticsOutOfBusinessFindings() {
        var persistence = new InMemoryTaskExecutionPersistence();
        var stateMachine = new TaskExecutionStateMachine(engine, composer, FIXED_CLOCK);

        var result = stateMachine.execute(partialSuccessRequest(), persistence);

        assertThat(result.execution().status()).isEqualTo(ExecutionStatus.PARTIAL_SUCCESS);
        assertThat(result.snapshot().status()).isEqualTo(SnapshotStatus.PARTIAL_SUCCESS);
        assertThat(result.snapshot().findings()).isEmpty();
        assertThat(result.snapshot().diagnostics())
                .extracting(com.cqcp.apiserver.tuning.PointDiagnostic::diagnosticCode)
                .containsExactly("SYS_MODEL_TIMEOUT");
    }

    @Test
    void marksExecutionFailedAndWritesFailureStageLogWhenReviewStageThrows() {
        var persistence = new InMemoryTaskExecutionPersistence();
        var failingEngine = new MinimalReviewEngine() {
            @Override
            public ReviewEngineResult review(ReviewEngineInput input) {
                throw new IllegalStateException("simulated review failure");
            }
        };
        var stateMachine = new TaskExecutionStateMachine(failingEngine, composer, FIXED_CLOCK);

        assertThatThrownBy(() -> stateMachine.execute(successRequest(), persistence))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated review failure");

        assertThat(persistence.savedExecutions).isNotEmpty();
        assertThat(persistence.savedExecutions.getLast().status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(persistence.savedExecutions.getLast().currentStage()).isEqualTo("REVIEWING_RULES");
        assertThat(persistence.snapshots).isEmpty();
        assertThat(persistence.stageLogs)
                .extracting(TaskStageLogEntry::stageName, TaskStageLogEntry::eventType, TaskStageLogEntry::summaryStatus)
                .containsExactly(
                        tuple("REVIEWING_RULES", "STARTED", "RUNNING"),
                        tuple("REVIEWING_RULES", "FAILED", "FAILED"));
    }

    @Test
    void rejectsReexecutionOfTerminalExecution() {
        var persistence = new InMemoryTaskExecutionPersistence();
        var stateMachine = new TaskExecutionStateMachine(engine, composer, FIXED_CLOCK);
        var terminalExecution = baseExecution(ExecutionStatus.SUCCESS);

        assertThatThrownBy(() -> stateMachine.execute(successRequest(terminalExecution), persistence))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal execution");

        assertThat(persistence.savedExecutions).isEmpty();
        assertThat(persistence.stageLogs).isEmpty();
        assertThat(persistence.snapshots).isEmpty();
    }

    private TaskExecutionRequest successRequest() {
        return successRequest(baseExecution(ExecutionStatus.CREATED));
    }

    private TaskExecutionRequest successRequest(TaskExecutionRecord execution) {
        var structuredFields = StructuredFieldSet.builder()
                .put("partyAName", "甲方公司")
                .put("partyBName", "乙方公司")
                .put("contractTotalAmount", "1130")
                .put("taxExcludedAmount", "1000")
                .put("taxAmount", "130")
                .put("taxRate", "13")
                .put("paymentMethod", "MONTHLY")
                .put("prepaymentRatio", "20")
                .put("progressPaymentRatio", "60")
                .put("completionPaymentRatio", "80")
                .put("settlementPaymentRatio", "95")
                .put("warrantyRetentionRatio", "5")
                .build();

        var enabledPoints = List.of(
                reviewPointSnapshot(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "P001", 1),
                reviewPointSnapshot(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "P002", 2),
                reviewPointSnapshot(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, "P003", 3),
                reviewPointSnapshot(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, "P004", 4),
                reviewPointSnapshot(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, "P005", 5),
                reviewPointSnapshot(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, "P006", 6),
                reviewPointSnapshot(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, "P007", 7),
                reviewPointSnapshot(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, "P008", 8),
                reviewPointSnapshot(ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, "P009", 9));

        return new TaskExecutionRequest(
                new ReviewTaskRecord("task-001", "测试合同", structuredFields.asMap()),
                execution,
                new ReviewEngineInput("task-001", execution.executionId(), "sample-001", structuredFields, defaultEvidence()),
                enabledPoints,
                List.of());
    }

    private TaskExecutionRequest partialSuccessRequest() {
        var request = successRequest();
        var overriddenEvidence = new EnumMap<ReviewPointCode, PointEvidence>(ReviewPointCode.class);
        overriddenEvidence.putAll(request.reviewInput().pointEvidences());
        overriddenEvidence.put(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY,
                new PointEvidence(
                        ReviewPointCode.PARTY_B_NAME_CONSISTENCY,
                        "PARTY_B",
                        null,
                        EvidenceStatus.SYSTEM_FAILURE,
                        "NATIVE_WORD",
                        "STRUCTURED",
                        "NORMAL",
                        "block-party-b",
                        "HIGH",
                        "系统能力暂不可用",
                        "SYS_MODEL_TIMEOUT",
                        NotConcludedReasonCode.MODEL_UNAVAILABLE));

        return new TaskExecutionRequest(
                request.task(),
                request.execution(),
                new ReviewEngineInput(
                        request.reviewInput().taskId(),
                        request.reviewInput().executionId(),
                        request.reviewInput().sampleId(),
                        request.reviewInput().structuredFields(),
                        overriddenEvidence),
                request.enabledReviewPointsSnapshot(),
                request.disabledReviewPointsSnapshot());
    }

    private TaskExecutionRecord baseExecution(ExecutionStatus status) {
        return new TaskExecutionRecord(
                "execution-001",
                "task-001",
                status,
                status.name(),
                defaultVersionReferences(),
                "default-model-profile",
                "MOCK",
                "gemma-local",
                "local-gemma",
                null,
                null);
    }

    private Map<ReviewPointCode, PointEvidence> defaultEvidence() {
        var evidence = new EnumMap<ReviewPointCode, PointEvidence>(ReviewPointCode.class);
        evidence.put(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, confirmedEvidence(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", "甲方公司", "block-party-a"));
        evidence.put(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, confirmedEvidence(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "乙方公司", "block-party-b"));
        evidence.put(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, confirmedEvidence(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, "CONTRACT_TOTAL_AMOUNT", "1130", "block-total"));
        evidence.put(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, confirmedEvidence(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, "TAX_AMOUNT", "130", "block-tax"));
        evidence.put(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, confirmedEvidence(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, "PREPAYMENT_RATIO", "20", "block-prepay"));
        evidence.put(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, confirmedEvidence(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, "PROGRESS_PAYMENT_RATIO", "60", "block-progress"));
        evidence.put(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, confirmedEvidence(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, "COMPLETION_PAYMENT_RATIO", "80", "block-completion"));
        evidence.put(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, confirmedEvidence(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, "SETTLEMENT_PAYMENT_RATIO", "95", "block-settlement"));
        evidence.put(ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, confirmedEvidence(ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, "WARRANTY_RETENTION_RATIO", "5", "block-warranty"));
        return evidence;
    }

    private PointEvidence confirmedEvidence(
            ReviewPointCode code,
            String role,
            String value,
            String blockId) {
        return new PointEvidence(
                code,
                role,
                value,
                EvidenceStatus.CONFIRMED,
                "NATIVE_WORD",
                "STRUCTURED",
                "NORMAL",
                blockId,
                "HIGH",
                "测试证据",
                null,
                null);
    }

    private ReviewPointSnapshot reviewPointSnapshot(ReviewPointCode code, String displayCode, int displayOrder) {
        return new ReviewPointSnapshot(
                code,
                displayCode,
                code.name(),
                familyOf(code),
                "ENGINEERING_PROCUREMENT",
                code == ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY ? FindingSeverity.WARNING : FindingSeverity.ERROR,
                displayOrder);
    }

    private String familyOf(ReviewPointCode code) {
        return switch (code) {
            case PARTY_A_NAME_CONSISTENCY, PARTY_B_NAME_CONSISTENCY -> "PARTY_FIELDS";
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY, TAX_AMOUNT_FORMULA_CONSISTENCY -> "AMOUNT_TAX";
            case PREPAYMENT_RATIO_CONSISTENCY,
                    PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                    COMPLETION_PAYMENT_RATIO_CONSISTENCY,
                    SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
                    WARRANTY_RETENTION_RATIO_CONSISTENCY -> "PAYMENT_TERMS";
        };
    }

    private VersionReferences defaultVersionReferences() {
        return new VersionReferences(
                "contract-type-v1",
                "ruleset-v1",
                "budget-v1",
                "model-v1",
                "parser-v1",
                "prompt-v1",
                "schema-v1",
                "pattern-v1",
                "lexicon-v1",
                "selector-v1");
    }

    private static final class InMemoryTaskExecutionPersistence implements TaskExecutionPersistence {

        private final List<TaskExecutionRecord> savedExecutions = new ArrayList<>();
        private final List<TaskStageLogEntry> stageLogs = new ArrayList<>();
        private final List<ReviewResultSnapshot> snapshots = new ArrayList<>();

        @Override
        public void saveExecution(TaskExecutionRecord execution) {
            savedExecutions.add(execution);
        }

        @Override
        public void appendStageLog(TaskStageLogEntry entry) {
            stageLogs.add(entry);
        }

        @Override
        public void saveSnapshot(ReviewResultSnapshot snapshot) {
            snapshots.add(snapshot);
        }
    }
}
