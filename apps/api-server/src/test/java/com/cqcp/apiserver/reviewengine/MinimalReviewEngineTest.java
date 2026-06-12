package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(result.pointDiagnostics())
                .extracting(PointDiagnostic::diagnosticCode)
                .contains("SYS_EVIDENCE_AMBIGUOUS");
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
                    notConcludedReason);
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
                    NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
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
                                null));
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
    }

    private record FixtureVariant(
            String matrixRowId,
            StructuredFieldSet structuredFields) {
    }
}
