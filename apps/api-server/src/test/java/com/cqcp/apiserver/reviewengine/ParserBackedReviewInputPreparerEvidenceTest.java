package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import java.util.regex.Pattern;
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

    @Test
    void textCandidateWithPlaceholderValueIsDowngradedToMediumConfidence() throws IOException {
        var preparer = new ParserBackedReviewInputPreparer(new PlaceholderValuePartyContractParser());
        var fixtureCase = new FixtureCase(
                "placeholder-value",
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

        assertNonHighEvidence(evidence, EvidenceConfidenceLevel.MEDIUM, "SYS_EVIDENCE_MEDIUM_CONFIDENCE");
        assertThat(evidence.candidateValue()).isEqualTo("—");
        assertThat(evidence.blockId()).isNotBlank();
    }

    @Test
    void textCandidateWithMissingBlockIdIsDowngradedToMediumConfidence() throws IOException {
        var preparer = new ParserBackedReviewInputPreparer(new NoBlockIdPartyContractParser());
        var fixtureCase = new FixtureCase(
                "no-blockid",
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

        assertNonHighEvidence(evidence, EvidenceConfidenceLevel.MEDIUM, "SYS_EVIDENCE_MEDIUM_CONFIDENCE");
        assertThat(evidence.candidateValue()).isEqualTo("测试公司");
        assertThat(evidence.blockId()).isBlank();
        assertThat(evidence.locationLevel()).isNull();
    }

    @Test
    void textCandidateWithAlphanumericCompanyNameAcceptsConfirmed() throws IOException {
        var preparer = new ParserBackedReviewInputPreparer(new AlphanumericPartyContractParser());
        var fixtureCase = new FixtureCase(
                "alphanumeric-party",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001.docx").normalize(),
                StructuredFieldSet.builder()
                        .put("partyAName", "A1")
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

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.candidateValue()).isEqualTo("A1");
        assertThat(evidence.blockId()).isNotBlank();
    }

    @Test
    void textCandidateWithNumericOnlyValueIsDowngradedToMediumConfidence() throws IOException {
        var preparer = new ParserBackedReviewInputPreparer(new NumericOnlyPartyContractParser());
        var fixtureCase = new FixtureCase(
                "numeric-only-party",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001.docx").normalize(),
                StructuredFieldSet.builder()
                        .put("partyAName", "123")
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

        assertNonHighEvidence(evidence, EvidenceConfidenceLevel.MEDIUM, "SYS_EVIDENCE_MEDIUM_CONFIDENCE");
        assertThat(evidence.candidateValue()).isEqualTo("123");
        assertThat(evidence.blockId()).isNotBlank();
    }

    @Test
    void patternCandidateWithExcessiveRatioHasFalseValueFormatSignal() {
        var block = new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                "block-1",
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                "预付款至150%",
                "预付款至150%",
                List.of("付款条款"),
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "test.docx", null, null, List.of(),
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);

        var pattern = Pattern.compile("预付款[^\\d]{0,60}(\\d{1,3}(?:\\.\\d+)?)\\s*%");
        var candidates = preparer.collectPatternCandidates(
                ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY,
                "PREPAYMENT_RATIO",
                List.of(block),
                List.of("预付款"),
                List.of(pattern),
                false);

        assertThat(candidates).isNotEmpty();
        assertThat(candidates).anySatisfy(c -> {
            assertThat(c.candidateValue()).isEqualTo("150");
            assertThat(c.valueFormatSignal()).isFalse();
            assertThat(c.roleLabelSignal()).isTrue();
            assertThat(c.blockAttributionSignal()).isTrue();
        });

        var resolver = new MinimalCandidateResolver();
        var resolution = resolver.resolve(
                ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY,
                "PREPAYMENT_RATIO",
                candidates);
        assertThat(resolution.confidenceLevel()).isNotEqualTo(EvidenceConfidenceLevel.HIGH);
    }

    @Test
    void patternCandidateWithValidRatioHasTrueValueFormatSignal() {
        var block = new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                "block-2",
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                "预付款70%",
                "预付款70%",
                List.of("付款条款"),
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "test.docx", null, null, List.of(),
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);

        var pattern = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?)\\s*%");
        var candidates = preparer.collectPatternCandidates(
                ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY,
                "PREPAYMENT_RATIO",
                List.of(block),
                List.of("预付款"),
                List.of(pattern),
                false);

        assertThat(candidates).isNotEmpty();
        assertThat(candidates).anySatisfy(c -> {
            assertThat(c.candidateValue()).isEqualTo("70");
            assertThat(c.valueFormatSignal()).isTrue();
        });
    }

    @Test
    void currentRuleSetPathDoesNotActivateOccurrenceAnchors() {
        var parser = new SameValueProgressContractParser();
        var runtimePreparer = new ParserBackedReviewInputPreparer(parser);
        var fixtureCase = new FixtureCase(
                "same-value-occurrences-not-activated",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001.docx").normalize(),
                StructuredFieldSet.builder()
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
                        .build());
        var candidates = runtimePreparer.collectPatternCandidates(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PROGRESS_PAYMENT_RATIO",
                parser.parse(fixtureCase.docxPath()).blocks(),
                List.of("进度"),
                List.of(Pattern.compile("进度款[^\\d]{0,60}(\\d{1,3}(?:\\.\\d+)?)\\s*%")),
                false);
        var resolution = new MinimalCandidateResolver().resolve(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PROGRESS_PAYMENT_RATIO",
                candidates);

        assertThat(resolution.confidenceLevel()).isEqualTo(EvidenceConfidenceLevel.HIGH);
        assertThat(resolution.selectedValueOccurrences()).hasSizeGreaterThanOrEqualTo(2);

        var reviewInput = buildReviewInput(
                runtimePreparer,
                fixtureCase);
        var evidence = reviewInput.pointEvidences().get(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY);

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.candidateValue()).isEqualTo("70");
        assertThat(evidence.occurrences()).isEmpty();

        var point = new MinimalReviewEngine().review(reviewInput).pointResults().stream()
                .filter(result -> result.reviewPointCode() == ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY)
                .findFirst()
                .orElseThrow();
        assertThat(point.pointStatus()).isEqualTo(PointStatus.PASS);
        assertThat(point.sourceAnchors()).singleElement();
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

    private static final class AlphanumericPartyContractParser extends DocxWordParserSpike {

        @Override
        public com.cqcp.apiserver.wordparser.WordParserSpikeDocument parse(Path docxPath) {
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument(
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.Metadata("alphanumeric", "alphanumeric.docx"),
                    List.of(partyABlock()),
                    List.of(),
                    List.of(),
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseQualityReport(
                            "DOCX", "test", "zh-CN",
                            "甲方：A1".length(), 1, 0, 0, 0, 0, false,
                            com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.GOOD,
                            "HIGH", 0, 0, 0, List.of()));
        }

        private com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock partyABlock() {
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    "party-block-1",
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                    "甲方：A1",
                    "甲方：A1",
                    List.of("合同主体"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "test.docx",
                    null, null, List.of(),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        }
    }

    private static final class NumericOnlyPartyContractParser extends DocxWordParserSpike {

        @Override
        public com.cqcp.apiserver.wordparser.WordParserSpikeDocument parse(Path docxPath) {
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument(
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.Metadata("numericonly", "numericonly.docx"),
                    List.of(partyABlock()),
                    List.of(),
                    List.of(),
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseQualityReport(
                            "DOCX", "test", "zh-CN",
                            "甲方：123".length(), 1, 0, 0, 0, 0, false,
                            com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.GOOD,
                            "HIGH", 0, 0, 0, List.of()));
        }

        private com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock partyABlock() {
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    "party-block-1",
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                    "甲方：123",
                    "甲方：123",
                    List.of("合同主体"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "test.docx",
                    null, null, List.of(),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        }
    }

    private static final class PlaceholderValuePartyContractParser extends DocxWordParserSpike {

        @Override
        public com.cqcp.apiserver.wordparser.WordParserSpikeDocument parse(Path docxPath) {
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument(
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.Metadata("placeholder", "placeholder.docx"),
                    List.of(partyABlock()),
                    List.of(),
                    List.of(),
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseQualityReport(
                            "DOCX", "test", "zh-CN",
                            "甲方：—".length(), 1, 0, 0, 0, 0, false,
                            com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.GOOD,
                            "HIGH", 0, 0, 0, List.of()));
        }

        private com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock partyABlock() {
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    "party-block-1",
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                    "甲方：—",
                    "甲方：—",
                    List.of("合同主体"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "test.docx",
                    null, null, List.of(),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        }
    }

    private static final class NoBlockIdPartyContractParser extends DocxWordParserSpike {

        @Override
        public com.cqcp.apiserver.wordparser.WordParserSpikeDocument parse(Path docxPath) {
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument(
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.Metadata("noblockid", "noblockid.docx"),
                    List.of(partyABlock()),
                    List.of(),
                    List.of(),
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseQualityReport(
                            "DOCX", "test", "zh-CN",
                            "甲方：测试公司".length(), 1, 0, 0, 0, 0, false,
                            com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.GOOD,
                            "HIGH", 0, 0, 0, List.of()));
        }

        private com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock partyABlock() {
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    "",   // empty blockId — no reliable anchor
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                    "甲方：测试公司",
                    "甲方：测试公司",
                    List.of("合同主体"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "test.docx",
                    null, null, List.of(),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        }
    }

    @Test
    void progressPaymentRatioSemanticAndWholeTextFallbackConflictProducesAmbiguous() {
        var preparer = new ParserBackedReviewInputPreparer(new ProgressSemanticWholeTextConflictParser());
        var fixtureCase = new FixtureCase(
                "progress-semantic-fallback-conflict",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001.docx").normalize(),
                StructuredFieldSet.builder()
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
                        .build());

        var reviewInput = buildReviewInput(preparer, fixtureCase);

        var progressEvidence = reviewInput.pointEvidences().get(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY);
        assertThat(progressEvidence).isNotNull();
        assertThat(progressEvidence.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(progressEvidence.confidence()).isEqualTo(EvidenceConfidenceLevel.CONFLICTED.name());
        assertThat(progressEvidence.diagnosticCode()).isEqualTo("SYS_ROLE_CONFLICT");
        assertThat(progressEvidence.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
        assertThat(progressEvidence.evidenceSummary()).contains("70");
        assertThat(progressEvidence.evidenceSummary()).contains("85");
        assertThat(progressEvidence.slotCoverages())
                .singleElement()
                .satisfies(slot -> assertThat(slot.coverageStatus()).isEqualTo(EvidenceSlotCoverageStatus.AMBIGUOUS));

        var prepayEvidence = reviewInput.pointEvidences().get(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY);
        assertThat(prepayEvidence).isNotNull();
        assertThat(prepayEvidence.status()).isEqualTo(EvidenceStatus.MISSING);
        assertThat(prepayEvidence.confidence()).isEqualTo(EvidenceConfidenceLevel.UNKNOWN.name());
        assertThat(prepayEvidence.diagnosticCode()).isEqualTo("SYS_INDEX_INCOMPLETE");
        assertThat(prepayEvidence.slotCoverages())
                .singleElement()
                .satisfies(slot -> assertThat(slot.coverageStatus()).isEqualTo(EvidenceSlotCoverageStatus.MISSING));
    }

    private static final class ProgressSemanticWholeTextConflictParser extends DocxWordParserSpike {

        @Override
        public com.cqcp.apiserver.wordparser.WordParserSpikeDocument parse(Path docxPath) {
            var blockA = new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    "block-progress-70",
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                    "进度款：形象进度产值的70%",
                    "进度款：形象进度产值的70%",
                    List.of("付款条款"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "test.docx", null, null, List.of(),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
            var blockB = new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    "block-node-85",
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                    "节点：形象进度产值的85%",
                    "节点：形象进度产值的85%",
                    List.of("付款条款"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "test.docx", null, null, List.of(),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
            var fullText = "进度款：形象进度产值的70%\n节点：形象进度产值的85%";
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument(
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.Metadata(
                            "progress-conflict", "progress-conflict.docx"),
                    List.of(blockA, blockB),
                    List.of(),
                    List.of(),
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseQualityReport(
                            "DOCX", "test", "zh-CN",
                            fullText.length(), 2, 0, 0, 0, 0, false,
                            com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.GOOD,
                            "HIGH", 0, 0, 0, List.of()));
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

    private static final class SameValueProgressContractParser extends DocxWordParserSpike {

        @Override
        public com.cqcp.apiserver.wordparser.WordParserSpikeDocument parse(Path docxPath) {
            var first = progressBlock("progress-block-1", "进度款按形象进度产值的70%支付");
            var second = progressBlock("progress-block-2", "本期进度款为形象进度产值的70%");
            var fullText = first.text() + "\n" + second.text();
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument(
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.Metadata(
                            "same-value-progress", "same-value-progress.docx"),
                    List.of(first, second),
                    List.of(),
                    List.of(),
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseQualityReport(
                            "DOCX", "test", "zh-CN", fullText.length(), 2, 0, 0, 0, 0, false,
                            com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.GOOD,
                            "HIGH", 0, 0, 0, List.of()));
        }

        private com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock progressBlock(
                String blockId,
                String text) {
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    blockId,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                    text,
                    text,
                    List.of("付款条款"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "same-value-progress.docx",
                    null,
                    null,
                    List.of(),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        }
    }

    // ────────── Deterministic parsers for observer/MONTHLY tests ──────────

    private static final class HasPrepaymentParser extends DocxWordParserSpike {

        @Override
        public com.cqcp.apiserver.wordparser.WordParserSpikeDocument parse(Path docxPath) {
            var block = new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    "block-prepay",
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                    "预付款20%",
                    "预付款20%",
                    List.of("付款条款"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "has-prepay.docx", null, null, List.of(),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
            var fullText = block.text();
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument(
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.Metadata("has-prepay", "has-prepay.docx"),
                    List.of(block), List.of(), List.of(),
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseQualityReport(
                            "DOCX", "test", "zh-CN", fullText.length(), 1, 0, 0, 0, 0, false,
                            com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.GOOD,
                            "HIGH", 0, 0, 0, List.of()));
        }
    }

    private static final class NoMatchPrepaymentParser extends DocxWordParserSpike {

        @Override
        public com.cqcp.apiserver.wordparser.WordParserSpikeDocument parse(Path docxPath) {
            var block = new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    "block-1",
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                    "本条款不涉及付款比例。",
                    "本条款不涉及付款比例。",
                    List.of("通用条款"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "no-match.docx", null, null, List.of(),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
            var fullText = block.text();
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument(
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.Metadata("no-match", "no-match.docx"),
                    List.of(block), List.of(), List.of(),
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseQualityReport(
                            "DOCX", "test", "zh-CN", fullText.length(), 1, 0, 0, 0, 0, false,
                            com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.GOOD,
                            "HIGH", 0, 0, 0, List.of()));
        }
    }

    private static final class MonthlyExactParser extends DocxWordParserSpike {

        @Override
        public com.cqcp.apiserver.wordparser.WordParserSpikeDocument parse(Path docxPath) {
            // blockA: inside MONTHLY payment clause slice (contains "按月度付款")
            var blockA = new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    "block-progress-70",
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                    "进度款：按月度付款，形象进度产值的70%。",
                    "进度款：按月度付款，形象进度产值的70%。",
                    List.of("付款条款"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "monthly.docx", null, null, List.of(),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
            // blockC: end marker for MONTHLY slice
            var blockC = new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    "block-end",
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                    "履约保函的说明：见附件。",
                    "履约保函的说明：见附件。",
                    List.of("其他条款"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "monthly.docx", null, null, List.of(),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
            // blockB: outside MONTHLY slice (after end marker); produces "75" via DIRECT pattern
            var blockB = new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.DocumentBlock(
                    "block-outside-75",
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH,
                    "进度款：支付至已完工程量的75%",
                    "进度款：支付至已完工程量的75%",
                    List.of("付款条款"),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "monthly.docx", null, null, List.of(),
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
            // Order: blockA (start marker), blockC (end marker), blockB (outside slice)
            // This ensures MONTHLY slice = [blockA] only
            var fullText = blockA.text() + "\n" + blockC.text() + "\n" + blockB.text();
            var scopeReport = new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ScopeCoverageReport(
                    true, List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of(), List.of());
            return new com.cqcp.apiserver.wordparser.WordParserSpikeDocument(
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.Metadata("monthly", "monthly.docx"),
                    List.of(blockA, blockC, blockB), List.of(), List.of(),
                    new com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseQualityReport(
                            "DOCX", "test", "zh-CN", fullText.length(), 3, 0, 0, 0, 0, false,
                            com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.GOOD,
                            "HIGH", 0, 0, 0, List.of()),
                    scopeReport);
        }
    }

    // ────────── Shared dispatcher test ──────────

    @Test
    void legacyBuildThroughSharedDispatcherProducesSameEvidence() throws IOException {
        // The legacy build(request, plan) now routes through probeAllForPoint(LEGACY),
        // not through the old resolveTextEvidence/resolveRatioEvidence directly.
        // This test verifies the same fixture still produces the same evidence.
        var fixtureCase = loadFixtureCases().getFirst();
        var reviewInput = buildReviewInput(fixtureCase);
        assertThat(reviewInput.pointEvidences()).hasSize(9);
        for (var code : ReviewPointCode.values()) {
            var evidence = reviewInput.pointEvidences().get(code);
            assertThat(evidence).as(code.name() + " evidence present in legacy build").isNotNull();
        }
    }

    @Test
    void c1BuildWithSnapshotProducesAllNineEvidences() throws IOException {
        var fixtureCase = loadFixtureCases().getFirst();
        var request = newRequest(fixtureCase);
        var parsed = preparer.parse(request.documentReference());
        var indexed = preparer.index(parsed);
        var plan = preparer.plan(indexed);

        var snapshot = makeTestSnapshot();
        var reviewInput = preparer.build(request, plan, snapshot);

        assertThat(reviewInput.runtimeRuleSetSnapshot()).isNotNull();
        assertThat(reviewInput.pointEvidences()).hasSize(9);
        for (var code : ReviewPointCode.values()) {
            var evidence = reviewInput.pointEvidences().get(code);
            assertThat(evidence).as(code.name() + " evidence present in C1 build").isNotNull();
        }
    }

    @Test
    void probeObserverRecordsLegacyPrepaymentStages() throws IOException {
        var observed = new java.util.ArrayList<String>();
        ParserBackedReviewInputPreparer.ProbeObserver observer =
                (code, mode, stage) -> observed.add(code.name() + "|" + mode.name() + "|" + stage);
        var preparerWithObserver = new ParserBackedReviewInputPreparer(
                new com.cqcp.apiserver.wordparser.DocxWordParserSpike(),
                new MinimalCandidateResolver(), observer);
        var fixtureCase = loadFixtureCases().getFirst();
        var request = newRequest(fixtureCase);
        var parsed = preparerWithObserver.parse(request.documentReference());
        var indexed = preparerWithObserver.index(parsed);
        var plan = preparerWithObserver.plan(indexed);
        preparerWithObserver.build(request, plan);

        var prepayLegacy = observed.stream()
                .filter(s -> s.startsWith("PREPAYMENT_RATIO_CONSISTENCY|LEGACY"))
                .toList();
        assertThat(prepayLegacy).isNotEmpty();
        assertThat(prepayLegacy).anyMatch(s -> s.endsWith("|SEMANTIC"));
        assertThat(prepayLegacy).anyMatch(s -> s.endsWith("|WHOLE_TEXT"));
    }

    @Test
    void c1PrepaymentDoesNotHaveWholeTextStage() throws IOException {
        var observed = new java.util.ArrayList<String>();
        ParserBackedReviewInputPreparer.ProbeObserver observer =
                (code, mode, stage) -> observed.add(code.name() + "|" + mode.name() + "|" + stage);
        var preparerWithObserver = new ParserBackedReviewInputPreparer(
                new com.cqcp.apiserver.wordparser.DocxWordParserSpike(),
                new MinimalCandidateResolver(), observer);
        var fixtureCase = loadFixtureCases().getFirst();
        var request = newRequest(fixtureCase);
        var parsed = preparerWithObserver.parse(request.documentReference());
        var indexed = preparerWithObserver.index(parsed);
        var plan = preparerWithObserver.plan(indexed);
        var snapshot = makeTestSnapshot();
        preparerWithObserver.build(request, plan, snapshot);

        var prepayC1WholeText = observed.stream()
                .filter(s -> s.startsWith("PREPAYMENT_RATIO_CONSISTENCY|CONSISTENCY_FULL_SCAN")
                        && s.endsWith("|WHOLE_TEXT"))
                .toList();
        assertThat(prepayC1WholeText).isEmpty();
    }

    @Test
    void prepaymentLegacyWithCandidatesExactOrderedStages() throws IOException {
        var observed = new java.util.ArrayList<String>();
        ParserBackedReviewInputPreparer.ProbeObserver observer =
                (code, mode, stage) -> observed.add(code.name() + "|" + mode.name() + "|" + stage);
        var preparerWithObserver = new ParserBackedReviewInputPreparer(
                new HasPrepaymentParser(), new MinimalCandidateResolver(), observer);
        var fixtureCase = new FixtureCase(
                "has-prepayment",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001.docx").normalize(),
                StructuredFieldSet.builder()
                        .put("partyAName", "甲方公司").put("partyBName", "乙方公司")
                        .put("contractTotalAmount", "100").put("taxExcludedAmount", "88.5")
                        .put("taxAmount", "11.5").put("paymentMethod", "MONTHLY")
                        .put("prepaymentRatio", "20").put("progressPaymentRatio", "70")
                        .put("completionPaymentRatio", "80").put("settlementPaymentRatio", "95")
                        .put("warrantyRetentionRatio", "5").build());
        var request = newRequest(fixtureCase);
        var parsed = preparerWithObserver.parse(request.documentReference());
        var indexed = preparerWithObserver.index(parsed);
        var plan = preparerWithObserver.plan(indexed);
        preparerWithObserver.build(request, plan);

        var stages = observed.stream()
                .filter(s -> s.startsWith("PREPAYMENT_RATIO_CONSISTENCY|LEGACY"))
                .map(s -> s.substring(s.lastIndexOf('|') + 1))
                .toList();
        // With candidates from SEMANTIC→DIRECT→WHOLE_TEXT = already found, no ROLE needed
        assertThat(stages).containsExactly("SEMANTIC", "DIRECT", "WHOLE_TEXT");
        assertThat(stages).doesNotContain("ROLE", "WEAK");
    }

    @Test
    void prepaymentC1WithCandidatesExactOrderedStages() throws IOException {
        var observed = new java.util.ArrayList<String>();
        ParserBackedReviewInputPreparer.ProbeObserver observer =
                (code, mode, stage) -> observed.add(code.name() + "|" + mode.name() + "|" + stage);
        var preparerWithObserver = new ParserBackedReviewInputPreparer(
                new HasPrepaymentParser(), new MinimalCandidateResolver(), observer);
        var fixtureCase = new FixtureCase(
                "has-prepayment-c1",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001.docx").normalize(),
                StructuredFieldSet.builder()
                        .put("partyAName", "甲方公司").put("partyBName", "乙方公司")
                        .put("contractTotalAmount", "100").put("taxExcludedAmount", "88.5")
                        .put("taxAmount", "11.5").put("paymentMethod", "MONTHLY")
                        .put("prepaymentRatio", "20").put("progressPaymentRatio", "70")
                        .put("completionPaymentRatio", "80").put("settlementPaymentRatio", "95")
                        .put("warrantyRetentionRatio", "5").build());
        var request = newRequest(fixtureCase);
        var parsed = preparerWithObserver.parse(request.documentReference());
        var indexed = preparerWithObserver.index(parsed);
        var plan = preparerWithObserver.plan(indexed);
        var snapshot = makeTestSnapshot();
        preparerWithObserver.build(request, plan, snapshot);

        var stages = observed.stream()
                .filter(s -> s.startsWith("PREPAYMENT_RATIO_CONSISTENCY|CONSISTENCY_FULL_SCAN"))
                .map(s -> s.substring(s.lastIndexOf('|') + 1))
                .toList();
        // C1 PREPAYMENT with candidates: SEMANTIC, DIRECT (no WHOLE_TEXT, no ROLE)
        assertThat(stages).containsExactly("SEMANTIC", "DIRECT");
        assertThat(stages).doesNotContain("WHOLE_TEXT", "ROLE", "WEAK");
    }

    @Test
    void prepaymentLegacyNoCandidatesExactOrderedStages() throws IOException {
        var observed = new java.util.ArrayList<String>();
        ParserBackedReviewInputPreparer.ProbeObserver observer =
                (code, mode, stage) -> observed.add(code.name() + "|" + mode.name() + "|" + stage);
        var preparerWithObserver = new ParserBackedReviewInputPreparer(
                new NoMatchPrepaymentParser(), new MinimalCandidateResolver(), observer);
        var fixtureCase = new FixtureCase(
                "no-match-prepayment",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001.docx").normalize(),
                StructuredFieldSet.builder()
                        .put("partyAName", "甲方公司").put("partyBName", "乙方公司")
                        .put("contractTotalAmount", "100").put("taxExcludedAmount", "88.5")
                        .put("taxAmount", "11.5").put("paymentMethod", "MONTHLY")
                        .put("prepaymentRatio", "20").put("progressPaymentRatio", "70")
                        .put("completionPaymentRatio", "80").put("settlementPaymentRatio", "95")
                        .put("warrantyRetentionRatio", "5").build());
        var request = newRequest(fixtureCase);
        var parsed = preparerWithObserver.parse(request.documentReference());
        var indexed = preparerWithObserver.index(parsed);
        var plan = preparerWithObserver.plan(indexed);
        preparerWithObserver.build(request, plan);

        var stages = observed.stream()
                .filter(s -> s.startsWith("PREPAYMENT_RATIO_CONSISTENCY|LEGACY"))
                .map(s -> s.substring(s.lastIndexOf('|') + 1))
                .toList();
        // No prepayment content → all stages execute
        assertThat(stages).containsExactly("SEMANTIC", "DIRECT", "WHOLE_TEXT", "ROLE");
        assertThat(stages).doesNotContain("WEAK");
    }

    @Test
    void prepaymentC1NoCandidatesExactOrderedStages() throws IOException {
        var observed = new java.util.ArrayList<String>();
        ParserBackedReviewInputPreparer.ProbeObserver observer =
                (code, mode, stage) -> observed.add(code.name() + "|" + mode.name() + "|" + stage);
        var preparerWithObserver = new ParserBackedReviewInputPreparer(
                new NoMatchPrepaymentParser(), new MinimalCandidateResolver(), observer);
        var fixtureCase = new FixtureCase(
                "no-match-prepayment-c1",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001.docx").normalize(),
                StructuredFieldSet.builder()
                        .put("partyAName", "甲方公司").put("partyBName", "乙方公司")
                        .put("contractTotalAmount", "100").put("taxExcludedAmount", "88.5")
                        .put("taxAmount", "11.5").put("paymentMethod", "MONTHLY")
                        .put("prepaymentRatio", "20").put("progressPaymentRatio", "70")
                        .put("completionPaymentRatio", "80").put("settlementPaymentRatio", "95")
                        .put("warrantyRetentionRatio", "5").build());
        var request = newRequest(fixtureCase);
        var parsed = preparerWithObserver.parse(request.documentReference());
        var indexed = preparerWithObserver.index(parsed);
        var plan = preparerWithObserver.plan(indexed);
        var snapshot = makeTestSnapshot();
        preparerWithObserver.build(request, plan, snapshot);

        var stages = observed.stream()
                .filter(s -> s.startsWith("PREPAYMENT_RATIO_CONSISTENCY|CONSISTENCY_FULL_SCAN"))
                .map(s -> s.substring(s.lastIndexOf('|') + 1))
                .toList();
        // C1: no WHOLE_TEXT, no WEAK for PREPAYMENT
        assertThat(stages).doesNotContain("WHOLE_TEXT", "WEAK");
        // No candidates → SEMANTIC, DIRECT, ROLE
        assertThat(stages).containsExactly("SEMANTIC", "DIRECT", "ROLE");
    }

    @Test
    void monthlyLegacySliceVsC1FullScan() throws IOException {
        var monthlyPreparer = new ParserBackedReviewInputPreparer(
                new MonthlyExactParser(), new MinimalCandidateResolver());
        var fixtureCase = new FixtureCase(
                "monthly-slice-c1",
                FIXTURE_ROOT.resolve("docx").resolve("CQCP-MVP-DOCX-001.docx").normalize(),
                StructuredFieldSet.builder()
                        .put("partyAName", "甲方公司").put("partyBName", "乙方公司")
                        .put("contractTotalAmount", "100").put("taxExcludedAmount", "88.5")
                        .put("taxAmount", "11.5").put("paymentMethod", "MONTHLY")
                        .put("prepaymentRatio", "20").put("progressPaymentRatio", "70")
                        .put("completionPaymentRatio", "80").put("settlementPaymentRatio", "95")
                        .put("warrantyRetentionRatio", "5").build());

        // Legacy build with MONTHLY payment clause slice: only sees blockA (progress-70 inside slice)
        var legacyInput = buildReviewInput(monthlyPreparer, fixtureCase);
        var legacyProgressEv = legacyInput.pointEvidences().get(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY);
        assertThat(legacyProgressEv).isNotNull();
        // Legacy slice: only blockA (progress-70) within payment-clause slice
        assertThat(legacyProgressEv.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(legacyProgressEv.candidateValue()).isEqualTo("70");
        assertThat(legacyProgressEv.blockId()).isEqualTo("block-progress-70");

        // C1 build with full document scan (no paymentMethod gating), verified scope report
        var request = newRequest(fixtureCase);
        var parsed = monthlyPreparer.parse(request.documentReference());
        var indexed = monthlyPreparer.index(parsed);
        var plan = monthlyPreparer.plan(indexed);
        var c1Input = monthlyPreparer.build(request, plan, makeTestSnapshot());
        var c1ProgressEv = c1Input.pointEvidences().get(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY);
        assertThat(c1ProgressEv).isNotNull();
        // C1 full-document scan sees both 70 (block-progress-70) and 75 (block-outside-75)
        assertThat(c1ProgressEv.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(c1ProgressEv.candidateValue()).isNull(); // two distinct values → null projection
        assertThat(c1ProgressEv.occurrences()).hasSize(2);
        // Exact occurrence tuples from system output
        assertThat(c1ProgressEv.occurrences())
                .extracting(PointEvidenceOccurrence::candidateValue,
                        PointEvidenceOccurrence::blockId,
                        PointEvidenceOccurrence::locationLevel,
                        PointEvidenceOccurrence::regionType,
                        PointEvidenceOccurrence::confidence,
                        PointEvidenceOccurrence::previewElementRef)
                .containsExactlyInAnyOrder(
                        tuple("70", "block-progress-70", "BLOCK_LEVEL", "BODY", "HIGH", null),
                        tuple("75", "block-outside-75", "BLOCK_LEVEL", "BODY", "HIGH", null));
        // Slot coverage: single SATISFIED slot with reliableAnchor=true
        assertThat(c1ProgressEv.slotCoverages()).hasSize(1);
        assertThat(c1ProgressEv.slotCoverages()).allMatch(s ->
                s.coverageStatus() == EvidenceSlotCoverageStatus.SATISFIED && s.reliableAnchor());
    }

    private static RuntimeRuleSetSnapshot makeTestSnapshot() {
        var map = new java.util.EnumMap<ReviewPointCode, ConsistencyPolicySnapshot>(ReviewPointCode.class);
        // B1-faithful identity fields
        var identityBlock = List.of("reviewPointCode", "blockId");
        var identityCell = List.of("reviewPointCode", "blockId", "previewElementRef");
        // B1-faithful attribution signals
        var reqSignals = List.of("SOURCE_CONFIDENCE", "PARSE_CONFIDENCE", "VALUE_GRAMMAR",
                "ROLE_LABEL", "REGION_CONTEXT", "ANCHOR_IDENTITY");
        // B1-faithful strong excluded context types
        var strongExcluded = List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED");
        // B1-faithful PARTY_A semantic contexts
        var partyASemantic = List.of("CONTRACT_TITLE_NAME_MENTION", "AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION");
        var noSemantic = List.<String>of();

        var textPolicy = new ConsistencyPolicySnapshot(
                "CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1",
                List.of("BODY", "APPENDIX"), strongExcluded, reqSignals, noSemantic,
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1", identityBlock, identityCell);
        var cnyPolicy = new ConsistencyPolicySnapshot(
                "CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1",
                List.of("BODY", "APPENDIX"), strongExcluded, reqSignals, noSemantic,
                "consistency-canonicalization-v20260715.1", "DECIMAL", "CNY",
                "mvp-occurrence-identity-v1", identityBlock, identityCell);
        var percentPolicy = new ConsistencyPolicySnapshot(
                "CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1",
                List.of("BODY", "APPENDIX"), strongExcluded, reqSignals, noSemantic,
                "consistency-canonicalization-v20260715.1", "DECIMAL", "PERCENT",
                "mvp-occurrence-identity-v1", identityBlock, identityCell);
        var partyATextPolicy = new ConsistencyPolicySnapshot(
                "CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1",
                List.of("BODY", "APPENDIX"), strongExcluded, reqSignals, partyASemantic,
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1", identityBlock, identityCell);
        for (var code : ReviewPointCode.values()) {
            switch (code) {
                case PARTY_A_NAME_CONSISTENCY -> map.put(code, partyATextPolicy);
                case PARTY_B_NAME_CONSISTENCY -> map.put(code, textPolicy);
                case CONTRACT_TOTAL_AMOUNT_CONSISTENCY, TAX_AMOUNT_FORMULA_CONSISTENCY -> map.put(code, cnyPolicy);
                case PREPAYMENT_RATIO_CONSISTENCY, PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                     COMPLETION_PAYMENT_RATIO_CONSISTENCY, SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
                     WARRANTY_RETENTION_RATIO_CONSISTENCY -> map.put(code, percentPolicy);
            }
        }
        return new RuntimeRuleSetSnapshot("test", "v20260715.1", "test-rp", "v20260715.1", map);
    }
}
