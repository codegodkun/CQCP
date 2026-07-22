package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cqcp.apiserver.tuning.PointDiagnostic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MinimalReviewEngineTest {

    private static final Path FIXTURE_ROOT = Path.of("..", "..", "packages", "test-fixtures").normalize();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MinimalReviewEngine engine = new MinimalReviewEngine();

    @Test
    void concludesAllGoldenFixturesWithNinePointResults() throws IOException {
        for (FixtureCase fixtureCase : loadFixtureCases()) {
            var result = engine.review(fixtureCase.toGoldenInput());

            assertThat(result.pointResults()).hasSize(9);
            assertThat(result.pointResults())
                    .extracting(PointReviewResult::pointStatus)
                    .containsOnly(PointStatus.PASS);
            assertThat(result.pointResults())
                    .extracting(PointReviewResult::pointCoverageStatus)
                    .containsOnly(PointCoverageStatus.COMPLETE);
            assertThat(result.summary()).isEqualTo(new ReviewSummary(9, 9, 0, 0, 0, 0));
            assertThat(result.reviewCompleteness().reviewCoverageStatus())
                    .isEqualTo(ReviewCoverageStatus.FULL_REVIEWED);
            assertThat(result.reviewCompleteness().concludedPointCount()).isEqualTo(9);
            assertThat(result.reviewCompleteness().notConcludedPointCount()).isZero();
            assertThat(result.snapshotDraft().status()).isEqualTo("SUCCESS");
            assertThat(result.snapshotDraft().pointResults()).hasSize(9);
            assertThat(result.pointDiagnostics()).isEmpty();
        }
    }

    @Test
    void returnsErrorForPartyMismatchAndWarningForWeakTaxMismatch() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-001");
        var result = engine.review(fixtureCase.toNegativeInput("1.3"));

        assertThat(statusByPoint(result.pointResults()))
                .containsEntry(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, PointStatus.ERROR)
                .containsEntry(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, PointStatus.WARNING);
        assertThat(result.summary()).isEqualTo(new ReviewSummary(9, 7, 1, 1, 0, 0));
        assertThat(result.snapshotDraft().status()).isEqualTo("SUCCESS");
    }

    @Test
    void skipsMonthlyOnlyPointsWhenPaymentMethodIsMilestone() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-001");
        var result = engine.review(fixtureCase.toNegativeInput("1.5"));

        assertThat(statusByPoint(result.pointResults()))
                .containsEntry(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, PointStatus.PASS)
                .containsEntry(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, PointStatus.SKIPPED)
                .containsEntry(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, PointStatus.SKIPPED)
                .containsEntry(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, PointStatus.SKIPPED)
                .containsEntry(ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, PointStatus.SKIPPED);
        assertThat(result.summary()).isEqualTo(new ReviewSummary(9, 5, 0, 0, 0, 4));
        assertThat(result.pointResults().stream()
                        .filter(point -> point.pointStatus() == PointStatus.SKIPPED))
                .allSatisfy(point -> assertThat(point.skippedReason()).isEqualTo(SkippedReason.NOT_APPLICABLE_FOR_PAYMENT_METHOD));
    }

    @Test
    void mapsSysDiagnosticToNotConcludedWithoutGeneratingBusinessFinding() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-002");
        var input = fixtureCase.toGoldenInput().withEvidenceOverride(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY,
                fixtureCase.systemFailureEvidence(
                        ReviewPointCode.PARTY_B_NAME_CONSISTENCY,
                        "SYS_MODEL_TIMEOUT",
                        NotConcludedReasonCode.MODEL_UNAVAILABLE));

        var result = engine.review(input);

        assertThat(statusByPoint(result.pointResults()))
                .containsEntry(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, PointStatus.NOT_CONCLUDED);
        assertThat(result.summary()).isEqualTo(new ReviewSummary(9, 8, 0, 0, 1, 0));
        assertThat(result.reviewCompleteness().reviewCoverageStatus())
                .isEqualTo(ReviewCoverageStatus.PARTIAL_REVIEWED);
        assertThat(result.snapshotDraft().status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.pointDiagnostics())
                .extracting(PointDiagnostic::diagnosticCode)
                .contains("SYS_MODEL_TIMEOUT");
    }

    @Test
    void returnsNotConcludedForAmbiguousEvidenceWithoutGuessing() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-003");
        var input = fixtureCase.toGoldenInput().withEvidenceOverride(
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
                fixtureCase.ambiguousEvidence(
                        ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
                        "金额候选存在冲突，无法可靠归属。"));

        var result = engine.review(input);

        var point = result.pointResults().stream()
                .filter(item -> item.reviewPointCode() == ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY)
                .findFirst()
                .orElseThrow();

        assertThat(point.pointStatus()).isEqualTo(PointStatus.NOT_CONCLUDED);
        assertThat(point.findingSeverity()).isNull();
        assertThat(point.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
        assertThat(point.pointCoverageStatus()).isEqualTo(PointCoverageStatus.PARTIAL);
        assertThat(point.notConcludedDetail()).isNull();
        assertThat(point.missingOptionalSlots()).isEmpty();
        assertThat(result.pointDiagnostics())
                .extracting(PointDiagnostic::diagnosticCode)
                .contains("SYS_EVIDENCE_AMBIGUOUS");
    }

    @Test
    void mapsLowConfidenceEvidenceToParseLowConfidenceInsteadOfBusinessFinding() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-003");
        var input = fixtureCase.toGoldenInput().withEvidenceOverride(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                fixtureCase.lowConfidenceEvidence(
                        ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                        "70",
                        "SYS_EVIDENCE_LOW_CONFIDENCE"));

        var result = engine.review(input);

        var point = result.pointResults().stream()
                .filter(item -> item.reviewPointCode() == ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY)
                .findFirst()
                .orElseThrow();

        assertThat(point.pointStatus()).isEqualTo(PointStatus.NOT_CONCLUDED);
        assertThat(point.findingSeverity()).isNull();
        assertThat(point.notConcludedReason()).isEqualTo(NotConcludedReasonCode.PARSE_LOW_CONFIDENCE);
        assertThat(point.notConcludedDetail()).isEqualTo("PARSE_LOW_CONFIDENCE");
        assertThat(point.pointCoverageStatus()).isEqualTo(PointCoverageStatus.LOW_CONFIDENCE);
        assertThat(result.pointDiagnostics())
                .extracting(PointDiagnostic::diagnosticCode)
                .contains("SYS_EVIDENCE_LOW_CONFIDENCE");
    }

    @Test
    void missingRequiredSlotReturnsNotConcludedWithoutBusinessFinding() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-003");
        var input = fixtureCase.toGoldenInput().withEvidenceOverride(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY,
                fixtureCase.missingEvidence(ReviewPointCode.PARTY_B_NAME_CONSISTENCY));

        var result = engine.review(input);

        var point = result.pointResults().stream()
                .filter(item -> item.reviewPointCode() == ReviewPointCode.PARTY_B_NAME_CONSISTENCY)
                .findFirst()
                .orElseThrow();

        assertThat(point.pointStatus()).isEqualTo(PointStatus.NOT_CONCLUDED);
        assertThat(point.findingSeverity()).isNull();
        assertThat(point.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_NOT_FOUND);
        assertThat(point.notConcludedDetail()).isEqualTo("INDEX_MISSING");
        assertThat(point.pointCoverageStatus()).isEqualTo(PointCoverageStatus.PARTIAL);
        assertThat(result.pointDiagnostics())
                .extracting(PointDiagnostic::diagnosticCode)
                .contains("SYS_INDEX_INCOMPLETE");
        assertThat(result.pointResults().stream()
                        .filter(item -> item.reviewPointCode() == ReviewPointCode.PARTY_B_NAME_CONSISTENCY)
                        .map(PointReviewResult::findingSeverity))
                .containsOnlyNulls();
    }

    @Test
    void budgetTruncatedReturnsModelBudgetExceededWithoutBusinessFinding() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-003");
        var input = fixtureCase.toGoldenInput().withEvidenceOverride(
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
                fixtureCase.budgetTruncatedEvidence(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY));

        var result = engine.review(input);

        var point = result.pointResults().stream()
                .filter(item -> item.reviewPointCode() == ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY)
                .findFirst()
                .orElseThrow();

        assertThat(point.pointStatus()).isEqualTo(PointStatus.NOT_CONCLUDED);
        assertThat(point.findingSeverity()).isNull();
        assertThat(point.notConcludedReason()).isEqualTo(NotConcludedReasonCode.MODEL_BUDGET_EXCEEDED);
        assertThat(point.notConcludedDetail()).isEqualTo("BUDGET_TRUNCATED");
        assertThat(point.pointCoverageStatus()).isEqualTo(PointCoverageStatus.PARTIAL);
        assertThat(result.pointDiagnostics())
                .extracting(PointDiagnostic::diagnosticCode)
                .contains("SYS_EVIDENCE_BUDGET_EXCEEDED");
    }

    @Test
    void confirmedEvidenceWithoutReliableAnchorCannotProduceDeterministicConclusion() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-003");
        var input = fixtureCase.toGoldenInput().withEvidenceOverride(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                fixtureCase.confirmedEvidenceWithoutAnchor(ReviewPointCode.PARTY_A_NAME_CONSISTENCY));

        var result = engine.review(input);

        var point = result.pointResults().stream()
                .filter(item -> item.reviewPointCode() == ReviewPointCode.PARTY_A_NAME_CONSISTENCY)
                .findFirst()
                .orElseThrow();

        assertThat(point.pointStatus()).isEqualTo(PointStatus.NOT_CONCLUDED);
        assertThat(point.findingSeverity()).isNull();
        assertThat(point.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(point.pointCoverageStatus()).isEqualTo(PointCoverageStatus.PARTIAL);
        assertThat(result.pointDiagnostics())
                .extracting(PointDiagnostic::diagnosticCode)
                .contains("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test
    void explicitCarrierPreservesAllEvidenceOccurrenceAnchorsForPassAndError() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-001");
        var exactValue = fixtureCase.goldenStructuredFields().getRequired("partyAName");
        var occurrences = new ArrayList<>(List.of(
                occurrenceCandidate(exactValue, "party-table-row", "table:party-table/row:1/cell:0"),
                occurrenceCandidate(exactValue, "party-table-row", "table:party-table/row:1/cell:1"),
                occurrenceCandidate(exactValue, "party-table-row", "table:party-table/row:1/cell:0"),
                occurrenceCandidate(exactValue, "party-table-row", "table:party-table/row:1"))
                .stream()
                .map(PointEvidenceOccurrence::fromSelectedCandidate)
                .toList());
        var passEvidence = explicitPartyEvidence(exactValue, occurrences);
        occurrences.clear();

        assertThat(passEvidence.occurrences()).hasSize(4);
        assertThatThrownBy(() -> passEvidence.occurrences().add(passEvidence.occurrences().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);

        var passPoint = pointFor(
                engine.review(fixtureCase.toGoldenInput().withEvidenceOverride(
                        ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                        passEvidence)),
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY);
        assertThat(passPoint.pointStatus()).isEqualTo(PointStatus.PASS);
        assertThat(passPoint.sourceAnchors())
                .extracting(SourceAnchorSummary::previewElementRef)
                .containsExactly(
                        "table:party-table/row:1/cell:0",
                        "table:party-table/row:1/cell:1",
                        "table:party-table/row:1");

        var mismatch = "另一家甲方公司";
        var errorOccurrences = List.of(
                        occurrenceCandidate(mismatch, "party-table-row", "table:party-table/row:1/cell:0"),
                        occurrenceCandidate(mismatch, "party-table-row", "table:party-table/row:1/cell:1"),
                        occurrenceCandidate(mismatch, "party-table-row", null))
                .stream()
                .map(PointEvidenceOccurrence::fromSelectedCandidate)
                .toList();
        var errorPoint = pointFor(
                engine.review(fixtureCase.toGoldenInput().withEvidenceOverride(
                        ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                        explicitPartyEvidence(mismatch, errorOccurrences))),
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY);
        assertThat(errorPoint.pointStatus()).isEqualTo(PointStatus.ERROR);
        assertThat(errorPoint.findingSeverity()).isEqualTo(FindingSeverity.ERROR);
        assertThat(errorPoint.sourceAnchors()).hasSize(3);
    }

    @Test
    void unreliableExplicitOccurrenceDowngradesWithoutEmptyAnchor() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-001");
        var exactValue = fixtureCase.goldenStructuredFields().getRequired("partyAName");
        var occurrences = List.of(
                        occurrenceCandidate(exactValue, "reliable-party-block", null),
                        occurrenceCandidate(exactValue, "", "table:party-table/row:1/cell:1"))
                .stream()
                .map(PointEvidenceOccurrence::fromSelectedCandidate)
                .toList();

        var result = engine.review(fixtureCase.toGoldenInput().withEvidenceOverride(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                explicitPartyEvidence(exactValue, occurrences)));
        var point = pointFor(result, ReviewPointCode.PARTY_A_NAME_CONSISTENCY);

        assertThat(point.pointStatus()).isEqualTo(PointStatus.NOT_CONCLUDED);
        assertThat(point.findingSeverity()).isNull();
        assertThat(point.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(point.pointCoverageStatus()).isEqualTo(PointCoverageStatus.PARTIAL);
        assertThat(point.sourceAnchors())
                .singleElement()
                .satisfies(anchor -> assertThat(anchor.blockId()).isEqualTo("reliable-party-block"));
        assertThat(point.sourceAnchors()).noneSatisfy(anchor -> assertThat(anchor.blockId()).isBlank());
        assertThat(result.pointDiagnostics())
                .extracting(PointDiagnostic::diagnosticCode)
                .contains("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    private PointReviewResult pointFor(ReviewEngineResult result, ReviewPointCode reviewPointCode) {
        return result.pointResults().stream()
                .filter(point -> point.reviewPointCode() == reviewPointCode)
                .findFirst()
                .orElseThrow();
    }

    private PointEvidence explicitPartyEvidence(
            String candidateValue,
            List<PointEvidenceOccurrence> occurrences) {
        return new PointEvidence(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                "PARTY_A",
                candidateValue,
                EvidenceStatus.CONFIRMED,
                "NATIVE_WORD",
                "STRUCTURED",
                "NORMAL",
                "party-table-row",
                "HIGH",
                "甲方名称证据",
                null,
                null,
                List.of(new EvidenceSlotCoverage(
                        "party_a",
                        true,
                        true,
                        EvidenceSlotCoverageStatus.SATISFIED,
                        null,
                        true)),
                List.of("合同主体"),
                "BODY",
                "BLOCK_LEVEL",
                "table:party-table/row:1/cell:0",
                occurrences);
    }

    private EvidenceCandidate occurrenceCandidate(
            String candidateValue,
            String blockId,
            String previewElementRef) {
        return new EvidenceCandidate(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                "PARTY_A",
                candidateValue,
                blockId,
                "甲方：" + candidateValue,
                true,
                true,
                true,
                List.of("合同主体"),
                "BODY",
                "party-table",
                1,
                previewElementRef != null && previewElementRef.contains("/cell:") ? 1 : null,
                previewElementRef);
    }

    private Map<ReviewPointCode, PointStatus> statusByPoint(List<PointReviewResult> pointResults) {
        var result = new EnumMap<ReviewPointCode, PointStatus>(ReviewPointCode.class);
        for (PointReviewResult pointResult : pointResults) {
            result.put(pointResult.reviewPointCode(), pointResult.pointStatus());
        }
        return result;
    }

    private List<FixtureCase> loadFixtureCases() throws IOException {
        try (Stream<Path> stream = Files.list(FIXTURE_ROOT.resolve("expected"))) {
            var paths = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

            var result = new ArrayList<FixtureCase>();
            for (Path path : paths) {
                result.add(loadFixtureCase(path));
            }
            return result;
        }
    }

    private FixtureCase loadFixtureCase(String sampleId) throws IOException {
        return loadFixtureCase(FIXTURE_ROOT.resolve("expected").resolve(sampleId + ".json"));
    }

    private FixtureCase loadFixtureCase(Path path) throws IOException {
        var root = objectMapper.readTree(Files.readString(path));
        var sampleId = root.path("sampleId").asText();
        var goldenStructuredFields = readStructuredFields(root.at("/goldenExpected/structuredFields"));
        var negatives = new ArrayList<FixtureVariant>();
        for (JsonNode node : root.withArray("negativeCandidates")) {
            negatives.add(new FixtureVariant(node.path("matrixRowId").asText(), readStructuredFields(node.path("structuredFields"))));
        }
        return new FixtureCase(sampleId, goldenStructuredFields, negatives);
    }

    private StructuredFieldSet readStructuredFields(JsonNode node) {
        var builder = StructuredFieldSet.builder();
        node.fields().forEachRemaining(entry -> builder.put(entry.getKey(), asString(entry.getValue())));
        return builder.build();
    }

    private String asString(JsonNode node) {
        if (node.isNumber()) {
            return new BigDecimal(node.asText()).stripTrailingZeros().toPlainString();
        }
        return node.asText();
    }

    private record FixtureCase(
            String sampleId,
            StructuredFieldSet goldenStructuredFields,
            List<FixtureVariant> negatives) {

        ReviewEngineInput toGoldenInput() {
            return new ReviewEngineInput(
                    "task-" + sampleId.toLowerCase(Locale.ROOT),
                    "execution-golden",
                    sampleId,
                    goldenStructuredFields,
                    defaultEvidence());
        }

        ReviewEngineInput toNegativeInput(String matrixRowId) {
            var fields = negatives.stream()
                    .filter(candidate -> candidate.matrixRowId().equals(matrixRowId))
                    .findFirst()
                    .orElseThrow()
                    .structuredFields();
            return new ReviewEngineInput(
                    "task-" + sampleId.toLowerCase(Locale.ROOT),
                    "execution-" + matrixRowId.replace('.', '-'),
                    sampleId,
                    fields,
                    defaultEvidence());
        }

        PointEvidence systemFailureEvidence(
                ReviewPointCode reviewPointCode,
                String diagnosticCode,
                NotConcludedReasonCode notConcludedReason) {
            return new PointEvidence(
                    reviewPointCode,
                    evidenceRole(reviewPointCode),
                    null,
                    EvidenceStatus.SYSTEM_FAILURE,
                    "NATIVE_WORD",
                    "STRUCTURED",
                    "NORMAL",
                    sampleId + "-" + reviewPointCode.name().toLowerCase(Locale.ROOT),
                    "HIGH",
                    "系统诊断阻断该审核点。",
                    diagnosticCode,
                    notConcludedReason,
                    List.of(new EvidenceSlotCoverage(
                            slotKey(reviewPointCode),
                            true,
                            true,
                            EvidenceSlotCoverageStatus.PARTIAL,
                            diagnosticCode,
                            false)));
        }

        PointEvidence ambiguousEvidence(ReviewPointCode reviewPointCode, String summary) {
            return new PointEvidence(
                    reviewPointCode,
                    evidenceRole(reviewPointCode),
                    null,
                    EvidenceStatus.AMBIGUOUS,
                    "NATIVE_WORD",
                    "STRUCTURED",
                    "NORMAL",
                    sampleId + "-" + reviewPointCode.name().toLowerCase(Locale.ROOT),
                    "MEDIUM",
                    summary,
                    "SYS_EVIDENCE_AMBIGUOUS",
                    NotConcludedReasonCode.EVIDENCE_AMBIGUOUS,
                    List.of(new EvidenceSlotCoverage(
                            slotKey(reviewPointCode),
                            true,
                            true,
                            EvidenceSlotCoverageStatus.AMBIGUOUS,
                            "SYS_EVIDENCE_AMBIGUOUS",
                            false)));
        }

        PointEvidence lowConfidenceEvidence(
                ReviewPointCode reviewPointCode,
                String candidateValue,
                String diagnosticCode) {
            return new PointEvidence(
                    reviewPointCode,
                    evidenceRole(reviewPointCode),
                    candidateValue,
                    EvidenceStatus.AMBIGUOUS,
                    "NATIVE_WORD",
                    "STRUCTURED",
                    "NORMAL",
                    sampleId + "-" + reviewPointCode.name().toLowerCase(Locale.ROOT),
                    "LOW",
                    "低置信候选",
                    diagnosticCode,
                    NotConcludedReasonCode.EVIDENCE_AMBIGUOUS,
                    List.of(new EvidenceSlotCoverage(
                            slotKey(reviewPointCode),
                            true,
                            true,
                            EvidenceSlotCoverageStatus.LOW_CONFIDENCE,
                            diagnosticCode,
                            true)));
        }

        PointEvidence missingEvidence(ReviewPointCode reviewPointCode) {
            return new PointEvidence(
                    reviewPointCode,
                    evidenceRole(reviewPointCode),
                    null,
                    EvidenceStatus.MISSING,
                    "NATIVE_WORD",
                    "STRUCTURED",
                    "NORMAL",
                    null,
                    "UNKNOWN",
                    "未找到 required slot 候选",
                    "SYS_INDEX_INCOMPLETE",
                    NotConcludedReasonCode.EVIDENCE_NOT_FOUND,
                    List.of(new EvidenceSlotCoverage(
                            slotKey(reviewPointCode),
                            true,
                            true,
                            EvidenceSlotCoverageStatus.MISSING,
                            "SYS_INDEX_INCOMPLETE",
                            false)));
        }

        PointEvidence budgetTruncatedEvidence(ReviewPointCode reviewPointCode) {
            return new PointEvidence(
                    reviewPointCode,
                    evidenceRole(reviewPointCode),
                    null,
                    EvidenceStatus.SYSTEM_FAILURE,
                    "NATIVE_WORD",
                    "STRUCTURED",
                    "NORMAL",
                    null,
                    "HIGH",
                    "证据因预算截断未完整保留",
                    "SYS_EVIDENCE_BUDGET_EXCEEDED",
                    NotConcludedReasonCode.MODEL_BUDGET_EXCEEDED,
                    List.of(new EvidenceSlotCoverage(
                            slotKey(reviewPointCode),
                            true,
                            true,
                            EvidenceSlotCoverageStatus.BUDGET_TRUNCATED,
                            "SYS_EVIDENCE_BUDGET_EXCEEDED",
                            false)));
        }

        PointEvidence confirmedEvidenceWithoutAnchor(ReviewPointCode reviewPointCode) {
            return new PointEvidence(
                    reviewPointCode,
                    evidenceRole(reviewPointCode),
                    evidenceValue(reviewPointCode),
                    EvidenceStatus.CONFIRMED,
                    "NATIVE_WORD",
                    "STRUCTURED",
                    "NORMAL",
                    null,
                    "HIGH",
                    "缺少可靠锚点",
                    null,
                    null,
                    List.of(new EvidenceSlotCoverage(
                            slotKey(reviewPointCode),
                            true,
                            true,
                            EvidenceSlotCoverageStatus.PARTIAL,
                            "SYS_EVIDENCE_BUNDLE_INVALID",
                            false)));
        }

        private Map<ReviewPointCode, PointEvidence> defaultEvidence() {
            var result = new EnumMap<ReviewPointCode, PointEvidence>(ReviewPointCode.class);
            for (ReviewPointCode reviewPointCode : ReviewPointCode.values()) {
                result.put(
                        reviewPointCode,
                        new PointEvidence(
                                reviewPointCode,
                                evidenceRole(reviewPointCode),
                                evidenceValue(reviewPointCode),
                                EvidenceStatus.CONFIRMED,
                                "NATIVE_WORD",
                                "STRUCTURED",
                                "NORMAL",
                                sampleId + "-" + reviewPointCode.name().toLowerCase(Locale.ROOT),
                                "HIGH",
                                "基于 expected fixture goldenExpected 生成的最小合同侧证据。",
                                null,
                                null,
                                List.of(new EvidenceSlotCoverage(
                                        slotKey(reviewPointCode),
                                        true,
                                        true,
                                        EvidenceSlotCoverageStatus.SATISFIED,
                                        null,
                                        true))));
            }
            return result;
        }

        private String evidenceValue(ReviewPointCode reviewPointCode) {
            return switch (reviewPointCode) {
                case PARTY_A_NAME_CONSISTENCY -> goldenStructuredFields.getRequired("partyAName");
                case PARTY_B_NAME_CONSISTENCY -> goldenStructuredFields.getRequired("partyBName");
                case CONTRACT_TOTAL_AMOUNT_CONSISTENCY -> goldenStructuredFields.getRequired("contractTotalAmount");
                case TAX_AMOUNT_FORMULA_CONSISTENCY -> goldenStructuredFields.getRequired("taxAmount");
                case PREPAYMENT_RATIO_CONSISTENCY -> goldenStructuredFields.getRequired("prepaymentRatio");
                case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> goldenStructuredFields.getRequired("progressPaymentRatio");
                case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> goldenStructuredFields.getRequired("completionPaymentRatio");
                case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> goldenStructuredFields.getRequired("settlementPaymentRatio");
                case WARRANTY_RETENTION_RATIO_CONSISTENCY -> goldenStructuredFields.getRequired("warrantyRetentionRatio");
            };
        }

        private String evidenceRole(ReviewPointCode reviewPointCode) {
            return switch (reviewPointCode) {
                case PARTY_A_NAME_CONSISTENCY -> "PARTY_A";
                case PARTY_B_NAME_CONSISTENCY -> "PARTY_B";
                case CONTRACT_TOTAL_AMOUNT_CONSISTENCY -> "CONTRACT_TOTAL_AMOUNT";
                case TAX_AMOUNT_FORMULA_CONSISTENCY -> "TAX_AMOUNT";
                case PREPAYMENT_RATIO_CONSISTENCY -> "PREPAYMENT_RATIO";
                case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> "PROGRESS_PAYMENT_RATIO";
                case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> "COMPLETION_PAYMENT_RATIO";
                case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> "SETTLEMENT_PAYMENT_RATIO";
                case WARRANTY_RETENTION_RATIO_CONSISTENCY -> "WARRANTY_RETENTION_RATIO";
            };
        }

        private String slotKey(ReviewPointCode reviewPointCode) {
            return evidenceRole(reviewPointCode).toLowerCase(Locale.ROOT);
        }
    }

    private record FixtureVariant(
            String matrixRowId,
            StructuredFieldSet structuredFields) {
    }

    private static String evidenceRole(ReviewPointCode code) {
        return switch (code) {
            case PARTY_A_NAME_CONSISTENCY -> "PARTY_A";
            case PARTY_B_NAME_CONSISTENCY -> "PARTY_B";
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY -> "CONTRACT_TOTAL_AMOUNT";
            case TAX_AMOUNT_FORMULA_CONSISTENCY -> "TAX_AMOUNT";
            case PREPAYMENT_RATIO_CONSISTENCY -> "PREPAYMENT_RATIO";
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> "PROGRESS_PAYMENT_RATIO";
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> "COMPLETION_PAYMENT_RATIO";
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> "SETTLEMENT_PAYMENT_RATIO";
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> "WARRANTY_RETENTION_RATIO";
        };
    }

    // ════════════════════════════════════════════════════════════════
    // Consistency runtime (snapshot-present) verdict tests
    // ════════════════════════════════════════════════════════════════

    private static RuntimeRuleSetSnapshot textSnapshot(String value, String candidateValue) {
        var pol = new ConsistencyPolicySnapshot(
                "CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1",
                List.of("BODY", "APPENDIX"), List.of("TOC"), List.of(), List.of(),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1", List.of(), List.of());
        var map = new EnumMap<ReviewPointCode, ConsistencyPolicySnapshot>(ReviewPointCode.class);
        for (var code : ReviewPointCode.values()) {
            var c = code == ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY
                        || code == ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY
                    ? new ConsistencyPolicySnapshot(
                            "CONSISTENCY_SET", 1, 8, 64,
                            "consistency-scope-v20260715.1",
                            List.of("BODY", "APPENDIX"), List.of("TOC"), List.of(), List.of(),
                            "consistency-canonicalization-v20260715.1", "DECIMAL", "CNY",
                            "mvp-occurrence-identity-v1", List.of(), List.of())
                    : code.name().contains("RATIO")
                            ? new ConsistencyPolicySnapshot(
                                    "CONSISTENCY_SET", 1, 8, 64,
                                    "consistency-scope-v20260715.1",
                                    List.of("BODY", "APPENDIX"), List.of("TOC"), List.of(), List.of(),
                                    "consistency-canonicalization-v20260715.1", "DECIMAL", "PERCENT",
                                    "mvp-occurrence-identity-v1", List.of(), List.of())
                            : pol;
            map.put(code, c);
        }
        return new RuntimeRuleSetSnapshot("test", "v20260715.1", "rp-test", "v20260715.1", map);
    }

    private static RuntimeRuleSetSnapshot textSnapshot() {
        return textSnapshot(null, null);
    }

    private PointEvidence singleValueEvidence(
            ReviewPointCode code, String candidateValue, String blockId) {
        var canonical = canonicalizeForTest(candidateValue, code);
        return new PointEvidence(
                code, evidenceRole(code), canonical,
                EvidenceStatus.CONFIRMED, "NATIVE_WORD", "STRUCTURED", "NORMAL",
                blockId, "HIGH", "Test evidence",
                null, null,
                List.of(new EvidenceSlotCoverage(
                        slotKeyForTest(code), true, true,
                        EvidenceSlotCoverageStatus.SATISFIED, null, true)),
                List.of("Section"), "BODY", "BLOCK_LEVEL", null,
                List.of(new PointEvidenceOccurrence(
                        canonical, blockId, "block text",
                        List.of("Section"), "BODY", "HIGH", "BLOCK_LEVEL", null)));
    }

    private PointEvidence multiValueEvidence(
            ReviewPointCode code, String v1, String v2, String b1, String b2) {
        var c1 = canonicalizeForTest(v1, code);
        var c2 = canonicalizeForTest(v2, code);
        return new PointEvidence(
                code, evidenceRole(code), null,
                EvidenceStatus.CONFIRMED, "NATIVE_WORD", "STRUCTURED", "NORMAL",
                b1, "HIGH", "Multi-value evidence",
                null, null,
                List.of(new EvidenceSlotCoverage(
                        slotKeyForTest(code), true, true,
                        EvidenceSlotCoverageStatus.SATISFIED, null, true)),
                List.of("Section"), "BODY", "BLOCK_LEVEL", null,
                List.of(
                        new PointEvidenceOccurrence(
                                c1, b1, "block1 text",
                                List.of("Section"), "BODY", "HIGH", "BLOCK_LEVEL", null),
                        new PointEvidenceOccurrence(
                                c2, b2, "block2 text",
                                List.of("Section"), "BODY", "HIGH", "BLOCK_LEVEL", null)));
    }

    private static String canonicalizeForTest(String value, ReviewPointCode code) {
        if (code == ReviewPointCode.PARTY_A_NAME_CONSISTENCY
                || code == ReviewPointCode.PARTY_B_NAME_CONSISTENCY) {
            return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        }
        if (code == ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY
                || code == ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY) {
            return value.replace(",", "").trim();
        }
        // ratio points: strip trailing %
        var cleaned = value.trim();
        if (cleaned.endsWith("%")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        return cleaned;
    }

    private String slotKeyForTest(ReviewPointCode code) {
        return evidenceRole(code).toLowerCase(Locale.ROOT);
    }

    @Test
    void consistencySnapshotNullDoesNotChangeLegacyTaxFormula() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-001");
        // Create evidence with candidateValue=999 which doesn't match structured taxAmount
        // With snapshot=null, evidence-tax check is skipped
        var mismatchEvidence = new PointEvidence(
                ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY,
                "TAX_AMOUNT",
                "999.99",
                EvidenceStatus.CONFIRMED,
                "NATIVE_WORD", "STRUCTURED", "NORMAL",
                "block-tax", "HIGH",
                "税额证据", null, null,
                List.of(new EvidenceSlotCoverage(
                        "tax_amount", true, true,
                        EvidenceSlotCoverageStatus.SATISFIED, null, true)));
        var input = fixtureCase.toGoldenInput()
                .withEvidenceOverride(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, mismatchEvidence);

        var result = engine.review(input);
        var point = pointFor(result, ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY);

        // With snapshot=null, evidence-tax check is skipped; strong formula runs and should pass
        assertThat(point.pointStatus()).isEqualTo(PointStatus.PASS);
    }

    @Test
    void consistencyMultiValueProducesBusinessErrorForAllNinePoints() {
        var records = List.of(
                new NinePointMultiRecord(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "partyAName", "甲方公司", "甲方公司A", "甲方公司B"),
                new NinePointMultiRecord(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "partyBName", "乙方公司", "乙方公司A", "乙方公司B"),
                new NinePointMultiRecord(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, "contractTotalAmount", "100", "100", "200"),
                new NinePointMultiRecord(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, "taxAmount", "11.5", "11", "12"),
                new NinePointMultiRecord(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, "prepaymentRatio", "20", "20", "30"),
                new NinePointMultiRecord(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, "progressPaymentRatio", "70", "70", "80"),
                new NinePointMultiRecord(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, "completionPaymentRatio", "80", "80", "90"),
                new NinePointMultiRecord(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, "settlementPaymentRatio", "95", "95", "97"),
                new NinePointMultiRecord(ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, "warrantyRetentionRatio", "5", "5", "3"));

        for (var rec : records) {
            var sf = StructuredFieldSet.builder()
                    .put("partyAName", "甲方公司")
                    .put("partyBName", "乙方公司")
                    .put("contractTotalAmount", "100")
                    .put("taxExcludedAmount", "88.5")
                    .put("taxAmount", "11.5")
                    .put("paymentMethod", "MONTHLY")
                    .put("prepaymentRatio", "20")
                    .put("progressPaymentRatio", "70")
                    .put("completionPaymentRatio", "80")
                    .put("settlementPaymentRatio", "95")
                    .put("warrantyRetentionRatio", "5")
                    .build();
            var evidence = multiValueEvidence(rec.code(), rec.v1(), rec.v2(), "block-a", "block-b");
            var input = new ReviewEngineInput(
                    "task-test", "exec-test", "test", sf,
                    defaultEvidence(sf), textSnapshot());

            // Override the test point with multi-value evidence
            var overridden = input.withEvidenceOverride(rec.code(), evidence);

            var result = engine.review(overridden);
            var point = pointFor(result, rec.code());

            assertThat(point.pointStatus())
                    .as(rec.code() + " multi-value should be ERROR")
                    .isEqualTo(PointStatus.ERROR);
            assertThat(point.findingSeverity())
                    .as(rec.code() + " severity should be ERROR")
                    .isEqualTo(FindingSeverity.ERROR);
            assertThat(point.pointCoverageStatus())
                    .as(rec.code() + " coverage should be COMPLETE")
                    .isEqualTo(PointCoverageStatus.COMPLETE);
            assertThat(point.sourceAnchors())
                    .as(rec.code() + " should preserve both occurrence anchors")
                    .hasSize(2);
        }
    }

    @Test
    void consistencySingleValueMatchPassesForAllPoints() {
        var records = List.of(
                new NinePointRecord(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "partyAName", "甲方公司", "甲方公司", "甲方公司"),
                new NinePointRecord(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "partyBName", "乙方公司", "乙方公司", "乙方公司"),
                new NinePointRecord(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, "contractTotalAmount", "100", "100", "100"),
                new NinePointRecord(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, "taxAmount", "11.5", "11.5", "11.5"),
                new NinePointRecord(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, "prepaymentRatio", "20", "20", "20"),
                new NinePointRecord(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, "progressPaymentRatio", "70", "70", "70"),
                new NinePointRecord(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, "completionPaymentRatio", "80", "80", "80"),
                new NinePointRecord(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, "settlementPaymentRatio", "95", "95", "95"),
                new NinePointRecord(ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, "warrantyRetentionRatio", "5", "5", "5"));

        for (var rec : records) {
            var sf = StructuredFieldSet.builder()
                    .put("partyAName", "甲方公司")
                    .put("partyBName", "乙方公司")
                    .put("contractTotalAmount", "100")
                    .put("taxExcludedAmount", "88.5")
                    .put("taxAmount", "11.5")
                    .put("paymentMethod", "MONTHLY")
                    .put("prepaymentRatio", "20")
                    .put("progressPaymentRatio", "70")
                    .put("completionPaymentRatio", "80")
                    .put("settlementPaymentRatio", "95")
                    .put("warrantyRetentionRatio", "5")
                    .build();
            var evidence = singleValueEvidence(rec.code(), rec.evidenceValue(), "block-main");
            var input = new ReviewEngineInput(
                    "task-test", "exec-test", "test", sf,
                    defaultEvidence(sf), textSnapshot());

            var overridden = input.withEvidenceOverride(rec.code(), evidence);
            var result = engine.review(overridden);
            var point = pointFor(result, rec.code());

            assertThat(point.pointStatus())
                    .as(rec.code() + " single match should be PASS")
                    .isEqualTo(PointStatus.PASS);
            assertThat(point.pointCoverageStatus())
                    .as(rec.code() + " coverage should be COMPLETE")
                    .isEqualTo(PointCoverageStatus.COMPLETE);
        }
    }

    @Test
    void consistencySingleValueMismatchErrorsForAllPoints() {
        var records = List.of(
                new NinePointRecord(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "partyAName", "甲方公司", "其他公司", "其他公司"),
                new NinePointRecord(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "partyBName", "乙方公司", "不同公司", "不同公司"),
                new NinePointRecord(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, "contractTotalAmount", "100", "999", "999"),
                new NinePointRecord(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, "taxAmount", "11.5", "20", "20"),
                new NinePointRecord(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, "prepaymentRatio", "20", "99", "99"),
                new NinePointRecord(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, "progressPaymentRatio", "70", "50", "50"),
                new NinePointRecord(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, "completionPaymentRatio", "80", "60", "60"),
                new NinePointRecord(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, "settlementPaymentRatio", "95", "85", "85"),
                new NinePointRecord(ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, "warrantyRetentionRatio", "5", "10", "10"));

        for (var rec : records) {
            var sf = StructuredFieldSet.builder()
                    .put("partyAName", "甲方公司")
                    .put("partyBName", "乙方公司")
                    .put("contractTotalAmount", "100")
                    .put("taxExcludedAmount", "88.5")
                    .put("taxAmount", "11.5")
                    .put("paymentMethod", "MONTHLY")
                    .put("prepaymentRatio", "20")
                    .put("progressPaymentRatio", "70")
                    .put("completionPaymentRatio", "80")
                    .put("settlementPaymentRatio", "95")
                    .put("warrantyRetentionRatio", "5")
                    .build();
            var evidence = singleValueEvidence(rec.code(), rec.evidenceValue(), "block-main");
            var input = new ReviewEngineInput(
                    "task-test", "exec-test", "test", sf,
                    defaultEvidence(sf), textSnapshot());

            var overridden = input.withEvidenceOverride(rec.code(), evidence);
            var result = engine.review(overridden);
            var point = pointFor(result, rec.code());

            assertThat(point.pointStatus())
                    .as(rec.code() + " single mismatch should be ERROR")
                    .isEqualTo(PointStatus.ERROR);
            assertThat(point.findingSeverity())
                    .as(rec.code() + " severity should be ERROR")
                    .isEqualTo(FindingSeverity.ERROR);
            assertThat(point.pointCoverageStatus())
                    .as(rec.code() + " coverage should be COMPLETE")
                    .isEqualTo(PointCoverageStatus.COMPLETE);
        }
    }

    @Test
    void consistencyTaxFormulaEvidenceTaxBeforeStrongFormula() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-001");
        var sf = StructuredFieldSet.builder()
                .put("partyAName", "甲方公司")
                .put("partyBName", "乙方公司")
                .put("contractTotalAmount", "100")
                .put("taxExcludedAmount", "88.5")
                .put("taxAmount", "11.5")
                .put("paymentMethod", "MONTHLY")
                .put("prepaymentRatio", "20")
                .put("progressPaymentRatio", "70")
                .put("completionPaymentRatio", "80")
                .put("settlementPaymentRatio", "95")
                .put("warrantyRetentionRatio", "5")
                .build();
        // evidence.candidateValue=999 doesn't match structured taxAmount=11.5
        var evidence = singleValueEvidence(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, "999", "block-tax");
        var input = new ReviewEngineInput(
                "task-test", "exec-test", "test", sf,
                defaultEvidence(sf), textSnapshot())
                .withEvidenceOverride(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, evidence);

        var result = engine.review(input);
        var point = pointFor(result, ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY);

        // evidence-tax mismatch → ERROR before strong formula
        assertThat(point.pointStatus()).isEqualTo(PointStatus.ERROR);
        assertThat(point.findingSeverity()).isEqualTo(FindingSeverity.ERROR);
    }

    @Test
    void consistencyTaxFormulaEvidenceTaxMatchThenStrongFormula() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-001");
        var sf = StructuredFieldSet.builder()
                .put("partyAName", "甲方公司")
                .put("partyBName", "乙方公司")
                .put("contractTotalAmount", "100")
                .put("taxExcludedAmount", "88.5")
                .put("taxAmount", "11.5")
                .put("paymentMethod", "MONTHLY")
                .put("prepaymentRatio", "20")
                .put("progressPaymentRatio", "70")
                .put("completionPaymentRatio", "80")
                .put("settlementPaymentRatio", "95")
                .put("warrantyRetentionRatio", "5")
                .build();
        // evidence.candidateValue=11.5 matches structured taxAmount=11.5 → strong formula runs
        var evidence = singleValueEvidence(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, "11.5", "block-tax");
        var input = new ReviewEngineInput(
                "task-test", "exec-test", "test", sf,
                defaultEvidence(sf), textSnapshot())
                .withEvidenceOverride(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, evidence);

        var result = engine.review(input);
        var point = pointFor(result, ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY);

        // evidence-tax match → strong formula 88.5+11.5=100 vs total=100 → PASS
        assertThat(point.pointStatus()).isEqualTo(PointStatus.PASS);
    }

    @Test
    void consistencySnapshotPresentMilestoneStillSkipsMonthlyOnly() throws IOException {
        var fixtureCase = loadFixtureCase("CQCP-MVP-DOCX-001");
        var sf = StructuredFieldSet.builder()
                .put("partyAName", "甲方公司")
                .put("partyBName", "乙方公司")
                .put("contractTotalAmount", "100")
                .put("taxExcludedAmount", "88.5")
                .put("taxAmount", "11.5")
                .put("paymentMethod", "MILESTONE")
                .put("prepaymentRatio", "20")
                .put("progressPaymentRatio", "70")
                .put("completionPaymentRatio", "80")
                .put("settlementPaymentRatio", "95")
                .put("warrantyRetentionRatio", "5")
                .build();
        var input = new ReviewEngineInput(
                "task-test", "exec-test", "test", sf,
                defaultEvidence(sf), textSnapshot());

        var result = engine.review(input);
        assertThat(statusByPoint(result.pointResults()))
                .containsEntry(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, PointStatus.SKIPPED)
                .containsEntry(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, PointStatus.SKIPPED)
                .containsEntry(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, PointStatus.SKIPPED)
                .containsEntry(ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, PointStatus.SKIPPED)
                .containsEntry(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, PointStatus.PASS);
    }

    private Map<ReviewPointCode, PointEvidence> defaultEvidence(StructuredFieldSet sf) {
        var result = new EnumMap<ReviewPointCode, PointEvidence>(ReviewPointCode.class);
        for (var code : ReviewPointCode.values()) {
            result.put(code, new PointEvidence(
                    code, evidenceRole(code), evidenceValue(code, sf),
                    EvidenceStatus.CONFIRMED, "NATIVE_WORD", "STRUCTURED", "NORMAL",
                    "block-" + code.name().toLowerCase(Locale.ROOT), "HIGH",
                    "Default evidence", null, null,
                    List.of(new EvidenceSlotCoverage(
                            slotKeyForTest(code), true, true,
                            EvidenceSlotCoverageStatus.SATISFIED, null, true))));
        }
        return result;
    }

    private static String evidenceValue(ReviewPointCode code, StructuredFieldSet sf) {
        return switch (code) {
            case PARTY_A_NAME_CONSISTENCY -> sf.getRequired("partyAName");
            case PARTY_B_NAME_CONSISTENCY -> sf.getRequired("partyBName");
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY -> sf.getRequired("contractTotalAmount");
            case TAX_AMOUNT_FORMULA_CONSISTENCY -> sf.getRequired("taxAmount");
            case PREPAYMENT_RATIO_CONSISTENCY -> sf.getRequired("prepaymentRatio");
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> sf.getRequired("progressPaymentRatio");
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> sf.getRequired("completionPaymentRatio");
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> sf.getRequired("settlementPaymentRatio");
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> sf.getRequired("warrantyRetentionRatio");
        };
    }

    private record NinePointRecord(
            ReviewPointCode code,
            String structuredFieldKey,
            String structuredValue,
            String evidenceValue,
            String evidenceBlockId) {
    }

    private record NinePointMultiRecord(
            ReviewPointCode code,
            String structuredFieldKey,
            String structuredValue,
            String v1,
            String v2) {
    }

    // ────────── Anti-circular multi-value cases ──────────

    @Test
    void antiCircularSameValueTwoIdentitiesCandidateValueNullDoesNotTriggerMultiValueError() {
        var sf = StructuredFieldSet.builder()
                .put("partyAName", "甲方公司").put("partyBName", "乙方公司")
                .put("contractTotalAmount", "100").put("taxExcludedAmount", "88.5")
                .put("taxAmount", "11.5").put("paymentMethod", "MONTHLY")
                .put("prepaymentRatio", "20").put("progressPaymentRatio", "70")
                .put("completionPaymentRatio", "80").put("settlementPaymentRatio", "95")
                .put("warrantyRetentionRatio", "5").build();
        // Two different identities (block-a, block-b) with SAME canonical value
        var occ1 = new PointEvidenceOccurrence("甲方公司", "block-a", "textA",
                List.of("Section"), "BODY", "HIGH", "BLOCK_LEVEL", null);
        var occ2 = new PointEvidenceOccurrence("甲方公司", "block-b", "textB",
                List.of("Section"), "BODY", "HIGH", "BLOCK_LEVEL", null);
        var evidence = new PointEvidence(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", null,
                EvidenceStatus.CONFIRMED, "NATIVE_WORD", "STRUCTURED", "NORMAL",
                null, "HIGH", "Evidence", null, null,
                List.of(new EvidenceSlotCoverage("party_a", true, true, EvidenceSlotCoverageStatus.SATISFIED, null, true)),
                List.of(), "BODY", null, null, List.of(occ1, occ2));
        var input = new ReviewEngineInput("t", "e", "s", sf, defaultEvidence(sf), textSnapshot())
                .withEvidenceOverride(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, evidence);
        var result = engine.review(input);
        var point = pointFor(result, ReviewPointCode.PARTY_A_NAME_CONSISTENCY);
        // Distinct values from occurrences = {"甲方公司"} → size=1 → NOT multi-value conflict
        assertThat(point.businessMessage()).doesNotContain("多个不同值");
    }

    @Test
    void antiCircularDifferentValueTwoIdentitiesCandidateValueNonNullStillErrors() {
        var sf = StructuredFieldSet.builder()
                .put("partyAName", "甲方公司").put("partyBName", "乙方公司")
                .put("contractTotalAmount", "100").put("taxExcludedAmount", "88.5")
                .put("taxAmount", "11.5").put("paymentMethod", "MONTHLY")
                .put("prepaymentRatio", "20").put("progressPaymentRatio", "70")
                .put("completionPaymentRatio", "80").put("settlementPaymentRatio", "95")
                .put("warrantyRetentionRatio", "5").build();
        // Two identities, DIFFERENT canonical values, candidateValue is non-null (projection from first)
        var occ1 = new PointEvidenceOccurrence("甲方公司A", "block-a", "textA",
                List.of("Section"), "BODY", "HIGH", "BLOCK_LEVEL", null);
        var occ2 = new PointEvidenceOccurrence("甲方公司B", "block-b", "textB",
                List.of("Section"), "BODY", "HIGH", "BLOCK_LEVEL", null);
        var evidence = new PointEvidence(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", "甲方公司A",
                EvidenceStatus.CONFIRMED, "NATIVE_WORD", "STRUCTURED", "NORMAL",
                "block-a", "HIGH", "Evidence", null, null,
                List.of(new EvidenceSlotCoverage("party_a", true, true, EvidenceSlotCoverageStatus.SATISFIED, null, true)),
                List.of("Section"), "BODY", "BLOCK_LEVEL", null, List.of(occ1, occ2));
        var input = new ReviewEngineInput("t", "e", "s", sf, defaultEvidence(sf), textSnapshot())
                .withEvidenceOverride(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, evidence);
        var result = engine.review(input);
        var point = pointFor(result, ReviewPointCode.PARTY_A_NAME_CONSISTENCY);
        // Distinct values = {"甲方公司A", "甲方公司B"} → size=2 → multi-value conflict
        assertThat(point.pointStatus()).isEqualTo(PointStatus.ERROR);
        assertThat(point.findingSeverity()).isEqualTo(FindingSeverity.ERROR);
        assertThat(point.pointCoverageStatus()).isEqualTo(PointCoverageStatus.COMPLETE);
        assertThat(point.sourceAnchors()).hasSize(2);
        assertThat(point.businessMessage()).contains("多个不同值");
        assertThat(result.pointDiagnostics()).isEmpty();
    }
}
