package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.cqcp.apiserver.wordparser.DocxWordParserSpike;
import com.cqcp.apiserver.wordparser.WordParserSpikeDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ParserBackedEvidenceOverlapBaselineTest {

    private static final Path FIXTURE_ROOT = Path.of("..", "..", "packages", "test-fixtures").normalize();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ParserBackedReviewInputPreparer preparer =
            new ParserBackedReviewInputPreparer(new DocxWordParserSpike());

    @Test
    void fourPositiveFixturesReachPerfectRequiredHitRate() throws IOException {
        var results = new ArrayList<EvidenceOverlapResult>();

        for (Path expectedPath : expectedFixturePaths()) {
            var root = objectMapper.readTree(Files.readString(expectedPath));
            var fixtureCase = fixtureCase(root);
            var reviewInput = buildReviewInput(preparer, fixtureCase);
            var evaluationCases = root.at("/goldenExpected/evidenceEvaluation/positiveCases");

            assertThat(evaluationCases.isArray())
                    .as("%s must define positive evidence evaluation cases", fixtureCase.sampleId())
                    .isTrue();
            assertThat(evaluationCases).hasSize(1);

            var evaluationCase = evaluationCases.get(0);
            var reviewPointCode = ReviewPointCode.valueOf(evaluationCase.path("reviewPointCode").asText());
            var evidence = reviewInput.pointEvidences().get(reviewPointCode);
            var expectedCandidateValue = evaluationCase.path("expectedCandidateValue").asText();
            var expectedAnchors = stringSet(evaluationCase.path("expectedCanonicalAnchors"));

            assertThat(evidence.candidateValue())
                    .as(fixtureCase.sampleId() + " candidateValue")
                    .isEqualTo(expectedCandidateValue);

            var result = EvidenceOverlapEvaluator.evaluatePositive(
                    expectedAnchors,
                    sourceAnchors(evidence));
            results.add(result);

            assertThat(result.expectedRecall()).isEqualByComparingTo("1.0000");
            assertThat(result.actualPrecision()).isEqualByComparingTo("1.0000");
            assertThat(result.requiredHit()).isEqualTo(1);
            assertThat(result.attributionFailureReason()).isNull();
        }

        var requiredHitRate = BigDecimal.valueOf(
                        results.stream().mapToInt(EvidenceOverlapResult::requiredHit).sum())
                .divide(BigDecimal.valueOf(results.size()), 4, java.math.RoundingMode.HALF_UP);
        assertThat(requiredHitRate).isEqualByComparingTo("1.0000");
    }

    @Test
    void existingConflictMediumAndLowCasesNeverPassAsPositiveAttribution() {
        var baseFixture = loadFixture("CQCP-MVP-DOCX-001");

        for (var negativeCase : List.of(
                new NegativeDocxCase(
                        "CQCP-MVP-DOCX-001-progress-conflict",
                        EvidenceConfidenceLevel.CONFLICTED),
                new NegativeDocxCase(
                        "CQCP-MVP-DOCX-001-progress-medium",
                        EvidenceConfidenceLevel.MEDIUM),
                new NegativeDocxCase(
                        "CQCP-MVP-DOCX-001-progress-low",
                        EvidenceConfidenceLevel.LOW))) {
            var fixtureCase = new FixtureCase(
                    negativeCase.sampleId(),
                    FIXTURE_ROOT.resolve("docx").resolve(negativeCase.sampleId() + ".docx").normalize(),
                    baseFixture.structuredFields());
            var evidence = buildReviewInput(preparer, fixtureCase)
                    .pointEvidences()
                    .get(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY);

            assertThat(evidence.confidence()).isEqualTo(negativeCase.expectedConfidence().name());
            var result = EvidenceOverlapEvaluator.evaluateNegative(
                    evidence.status(),
                    Set.of(),
                    sourceAnchors(evidence));
            assertThat(result.requiredHit()).isZero();
            assertThat(result.attributionFailureReason())
                    .isEqualTo(AttributionFailureReason.ATTRIBUTION_AMBIGUOUS);
        }
    }

    @Test
    void correctCandidateValueWithWrongBlockAndWrongCellFailsEvaluation() {
        var wrongBlock = EvidenceOverlapEvaluator.evaluatePositive(
                Set.of("BLOCK:block-38"),
                List.of(anchor("block-39", null)));
        var wrongCell = EvidenceOverlapEvaluator.evaluatePositive(
                Set.of("TABLE_CELL:block-4:0:0"),
                List.of(anchor("block-4", "table:table-1/row:0/cell:1")));

        assertThat(wrongBlock.requiredHit()).isZero();
        assertThat(wrongBlock.attributionFailureReason())
                .isEqualTo(AttributionFailureReason.WRONG_BLOCK_ATTRIBUTION);
        assertThat(wrongCell.requiredHit()).isZero();
        assertThat(wrongCell.attributionFailureReason())
                .isEqualTo(AttributionFailureReason.WRONG_TABLE_CELL_ATTRIBUTION);
    }

    @Test
    void testOnlyParserBackedCaseProducesTableCellCanonicalKey() {
        var fixtureCase = loadFixture("CQCP-MVP-DOCX-001");
        var input = buildReviewInput(
                new ParserBackedReviewInputPreparer(new CellAnchorContractParser()),
                fixtureCase);
        var evidence = input.pointEvidences().get(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY);

        assertThat(EvidenceOverlapEvaluator.canonicalKey(sourceAnchors(evidence).getFirst()))
                .contains("TABLE_CELL:block-cell:1:1");
    }

    private ReviewEngineInput buildReviewInput(
            ParserBackedReviewInputPreparer targetPreparer,
            FixtureCase fixtureCase) {
        var request = TaskExecutionRequest.forDocument(
                new ReviewTaskRecord(
                        "task-" + fixtureCase.sampleId(),
                        "测试合同",
                        fixtureCase.structuredFields().asMap()),
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
        var parsed = targetPreparer.parse(request.documentReference());
        return targetPreparer.build(request, targetPreparer.plan(targetPreparer.index(parsed)));
    }

    private List<SourceAnchorSummary> sourceAnchors(PointEvidence evidence) {
        if (evidence == null || evidence.blockId() == null || evidence.blockId().isBlank()) {
            return List.of();
        }
        return List.of(new SourceAnchorSummary(
                evidence.blockId(),
                evidence.sourceOrigin(),
                evidence.sourceExtractionMode(),
                evidence.contextType(),
                evidence.evidenceSummary(),
                evidence.sectionPath(),
                evidence.regionType(),
                evidence.confidence(),
                evidence.locationLevel(),
                evidence.previewElementRef()));
    }

    private SourceAnchorSummary anchor(String blockId, String previewElementRef) {
        return new SourceAnchorSummary(
                blockId,
                "NATIVE_WORD",
                "STRUCTURED",
                "NORMAL",
                "测试证据",
                List.of(),
                "BODY",
                "HIGH",
                "BLOCK_LEVEL",
                previewElementRef);
    }

    private FixtureCase loadFixture(String sampleId) {
        try {
            return fixtureCase(objectMapper.readTree(Files.readString(
                    FIXTURE_ROOT.resolve("expected").resolve(sampleId + ".json"))));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load fixture: " + sampleId, exception);
        }
    }

    private FixtureCase fixtureCase(JsonNode root) {
        var builder = StructuredFieldSet.builder();
        root.at("/goldenExpected/structuredFields")
                .fields()
                .forEachRemaining(entry -> builder.put(entry.getKey(), entry.getValue().asText()));
        return new FixtureCase(
                root.path("sampleId").asText(),
                FIXTURE_ROOT.resolve(root.path("sourceDocx").asText()).normalize(),
                builder.build());
    }

    private Set<String> stringSet(JsonNode arrayNode) {
        var result = new java.util.LinkedHashSet<String>();
        arrayNode.forEach(node -> result.add(node.asText()));
        return Set.copyOf(result);
    }

    private List<Path> expectedFixturePaths() throws IOException {
        try (Stream<Path> paths = Files.list(FIXTURE_ROOT.resolve("expected"))) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private record FixtureCase(
            String sampleId,
            Path docxPath,
            StructuredFieldSet structuredFields) {
    }

    private record NegativeDocxCase(
            String sampleId,
            EvidenceConfidenceLevel expectedConfidence) {
    }

    private static final class CellAnchorContractParser extends DocxWordParserSpike {

        @Override
        public WordParserSpikeDocument parse(Path docxPath) {
            var firstCell = "进度款";
            var secondCell = "形象进度产值的70%";
            var text = firstCell + " | " + secondCell;
            var secondCellStart = firstCell.length() + " | ".length();
            return new WordParserSpikeDocument(
                    new WordParserSpikeDocument.Metadata("cell", "cell.docx"),
                    List.of(new WordParserSpikeDocument.DocumentBlock(
                            "block-cell",
                            WordParserSpikeDocument.BlockType.TABLE_ROW,
                            text,
                            text,
                            List.of("付款条款"),
                            WordParserSpikeDocument.RegionType.BODY,
                            WordParserSpikeDocument.ContextType.NORMAL,
                            WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                            WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                            "cell.docx",
                            "table-cell",
                            1,
                            List.of(
                                    new WordParserSpikeDocument.TableCellSpan(
                                            0, firstCell, 0, firstCell.length()),
                                    new WordParserSpikeDocument.TableCellSpan(
                                            1, secondCell, secondCellStart, text.length())),
                            WordParserSpikeDocument.ConfidenceLevel.HIGH,
                            WordParserSpikeDocument.PreviewAnchorLevel.TABLE_CELL)),
                    List.of(),
                    List.of(),
                    new WordParserSpikeDocument.ParseQualityReport(
                            "DOCX",
                            "test",
                            "zh-CN",
                            text.length(),
                            1,
                            0,
                            1,
                            0,
                            0,
                            false,
                            WordParserSpikeDocument.ParseStatus.GOOD,
                            "HIGH",
                            0,
                            0,
                            0,
                            List.of()));
        }
    }
}
