package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.cqcp.apiserver.wordparser.DocxWordParserSpike;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TaskExecutionStateMachineTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-13T10:00:00Z"), ZoneOffset.UTC);
    private static final Path FIXTURE_ROOT = Path.of("..", "..", "packages", "test-fixtures").normalize();

    private final MinimalReviewEngine engine = new MinimalReviewEngine();
    private final ResultComposer composer = new ResultComposer();
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Test
    void executesParserBackedPreparationStagesBeforeReviewingRules() throws IOException {
        var persistence = new InMemoryTaskExecutionPersistence();
        var stateMachine = new TaskExecutionStateMachine(engine, composer, FIXED_CLOCK);
        var fixture = loadFixtureCase("CQCP-MVP-DOCX-002");

        var result = stateMachine.execute(parserBackedRequest(fixture), persistence);

        assertThat(result.execution().status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.snapshot().summary().passCount()).isGreaterThan(0);
        assertThat(result.snapshot().summary().notConcludedCount()).isZero();
        assertThat(persistence.stageLogs)
                .extracting(TaskStageLogEntry::stageName, TaskStageLogEntry::eventType)
                .containsExactly(
                        tuple("PARSING", "STARTED"),
                        tuple("PARSING", "COMPLETED"),
                        tuple("INDEXING", "STARTED"),
                        tuple("INDEXING", "COMPLETED"),
                        tuple("PLANNING", "STARTED"),
                        tuple("PLANNING", "COMPLETED"),
                        tuple("BUILDING_EVIDENCE", "STARTED"),
                        tuple("BUILDING_EVIDENCE", "COMPLETED"),
                        tuple("REVIEWING_RULES", "STARTED"),
                        tuple("REVIEWING_RULES", "COMPLETED"),
                        tuple("COMPOSING", "STARTED"),
                        tuple("COMPOSING", "COMPLETED"));
    }

    @Test
    void parserBackedPositiveFixtureConcludesAllNinePoints() throws IOException {
        var persistence = new InMemoryTaskExecutionPersistence();
        var stateMachine = new TaskExecutionStateMachine(engine, composer, FIXED_CLOCK);
        var fixture = loadFixtureCase("CQCP-MVP-DOCX-002");

        var result = stateMachine.execute(parserBackedRequest(fixture), persistence);

        assertThat(statusByPoint(result.snapshot().pointResults())).isEqualTo(Map.of(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, PointStatus.PASS,
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, PointStatus.PASS,
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, PointStatus.PASS,
                ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, PointStatus.PASS,
                ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, PointStatus.PASS,
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, PointStatus.PASS,
                ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, PointStatus.PASS,
                ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, PointStatus.PASS,
                ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, PointStatus.PASS));
    }

    @Test
    void parserBackedOtherPositiveFixturesConcludeAllEnabledPoints() throws IOException {
        var fixtureCases = loadFixtureCases().stream()
                .filter(fixture -> !fixture.sampleId().equals("CQCP-MVP-DOCX-002"))
                .toList();

        for (FixtureCase fixtureCase : fixtureCases) {
            var persistence = new InMemoryTaskExecutionPersistence();
            var stateMachine = new TaskExecutionStateMachine(engine, composer, FIXED_CLOCK);

            var result = stateMachine.execute(parserBackedRequest(fixtureCase), persistence);
            var statusByPoint = statusByPoint(result.snapshot().pointResults());
            var unexpectedStatuses = statusByPoint.entrySet().stream()
                    .filter(entry -> entry.getValue() != PointStatus.PASS)
                    .map(entry -> fixtureCase.sampleId() + " -> " + entry.getKey().name() + " -> " + entry.getValue().name())
                    .toList();

            assertThat(unexpectedStatuses)
                    .as("parser-backed fixture should conclude all enabled points for %s", fixtureCase.sampleId())
                    .isEmpty();
        }
    }

    @Test
    void parserBackedEvidenceCanExposeStructuredFieldMismatch() throws IOException {
        var persistence = new InMemoryTaskExecutionPersistence();
        var stateMachine = new TaskExecutionStateMachine(engine, composer, FIXED_CLOCK);
        var fixture = loadFixtureCase("CQCP-MVP-DOCX-002");
        var mismatchedFields = new java.util.LinkedHashMap<>(fixture.structuredFields().asMap());
        mismatchedFields.put("partyBName", "天下装饰工程有限公司");

        var request = TaskExecutionRequest.forDocument(
                new ReviewTaskRecord("task-parser-negative", "测试合同", mismatchedFields),
                baseExecution(ExecutionStatus.CREATED),
                new TaskExecutionDocumentReference(fixture.docxPath(), fixture.sampleId()),
                enabledPoints(),
                List.of());

        var result = stateMachine.execute(request, persistence);

        assertThat(result.snapshot().pointResults().stream()
                        .filter(point -> point.reviewPointCode() == ReviewPointCode.PARTY_B_NAME_CONSISTENCY)
                        .findFirst()
                .orElseThrow()
                .pointStatus())
                .isEqualTo(PointStatus.ERROR);
    }

    @Test
    void parserBackedFixture001BuildsExpectedMonthlyRatios() throws IOException {
        var fixture = loadFixtureCase("CQCP-MVP-DOCX-001");
        var request = parserBackedRequest(fixture);
        var preparer = new ParserBackedReviewInputPreparer(new DocxWordParserSpike());
        var parsedDocument = preparer.parse(request.documentReference());
        var evidenceIndex = preparer.index(parsedDocument);
        var buildPlan = preparer.plan(evidenceIndex);

        var reviewInput = preparer.build(request, buildPlan);

        assertThat(reviewInput.pointEvidences().get(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY).candidateValue())
                .isEqualTo("70");
        assertThat(reviewInput.pointEvidences().get(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY).candidateValue())
                .isEqualTo("80");
        assertThat(reviewInput.pointEvidences().get(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY).candidateValue())
                .isEqualTo("97");
    }

    @Test
    void parserBackedNegativeFixturesDoNotProduceUnexpectedPassesOrFalsePositives() throws IOException {
        var negativeCases = loadNegativeFixtureCases();

        for (NegativeFixtureCase negativeCase : negativeCases) {
            var persistence = new InMemoryTaskExecutionPersistence();
            var stateMachine = new TaskExecutionStateMachine(engine, composer, FIXED_CLOCK);

            var result = stateMachine.execute(parserBackedRequest(negativeCase), persistence);
            var statusByPoint = statusByPoint(result.snapshot().pointResults());
            var unexpectedStatuses = new ArrayList<String>();

            for (ReviewPointCode reviewPointCode : enabledPointCodes()) {
                var actualStatus = statusByPoint.get(reviewPointCode);
                var expectedStatuses = negativeCase.expectedStatusesByPoint().get(reviewPointCode);
                if (expectedStatuses != null) {
                    if (!expectedStatuses.contains(actualStatus)) {
                        unexpectedStatuses.add(negativeCase.caseId()
                                + " -> expected "
                                + reviewPointCode.name()
                                + " in "
                                + expectedStatuses
                                + " but was "
                                + actualStatus);
                    }
                    continue;
                }

                if (negativeCase.paymentMethod().equals("MILESTONE")
                        && monthlyOnlyPoints().contains(reviewPointCode)) {
                    if (actualStatus != PointStatus.SKIPPED) {
                        unexpectedStatuses.add(negativeCase.caseId()
                                + " -> expected "
                                + reviewPointCode.name()
                                + " to be SKIPPED under MILESTONE but was "
                                + actualStatus);
                    }
                    continue;
                }

                if (actualStatus != PointStatus.PASS) {
                    unexpectedStatuses.add(negativeCase.caseId()
                            + " -> unexpected "
                            + reviewPointCode.name()
                            + " status "
                            + actualStatus);
                }
            }

            assertThat(unexpectedStatuses)
                    .as("parser-backed negative fixture should only conclude expected impacted points for %s", negativeCase.caseId())
                    .isEmpty();
        }
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

        return new TaskExecutionRequest(
                new ReviewTaskRecord("task-001", "测试合同", structuredFields.asMap()),
                execution,
                new ReviewEngineInput("task-001", execution.executionId(), "sample-001", structuredFields, defaultEvidence()),
                enabledPoints(),
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
                        NotConcludedReasonCode.MODEL_UNAVAILABLE,
                        List.of(new EvidenceSlotCoverage(
                                "party_b",
                                true,
                                true,
                                EvidenceSlotCoverageStatus.PARTIAL,
                                "SYS_MODEL_TIMEOUT",
                                true))));

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
                null,
                List.of(new EvidenceSlotCoverage(
                        role.toLowerCase(Locale.ROOT),
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

    private Map<ReviewPointCode, PointStatus> statusByPoint(List<PointReviewResult> pointResults) {
        var result = new EnumMap<ReviewPointCode, PointStatus>(ReviewPointCode.class);
        for (PointReviewResult pointResult : pointResults) {
            result.put(pointResult.reviewPointCode(), pointResult.pointStatus());
        }
        return result;
    }

    private List<ReviewPointSnapshot> enabledPoints() {
        return List.of(
                reviewPointSnapshot(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "P001", 1),
                reviewPointSnapshot(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "P002", 2),
                reviewPointSnapshot(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, "P003", 3),
                reviewPointSnapshot(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, "P004", 4),
                reviewPointSnapshot(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, "P005", 5),
                reviewPointSnapshot(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, "P006", 6),
                reviewPointSnapshot(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, "P007", 7),
                reviewPointSnapshot(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, "P008", 8),
                reviewPointSnapshot(ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, "P009", 9));
    }

    private List<ReviewPointCode> enabledPointCodes() {
        return enabledPoints().stream()
                .map(ReviewPointSnapshot::reviewPointCode)
                .toList();
    }

    private List<ReviewPointCode> monthlyOnlyPoints() {
        return List.of(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY,
                ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
                ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY);
    }

    private TaskExecutionRequest parserBackedRequest(FixtureCaseLike fixtureCase) {
        return TaskExecutionRequest.forDocument(
                new ReviewTaskRecord("task-parser-001", "测试合同", fixtureCase.structuredFields().asMap()),
                baseExecution(ExecutionStatus.CREATED),
                new TaskExecutionDocumentReference(fixtureCase.docxPath(), fixtureCase.sampleId()),
                enabledPoints(),
                List.of());
    }

    private FixtureCase loadFixtureCase(String sampleId) throws IOException {
        var root = objectMapper.readTree(Files.readString(FIXTURE_ROOT.resolve("expected").resolve(sampleId + ".json")));
        var structuredFields = readStructuredFields(root.at("/goldenExpected/structuredFields"));
        var sourceDocx = FIXTURE_ROOT.resolve(root.path("sourceDocx").asText()).normalize();
        return new FixtureCase(sampleId, sourceDocx, structuredFields);
    }

    private List<FixtureCase> loadFixtureCases() throws IOException {
        try (Stream<Path> stream = Files.list(FIXTURE_ROOT.resolve("expected"))) {
            var paths = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

            var result = new ArrayList<FixtureCase>();
            for (Path path : paths) {
                var fileName = path.getFileName().toString();
                var sampleId = fileName.substring(0, fileName.length() - ".json".length());
                result.add(loadFixtureCase(sampleId));
            }
            return result;
        }
    }

    private List<NegativeFixtureCase> loadNegativeFixtureCases() throws IOException {
        var result = new ArrayList<NegativeFixtureCase>();
        try (Stream<Path> stream = Files.list(FIXTURE_ROOT.resolve("expected"))) {
            var paths = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

            for (Path path : paths) {
                var root = objectMapper.readTree(Files.readString(path));
                var sampleId = root.path("sampleId").asText();
                var sourceDocx = FIXTURE_ROOT.resolve(root.path("sourceDocx").asText()).normalize();
                for (JsonNode negativeCandidate : root.withArray("negativeCandidates")) {
                    var matrixRowId = negativeCandidate.path("matrixRowId").asText();
                    var structuredFields = readStructuredFields(negativeCandidate.path("structuredFields"));
                    result.add(new NegativeFixtureCase(
                            sampleId,
                            matrixRowId,
                            sourceDocx,
                            structuredFields,
                            expectedStatusesByPoint(negativeCandidate, structuredFields)));
                }
            }
        }
        return result;
    }

    private Map<ReviewPointCode, List<PointStatus>> expectedStatusesByPoint(
            JsonNode negativeCandidate,
            StructuredFieldSet structuredFields) {
        var result = new EnumMap<ReviewPointCode, List<PointStatus>>(ReviewPointCode.class);

        for (JsonNode diff : negativeCandidate.withArray("allDiffs")) {
            var field = diff.path("field").asText();
            switch (field) {
                case "partyAName" -> result.put(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, List.of(PointStatus.ERROR));
                case "partyBName" -> result.put(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, List.of(PointStatus.ERROR));
                case "contractTotalAmount" -> result.put(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, List.of(PointStatus.ERROR));
                case "prepaymentRatio" -> result.put(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, List.of(PointStatus.ERROR));
                case "progressPaymentRatio" -> result.put(
                        ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                        paymentMethodAwareStatuses(structuredFields, PointStatus.ERROR));
                case "completionPaymentRatio" -> result.put(
                        ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY,
                        paymentMethodAwareStatuses(structuredFields, PointStatus.ERROR));
                case "settlementPaymentRatio" -> result.put(
                        ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
                        paymentMethodAwareStatuses(structuredFields, PointStatus.ERROR));
                case "warrantyRetentionRatio" -> result.put(
                        ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY,
                        paymentMethodAwareStatuses(structuredFields, PointStatus.ERROR));
                default -> {
                    // ignore non-enabled fields in current nine-point boundary
                }
            }
        }

        for (JsonNode linkedEffect : negativeCandidate.withArray("formulaLinkedEffects")) {
            if ("TAX_AMOUNT_FORMULA_CONSISTENCY".equals(linkedEffect.path("target").asText())) {
                result.put(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, List.of(PointStatus.ERROR, PointStatus.WARNING));
            }
        }

        return result;
    }

    private List<PointStatus> paymentMethodAwareStatuses(
            StructuredFieldSet structuredFields,
            PointStatus mismatchStatus) {
        if ("MILESTONE".equals(structuredFields.getOptional("paymentMethod").orElse(null))) {
            return List.of(PointStatus.SKIPPED);
        }
        return List.of(mismatchStatus);
    }

    private StructuredFieldSet readStructuredFields(JsonNode node) {
        var builder = StructuredFieldSet.builder();
        node.fields().forEachRemaining(entry -> builder.put(entry.getKey(), entry.getValue().asText()));
        return builder.build();
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

    private interface FixtureCaseLike {

        String sampleId();

        Path docxPath();

        StructuredFieldSet structuredFields();
    }

    private record FixtureCase(
            String sampleId,
            Path docxPath,
            StructuredFieldSet structuredFields) implements FixtureCaseLike {
    }

    private record NegativeFixtureCase(
            String sampleId,
            String matrixRowId,
            Path docxPath,
            StructuredFieldSet structuredFields,
            Map<ReviewPointCode, List<PointStatus>> expectedStatusesByPoint) implements FixtureCaseLike {

        String caseId() {
            return sampleId + "#" + matrixRowId;
        }

        String paymentMethod() {
            return structuredFields.getOptional("paymentMethod").orElse("");
        }
    }
}
