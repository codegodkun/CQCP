package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskResultQueryServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-13T10:00:00Z"), ZoneOffset.UTC);

    private final MinimalReviewEngine engine = new MinimalReviewEngine();
    private final ResultComposer composer = new ResultComposer();

    @Test
    void returnsSnapshotWhenTaskAlreadyHasResult() {
        var store = new InMemoryTaskResultStore();
        var stateMachine = new TaskExecutionStateMachine(engine, composer, FIXED_CLOCK);
        var executionResult = stateMachine.execute(successRequest(), store);
        var service = new TaskResultQueryService(store);

        var snapshot = service.getResult("task-001");

        assertThat(snapshot).isEqualTo(executionResult.snapshot());
        assertThat(snapshot.executionId()).isEqualTo("execution-001");
    }

    @Test
    void throwsNotFoundWhenTaskDoesNotExist() {
        var service = new TaskResultQueryService(new InMemoryTaskResultStore());

        assertThatThrownBy(() -> service.getResult("missing-task"))
                .isInstanceOf(TaskResultNotFoundException.class)
                .hasMessageContaining("missing-task");
    }

    @Test
    void throwsConflictWhenTaskExistsButResultNotReadyAndDoesNotChangeExecutionState() {
        var store = new InMemoryTaskResultStore();
        var execution = baseExecution(ExecutionStatus.REVIEWING_RULES);
        store.saveExecution(execution);
        var service = new TaskResultQueryService(store);

        assertThatThrownBy(() -> service.getResult("task-001"))
                .isInstanceOf(TaskResultNotReadyException.class)
                .hasMessageContaining("task-001");

        assertThat(store.findExecution("execution-001")).isEqualTo(execution);
    }

    private TaskExecutionRequest successRequest() {
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
                baseExecution(ExecutionStatus.CREATED),
                new ReviewEngineInput("task-001", "execution-001", "sample-001", structuredFields, defaultEvidence()),
                enabledPoints,
                List.of());
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
                null,
                List.of(new EvidenceSlotCoverage(
                        role.toLowerCase(java.util.Locale.ROOT),
                        true,
                        true,
                        EvidenceSlotCoverageStatus.SATISFIED,
                        null,
                        true)));
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
}
