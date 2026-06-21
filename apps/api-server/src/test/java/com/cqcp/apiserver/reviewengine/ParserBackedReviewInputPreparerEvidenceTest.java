package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.cqcp.apiserver.wordparser.DocxWordParserSpike;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ParserBackedReviewInputPreparerEvidenceTest {

    private static final Path FIXTURE_ROOT = Path.of("..", "..", "packages", "test-fixtures").normalize();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ParserBackedReviewInputPreparer preparer =
            new ParserBackedReviewInputPreparer(new DocxWordParserSpike());

    @Test
    void positiveFixturesBuildExpectedCandidateValuesAndAnchors() throws IOException {
        for (FixtureCase fixtureCase : loadFixtureCases()) {
            var reviewInput = buildReviewInput(fixtureCase);

            assertEvidence(fixtureCase.sampleId(), reviewInput, ReviewPointCode.PARTY_A_NAME_CONSISTENCY, fixtureCase.structuredFields().getRequired("partyAName"), null);
            assertEvidence(fixtureCase.sampleId(), reviewInput, ReviewPointCode.PARTY_B_NAME_CONSISTENCY, fixtureCase.structuredFields().getRequired("partyBName"), null);
            assertEvidence(fixtureCase.sampleId(), reviewInput, ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, fixtureCase.structuredFields().getRequired("contractTotalAmount"), null);
            assertEvidence(fixtureCase.sampleId(), reviewInput, ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, fixtureCase.structuredFields().getRequired("taxAmount"), null);
            assertEvidence(fixtureCase.sampleId(), reviewInput, ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, fixtureCase.structuredFields().getRequired("prepaymentRatio"), null);
            assertEvidence(fixtureCase.sampleId(), reviewInput, ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, fixtureCase.structuredFields().getRequired("progressPaymentRatio"), "\u8fdb\u5ea6");
            assertEvidence(fixtureCase.sampleId(), reviewInput, ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, fixtureCase.structuredFields().getRequired("completionPaymentRatio"), "\u7ae3\u5de5");
            assertEvidence(fixtureCase.sampleId(), reviewInput, ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, fixtureCase.structuredFields().getRequired("settlementPaymentRatio"), "\u7ed3\u7b97");
            assertEvidence(fixtureCase.sampleId(), reviewInput, ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, fixtureCase.structuredFields().getRequired("warrantyRetentionRatio"), "\u8d28\u4fdd");
        }
    }

    @Test
    void parserBackedEvidenceExposesRealTableCellAnchorWithoutValueBasedCellLookup() {
        var fixtureCase = loadFixtureCase(FIXTURE_ROOT.resolve("expected").resolve("CQCP-MVP-DOCX-001.json"));
        var reviewInput = buildReviewInput(
                new ParserBackedReviewInputPreparer(new TableAnchorContractParser()),
                fixtureCase);
        var evidence = reviewInput.pointEvidences().get(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY);

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.locationLevel()).isEqualTo("BLOCK_LEVEL");
        assertThat(evidence.previewElementRef()).isEqualTo("table:table-1/row:1/cell:1");
    }

    @Test
    void parserBackedEvidenceDowngradesCrossCellMatchToTableRowAnchor() {
        var fixtureCase = loadFixtureCase(FIXTURE_ROOT.resolve("expected").resolve("CQCP-MVP-DOCX-001.json"));
        var reviewInput = buildReviewInput(
                new ParserBackedReviewInputPreparer(new TableAnchorContractParser()),
                fixtureCase);
        var evidence = reviewInput.pointEvidences().get(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY);

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.locationLevel()).isEqualTo("BLOCK_LEVEL");
        assertThat(evidence.previewElementRef()).isEqualTo("table:table-1/row:0");
    }

    @Test
    void negativeFixturesStillBindEvidenceToGoldenDocumentValues() throws IOException {
        for (NegativeFixtureCase negativeCase : loadNegativeFixtureCases()) {
            var reviewInput = buildReviewInput(negativeCase);
            var goldenFields = negativeCase.goldenStructuredFields();
            var caseId = negativeCase.sampleId() + "#" + negativeCase.matrixRowId();

            assertEvidence(caseId, reviewInput, ReviewPointCode.PARTY_A_NAME_CONSISTENCY, goldenFields.getRequired("partyAName"), null);
            assertEvidence(caseId, reviewInput, ReviewPointCode.PARTY_B_NAME_CONSISTENCY, goldenFields.getRequired("partyBName"), null);
            assertEvidence(caseId, reviewInput, ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, goldenFields.getRequired("contractTotalAmount"), null);
            assertEvidence(caseId, reviewInput, ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, goldenFields.getRequired("taxAmount"), null);
            assertEvidence(caseId, reviewInput, ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, goldenFields.getRequired("prepaymentRatio"), null);

            if ("MONTHLY".equals(negativeCase.structuredFields().getOptional("paymentMethod").orElse(null))) {
                assertEvidence(caseId, reviewInput, ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, goldenFields.getRequired("progressPaymentRatio"), "\u8fdb\u5ea6");
                assertEvidence(caseId, reviewInput, ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, goldenFields.getRequired("completionPaymentRatio"), "\u7ae3\u5de5");
                assertEvidence(caseId, reviewInput, ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, goldenFields.getRequired("settlementPaymentRatio"), "\u7ed3\u7b97");
                assertEvidence(caseId, reviewInput, ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, goldenFields.getRequired("warrantyRetentionRatio"), "\u8d28\u4fdd");
            }
        }
    }

    @Test
    void realParserPipelineCanProduceConflictedProgressPaymentCandidates() throws IOException {
        var baseFixture = loadFixtureCase(FIXTURE_ROOT.resolve("expected").resolve("CQCP-MVP-DOCX-001.json"));
        var conflictFixture = new FixtureCase(
                "CQCP-MVP-DOCX-001-progress-conflict",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001-progress-conflict.docx").normalize(),
                baseFixture.structuredFields());

        var reviewInput = buildReviewInput(conflictFixture);
        var evidence = reviewInput.pointEvidences().get(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY);

        assertThat(evidence).isNotNull();
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(evidence.candidateValue()).isNull();
        assertThat(evidence.blockId()).isNull();
        assertThat(evidence.confidence()).isEqualTo(EvidenceConfidenceLevel.CONFLICTED.name());
        assertThat(evidence.diagnosticCode()).isEqualTo("SYS_ROLE_CONFLICT");
        assertThat(evidence.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
        assertThat(evidence.evidenceSummary()).contains("70");
        assertThat(evidence.evidenceSummary()).contains("75");
        assertThat(evidence.slotCoverages())
                .singleElement()
                .satisfies(slot -> assertThat(slot.coverageStatus()).isEqualTo(EvidenceSlotCoverageStatus.AMBIGUOUS));
    }

    @Test
    void realParserPipelineCanProduceMediumConfidenceProgressPaymentCandidates() throws IOException {
        var baseFixture = loadFixtureCase(FIXTURE_ROOT.resolve("expected").resolve("CQCP-MVP-DOCX-001.json"));
        var mediumFixture = new FixtureCase(
                "CQCP-MVP-DOCX-001-progress-medium",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001-progress-medium.docx").normalize(),
                baseFixture.structuredFields());

        var reviewInput = buildReviewInput(mediumFixture);
        var evidence = reviewInput.pointEvidences().get(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY);

        assertNonHighEvidence(evidence, EvidenceConfidenceLevel.MEDIUM, "SYS_EVIDENCE_MEDIUM_CONFIDENCE");
        assertThat(evidence.candidateValue()).isEqualTo("70");
        assertThat(evidence.blockId()).isNotBlank();
        assertThat(evidence.evidenceSummary()).contains("70");
    }

    @Test
    void realParserPipelineCanProduceLowConfidenceProgressPaymentCandidates() throws IOException {
        var baseFixture = loadFixtureCase(FIXTURE_ROOT.resolve("expected").resolve("CQCP-MVP-DOCX-001.json"));
        var lowFixture = new FixtureCase(
                "CQCP-MVP-DOCX-001-progress-low",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001-progress-low.docx").normalize(),
                baseFixture.structuredFields());

        var reviewInput = buildReviewInput(lowFixture);
        var evidence = reviewInput.pointEvidences().get(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY);

        assertNonHighEvidence(evidence, EvidenceConfidenceLevel.LOW, "SYS_EVIDENCE_LOW_CONFIDENCE");
        assertThat(evidence.candidateValue()).isEqualTo("70");
        assertThat(evidence.blockId()).isNotBlank();
        assertThat(evidence.evidenceSummary()).contains("70");
    }

    @Test
    void missingRequiredSlotProducesMissingCoverage() {
        var preparer = new ParserBackedReviewInputPreparer(new EmptyContractParser());
        var fixtureCase = new FixtureCase(
                "empty-contract",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001.docx").normalize(),
                StructuredFieldSet.builder()
                        .put("partyAName", "甲方公司")
                        .put("partyBName", "乙方公司")
                        .put("contractTotalAmount", "100")
                        .put("taxExcludedAmount", "88.5")
                        .put("taxAmount", "11.5")
                        .put("paymentMethod", "MONTHLY")
                        .put("prepaymentRatio", "20")
                        .put("progressPaymentRatio", "60")
                        .put("completionPaymentRatio", "80")
                        .put("settlementPaymentRatio", "95")
                        .put("warrantyRetentionRatio", "5")
                        .build());

        var reviewInput = buildReviewInput(preparer, fixtureCase);
        var evidence = reviewInput.pointEvidences().get(ReviewPointCode.PARTY_A_NAME_CONSISTENCY);

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.MISSING);
        assertThat(evidence.slotCoverages())
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.coverageStatus()).isEqualTo(EvidenceSlotCoverageStatus.MISSING);
                    assertThat(slot.required()).isTrue();
                    assertThat(slot.critical()).isTrue();
                });
    }

    private ReviewEngineInput buildReviewInput(FixtureCaseLike fixtureCase) {
        return buildReviewInput(preparer, fixtureCase);
    }

    private ReviewEngineInput buildReviewInput(
            ParserBackedReviewInputPreparer preparer,
            FixtureCaseLike fixtureCase) {
        var request = newRequest(fixtureCase);
        var parsed = preparer.parse(request.documentReference());
        var indexed = preparer.index(parsed);
        var plan = preparer.plan(indexed);
        return preparer.build(request, plan);
    }

    private TaskExecutionRequest newRequest(FixtureCaseLike fixtureCase) {
        return TaskExecutionRequest.forDocument(
                new ReviewTaskRecord("task-" + fixtureCase.sampleId(), "娴嬭瘯鍚堝悓", fixtureCase.structuredFields().asMap()),
                new TaskExecutionRecord(
                        "execution-" + fixtureCase.sampleId(),
                        "task-" + fixtureCase.sampleId(),
                        ExecutionStatus.CREATED,
                        ExecutionStatus.CREATED.name(),
                        new VersionReferences("ct", "rs", "bg", "mp", "pv", "pr", "sv", "pt", "lx", "sl"),
                        "default-model-profile",
                        "MOCK",
                        "gemma-local",
                        "local-gemma",
                        null,
                        null),
                new TaskExecutionDocumentReference(fixtureCase.docxPath(), fixtureCase.sampleId()),
                List.of(),
                List.of());
    }

    private void assertEvidence(
            String caseId,
            ReviewEngineInput reviewInput,
            ReviewPointCode reviewPointCode,
            String expectedValue,
            String expectedKeyword) {
        var evidence = reviewInput.pointEvidences().get(reviewPointCode);

        assertThat(evidence)
                .as(caseId + " -> " + reviewPointCode.name())
                .isNotNull();
        assertThat(evidence.status())
                .as(caseId + " -> " + reviewPointCode.name() + " -> " + evidence)
                .isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.candidateValue()).isEqualTo(expectedValue);
        assertThat(evidence.blockId()).isNotBlank();
        assertThat(evidence.evidenceSummary()).contains(expectedValue);
        assertThat(evidence.slotCoverages())
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.coverageStatus()).isEqualTo(EvidenceSlotCoverageStatus.SATISFIED);
                    assertThat(slot.required()).isTrue();
                    assertThat(slot.critical()).isTrue();
                    assertThat(slot.reliableAnchor()).isTrue();
                });
        if (expectedKeyword != null) {
            assertThat(evidence.evidenceSummary()).contains(expectedKeyword);
        }
    }

    private void assertNonHighEvidence(
            PointEvidence evidence,
            EvidenceConfidenceLevel expectedConfidence,
            String expectedDiagnosticCode) {
        assertThat(evidence).isNotNull();
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(evidence.confidence()).isEqualTo(expectedConfidence.name());
        assertThat(evidence.diagnosticCode()).isEqualTo(expectedDiagnosticCode);
        assertThat(evidence.slotCoverages())
                .singleElement()
                .satisfies(slot -> assertThat(slot.coverageStatus()).isEqualTo(EvidenceSlotCoverageStatus.LOW_CONFIDENCE));
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

    private List<NegativeFixtureCase> loadNegativeFixtureCases() throws IOException {
        var result = new ArrayList<NegativeFixtureCase>();
        try (Stream<Path> stream = Files.list(FIXTURE_ROOT.resolve("expected"))) {
            var paths = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

            for (Path path : paths) {
                var root = objectMapper.readTree(Files.readString(path));
                var sampleId = root.path("sampleId").asText();
                var goldenFields = readStructuredFields(root.at("/goldenExpected/structuredFields"));
                var sourceDocx = FIXTURE_ROOT.resolve(root.path("sourceDocx").asText()).normalize();
                for (JsonNode negativeCandidate : root.withArray("negativeCandidates")) {
                    result.add(new NegativeFixtureCase(
                            sampleId,
                            negativeCandidate.path("matrixRowId").asText(),
                            sourceDocx,
                            readStructuredFields(negativeCandidate.path("structuredFields")),
                            goldenFields));
                }
            }
        }
        return result;
    }

    private FixtureCase loadFixtureCase(Path path) {
        try {
            var root = objectMapper.readTree(Files.readString(path));
            return new FixtureCase(
                    root.path("sampleId").asText(),
                    FIXTURE_ROOT.resolve(root.path("sourceDocx").asText()).normalize(),
                    readStructuredFields(root.at("/goldenExpected/structuredFields")));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load fixture: " + path, exception);
        }
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
            StructuredFieldSet goldenStructuredFields) implements FixtureCaseLike {
    }

    private static final class EmptyContractParser extends DocxWordParserSpike {

        @Override
        public com.cqcp.apiserver.wordparser.WordParserSpikeDocument parse(Path docxPath) {
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument(
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.Metadata("empty", "empty.docx"),
                    List.of(),
                    List.of(),
                    List.of(),
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseQualityReport(
                            "DOCX",
                            "test",
                            "zh-CN",
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            false,
                            com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.FAILED,
                            "LOW",
                            0,
                            0,
                            0,
                            List.of("EMPTY")));
        }
    }

    private static final class TableAnchorContractParser extends DocxWordParserSpike {

        @Override
        public com.cqcp.apiserver.wordparser.WordParserSpikeDocument parse(Path docxPath) {
            var amountText = "合同固定总价 | 100元";
            var progressText = "进度款 | 形象进度产值的70%";
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument(
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.Metadata("table", "table.docx"),
                    List.of(
                            tableRowBlock("block-1", amountText, 0, "合同固定总价", "100元"),
                            tableRowBlock("block-2", progressText, 1, "进度款", "形象进度产值的70%")),
                    List.of(),
                    List.of(),
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseQualityReport(
                            "DOCX",
                            "test",
                            "zh-CN",
                            amountText.length() + progressText.length(),
                            2,
                            0,
                            1,
                            0,
                            0,
                            false,
                            com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.GOOD,
                            "HIGH",
                            0,
                            0,
                            0,
                            List.of()));
        }

        private com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock tableRowBlock(
                String blockId,
                String text,
                int rowIndex,
                String firstCell,
                String secondCell) {
            var secondCellStart = firstCell.length() + " | ".length();
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    blockId,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.TABLE_ROW,
                    text,
                    text,
                    List.of("付款条款"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "table.docx",
                    "table-1",
                    rowIndex,
                    List.of(
                            new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.TableCellSpan(
                                    0, firstCell, 0, firstCell.length()),
                            new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.TableCellSpan(
                                    1, secondCell, secondCellStart, text.length())),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.TABLE_CELL);
        }
    }
}
