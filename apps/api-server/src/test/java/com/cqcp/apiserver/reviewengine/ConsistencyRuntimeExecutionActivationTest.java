package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.cqcp.apiserver.wordparser.DocxWordParserSpike;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsistencyRuntimeExecutionActivationTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC);
    private static final String LEGACY_VERSION = "v20260705.1";
    private static final String CONSISTENCY_VERSION = "v20260715.1";
    private static final Path SAMPLE_DOCX = Path.of(
            "..", "..", "packages", "test-fixtures", "docx",
            "2、达利安造船厂四方城翡翠大道项目北二期3强化地板产品采购合同_缩减版.docx").normalize();

    @Test
    void legacyExecutionUsesOnlyLegacyBuild() {
        var preparer = spy(new ParserBackedReviewInputPreparer(new DocxWordParserSpike()));
        var machine = machine(preparer, ConsistencyRuntimeRelease.accepted(), new MinimalReviewEngine());
        var persistence = new RecordingPersistence();

        var result = machine.execute(documentRequest(LEGACY_VERSION), persistence);

        assertThat(result.snapshot().ruleSetVersion()).isEqualTo(LEGACY_VERSION);
        verify(preparer).build(
                any(TaskExecutionRequest.class),
                any(EvidenceBuildPlan.class));
        verify(preparer, never()).build(
                any(TaskExecutionRequest.class),
                any(EvidenceBuildPlan.class),
                any(RuntimeRuleSetSnapshot.class));
    }

    @Test
    void acceptedConsistencyExecutionUsesSnapshotBuildAndPersistsVersion() {
        var preparer = spy(new ParserBackedReviewInputPreparer(new DocxWordParserSpike()));
        var machine = machine(preparer, ConsistencyRuntimeRelease.accepted(), new MinimalReviewEngine());
        var persistence = new RecordingPersistence();

        var result = machine.execute(documentRequest(CONSISTENCY_VERSION), persistence);

        assertThat(result.snapshot().ruleSetVersion()).isEqualTo(CONSISTENCY_VERSION);
        assertThat(result.execution().status())
                .isIn(ExecutionStatus.SUCCESS, ExecutionStatus.PARTIAL_SUCCESS);
        verify(preparer, never()).build(
                any(TaskExecutionRequest.class),
                any(EvidenceBuildPlan.class));
        verify(preparer).build(
                any(TaskExecutionRequest.class),
                any(EvidenceBuildPlan.class),
                any(RuntimeRuleSetSnapshot.class));
    }

    @Test
    void consistencyExecutionFailsClosedWhenReleaseIsNotReady() {
        var preparer = spy(new ParserBackedReviewInputPreparer(new DocxWordParserSpike()));
        var machine = machine(preparer, ConsistencyRuntimeRelease.disabledForTest(), new MinimalReviewEngine());
        var persistence = new RecordingPersistence();

        assertThatThrownBy(() -> machine.execute(documentRequest(CONSISTENCY_VERSION), persistence))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Rule-set activation rejected: POLICY_NOT_READY");

        assertThat(persistence.savedExecutions.getLast().status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(persistence.savedExecutions.getLast().currentStage()).isEqualTo("BUILDING_EVIDENCE");
        verify(preparer, never()).build(
                any(TaskExecutionRequest.class),
                any(EvidenceBuildPlan.class));
        verify(preparer, never()).build(
                any(TaskExecutionRequest.class),
                any(EvidenceBuildPlan.class),
                any(RuntimeRuleSetSnapshot.class));
    }

    @Test
    void unknownRuleSetVersionFailsClosedWithoutLegacyFallback() {
        var preparer = spy(new ParserBackedReviewInputPreparer(new DocxWordParserSpike()));
        var machine = machine(preparer, ConsistencyRuntimeRelease.accepted(), new MinimalReviewEngine());
        var persistence = new RecordingPersistence();

        assertThatThrownBy(() -> machine.execute(documentRequest("v20990101.1"), persistence))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Rule-set activation rejected: UNKNOWN_RULE_SET_VERSION");

        assertThat(persistence.savedExecutions.getLast().currentStage()).isEqualTo("BUILDING_EVIDENCE");
        verify(preparer, never()).build(
                any(TaskExecutionRequest.class),
                any(EvidenceBuildPlan.class));
        verify(preparer, never()).build(
                any(TaskExecutionRequest.class),
                any(EvidenceBuildPlan.class),
                any(RuntimeRuleSetSnapshot.class));
    }

    @Test
    void consistencyPrebuiltInputWithoutSnapshotNeverReachesEngine() {
        var countingEngine = new CountingReviewEngine();
        var machine = machine(
                new ParserBackedReviewInputPreparer(new DocxWordParserSpike()),
                ConsistencyRuntimeRelease.accepted(),
                countingEngine);
        var persistence = new RecordingPersistence();
        var request = prebuiltRequest(CONSISTENCY_VERSION, null);

        assertThatThrownBy(() -> machine.execute(request, persistence))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Consistency execution requires a runtime rule-set snapshot");

        assertThat(countingEngine.calls).isZero();
        assertThat(persistence.savedExecutions.getLast().currentStage()).isEqualTo("REVIEWING_RULES");
    }

    @Test
    void legacyPrebuiltInputWithConsistencySnapshotNeverReachesEngine() {
        var countingEngine = new CountingReviewEngine();
        var machine = machine(
                new ParserBackedReviewInputPreparer(new DocxWordParserSpike()),
                ConsistencyRuntimeRelease.accepted(),
                countingEngine);
        var persistence = new RecordingPersistence();
        var snapshot = new RuleSetActivationGate()
                .request(CONSISTENCY_VERSION, true)
                .snapshot();
        var request = prebuiltRequest(LEGACY_VERSION, snapshot);

        assertThatThrownBy(() -> machine.execute(request, persistence))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Legacy execution must not carry a runtime rule-set snapshot");

        assertThat(countingEngine.calls).isZero();
        assertThat(persistence.savedExecutions.getLast().currentStage()).isEqualTo("REVIEWING_RULES");
    }

    private TaskExecutionStateMachine machine(
            ParserBackedReviewInputPreparer preparer,
            ConsistencyRuntimeRelease release,
            MinimalReviewEngine engine) {
        return new TaskExecutionStateMachine(
                engine,
                new ResultComposer(),
                preparer,
                new RuleSetActivationGate(),
                release,
                FIXED_CLOCK);
    }

    private TaskExecutionRequest documentRequest(String ruleSetVersion) {
        var execution = execution(ruleSetVersion);
        return TaskExecutionRequest.forDocument(
                new ReviewTaskRecord("task-c2", "测试合同", structuredFields()),
                execution,
                new TaskExecutionDocumentReference(SAMPLE_DOCX, "CQCP-MVP-DOCX-002"),
                List.of(),
                List.of());
    }

    private TaskExecutionRequest prebuiltRequest(
            String ruleSetVersion,
            RuntimeRuleSetSnapshot runtimeSnapshot) {
        var execution = execution(ruleSetVersion);
        var input = new ReviewEngineInput(
                "task-c2",
                execution.executionId(),
                "sample-c2",
                StructuredFieldSet.fromMap(structuredFields()),
                new EnumMap<>(ReviewPointCode.class),
                runtimeSnapshot);
        return new TaskExecutionRequest(
                new ReviewTaskRecord("task-c2", "测试合同", structuredFields()),
                execution,
                input,
                List.of(),
                List.of());
    }

    private TaskExecutionRecord execution(String ruleSetVersion) {
        return new TaskExecutionRecord(
                "execution-c2",
                "task-c2",
                ExecutionStatus.CREATED,
                "CREATED",
                versions(ruleSetVersion),
                "default-model-profile",
                "MOCK",
                "gemma-local",
                "local-gemma",
                null,
                null);
    }

    private VersionReferences versions(String ruleSetVersion) {
        return new VersionReferences(
                "contract-type-v1",
                ruleSetVersion,
                "budget-v1",
                "model-v1",
                "parser-v1",
                "prompt-v1",
                "schema-v1",
                "pattern-v1",
                "lexicon-v1",
                "selector-v1");
    }

    private Map<String, String> structuredFields() {
        return Map.ofEntries(
                Map.entry("contractName", "达利安造船厂四方城翡翠大道项目北二期3强化地板产品采购合同"),
                Map.entry("partyAName", "达利安造船厂"),
                Map.entry("partyBName", "天下会装饰工程有限公司"),
                Map.entry("contractTotalAmount", "884800"),
                Map.entry("taxExcludedAmount", "783008.85"),
                Map.entry("taxAmount", "101791.15"),
                Map.entry("taxRate", "13"),
                Map.entry("paymentMethod", "MONTHLY"),
                Map.entry("prepaymentRatio", "0"),
                Map.entry("progressPaymentRatio", "80"),
                Map.entry("completionPaymentRatio", "95"),
                Map.entry("settlementPaymentRatio", "95"),
                Map.entry("warrantyRetentionRatio", "5"));
    }

    private static final class CountingReviewEngine extends MinimalReviewEngine {
        private int calls;

        @Override
        public ReviewEngineResult review(ReviewEngineInput input) {
            calls++;
            return super.review(input);
        }
    }

    private static final class RecordingPersistence implements TaskExecutionPersistence {
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
