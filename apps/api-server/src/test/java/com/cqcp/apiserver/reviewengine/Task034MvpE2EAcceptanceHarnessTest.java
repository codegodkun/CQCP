package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.cqcp.apiserver.tuning.PointDiagnostic;
import com.cqcp.apiserver.wordparser.DocxWordParserSpike;
import com.cqcp.apiserver.wordparser.WordParserSpikeDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** TASK_SPEC-034-A：仅测试源码可见的 MVP E2E 验收入口。 */
class Task034MvpE2eAcceptanceHarnessTest {

    private static final String SCHEMA_VERSION = "task034-acceptance-v1";
    private static final String FORMAL_PROPERTY = "cqcp.task034.formal";
    private static final String FORMAL_INPUT_PROPERTY = "cqcp.task034.formalInput";
    private static final String QUERY_PATH_TEMPLATE = "/api/v1/tasks/%s/result";
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
    private static final Path FIXTURE_ROOT =
            Path.of("..", "..", "packages", "test-fixtures").normalize();
    private static final Path FORMAL_OUTPUT_ROOT =
            Path.of("..", "..", "outputs", "task-034-mvp-e2e-acceptance").normalize();
    private static final List<String> FROZEN_SAMPLE_IDS = List.of(
            "CQCP-MVP-DOCX-001", "CQCP-MVP-DOCX-002", "CQCP-MVP-DOCX-003");
    private static final Map<String, Integer> FROZEN_TOTALS = Map.of(
            "CQCP-MVP-DOCX-001", 22,
            "CQCP-MVP-DOCX-002", 19,
            "CQCP-MVP-DOCX-003", 22);
    private static final Map<String, Integer> FROZEN_INCLUDED = Map.of(
            "CQCP-MVP-DOCX-001", 20,
            "CQCP-MVP-DOCX-002", 17,
            "CQCP-MVP-DOCX-003", 20);
    private static final Pattern TABLE_REF = Pattern.compile(
            "table:([^/]+)/row:(\\d+)(?:/cell:(\\d+))?");
    private static final Pattern BLOCK_REF = Pattern.compile("block:([^/]+)");
    private static final List<String> EXPECTED_STAGE_EVENTS = List.of(
            "PARSING:STARTED", "PARSING:COMPLETED",
            "INDEXING:STARTED", "INDEXING:COMPLETED",
            "PLANNING:STARTED", "PLANNING:COMPLETED",
            "BUILDING_EVIDENCE:STARTED", "BUILDING_EVIDENCE:COMPLETED",
            "REVIEWING_RULES:STARTED", "REVIEWING_RULES:COMPLETED",
            "COMPOSING:STARTED", "COMPOSING:COMPLETED");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void syntheticDocxProvesSingleRunReferenceChainStageOrderAndSameStoreQuery(@TempDir Path tempDir)
            throws Exception {
        Path docx = writeSyntheticDocx(tempDir.resolve("task-034-synthetic.docx"));
        SampleInput input = syntheticInput(docx);
        Map<ReviewPointCode, String> testOnlyExpected = testOnlyExpectedCandidates();
        ObservedSampleRun run = executeOne(input, testOnlyExpected);

        assertThat(run.result().execution().status())
                .isIn(ExecutionStatus.SUCCESS, ExecutionStatus.PARTIAL_SUCCESS);
        assertThat(run.querySnapshot()).isSameAs(run.result().snapshot());
        assertThat(run.querySnapshot().taskId()).isEqualTo(input.taskId());
        assertThat(run.querySnapshot().executionId()).isEqualTo(input.executionId());
        assertThat(run.observation().parsedDocument().document().blocks()).isNotEmpty();
        assertThat(run.observation().reviewInput().taskId()).isEqualTo(input.taskId());
        assertThat(run.observation().reviewInput().executionId()).isEqualTo(input.executionId());
        assertThat(run.stageEvents()).containsExactlyElementsOf(EXPECTED_STAGE_EVENTS);
        assertThat(run.reviewInvocationCount()).isEqualTo(1);
        assertThat(run.sampleResult().queryPath()).isEqualTo(QUERY_PATH_TEMPLATE.formatted(input.taskId()));
        assertThat(run.sampleResult().executionMetadata().versionReferences())
                .isEqualTo(snapshotVersions(run.querySnapshot()));
        assertQueriedFieldsAreUnmodified(run.sampleResult(), run.querySnapshot());
    }

    @Test
    void bipartiteComparatorIsOrderIndependentAndRejectsManyToOneAndOneToMany() {
        ParsedContractDocument parsed = parsedDocument(List.of(
                tableBlock("block-1", "table-1", 0, List.of("甲方", "奔腾公司")),
                tableBlock("block-2", "table-2", 0, List.of("甲方", "奔腾公司"))));
        ActualAnchor first = actualCell("block-1", "table-1", 0, 1);
        ActualAnchor second = actualCell("block-2", "table-2", 0, 1);

        List<HumanOccurrence> manyHuman = List.of(
                human("H-01", true, "奔腾公司", "奔腾公司"),
                human("H-02", true, "奔腾公司", "奔腾公司"));
        List<OccurrenceComparisonRow> manyToOne = compareOccurrences(manyHuman, parsed, List.of(first));
        assertThat(manyToOne).extracting(OccurrenceComparisonRow::coverageResult)
                .containsOnly(CoverageResult.NOT_OBSERVABLE);

        var reversed = new ArrayList<>(manyHuman);
        Collections.reverse(reversed);
        Map<String, CoverageResult> forwardById = coverageByOccurrence(manyToOne);
        Map<String, CoverageResult> reversedById = coverageByOccurrence(
                compareOccurrences(reversed, parsed, List.of(first)));
        assertThat(reversedById).isEqualTo(forwardById);

        List<OccurrenceComparisonRow> oneToMany = compareOccurrences(
                List.of(human("H-03", true, "奔腾公司", "奔腾公司")),
                parsed,
                List.of(first, second));
        assertThat(oneToMany.getFirst().coverageResult()).isEqualTo(CoverageResult.NOT_OBSERVABLE);

        List<OccurrenceComparisonRow> mutualUnique = compareOccurrences(
                List.of(human("H-04", true, "奔腾公司", "奔腾公司")), parsed, List.of(first));
        assertThat(mutualUnique.getFirst().coverageResult()).isEqualTo(CoverageResult.MATCHED);
    }

    @Test
    void nonmatchRequiresUniqueContextProjectionAndNeverUsesFirstOrReusesActual() {
        ParsedContractDocument parsed = parsedDocument(List.of(
                tableBlock("block-1", "table-1", 0, List.of("甲方", "奔腾公司")),
                tableBlock("block-2", "table-2", 0, List.of("甲方", "奔腾公司"))));
        ActualAnchor first = actualCell("block-1", "table-1", 0, 1);
        ActualAnchor second = actualCell("block-2", "table-2", 0, 1);
        HumanOccurrence nonmatch = human("N-01", true, "前水公司", "奔腾公司");

        var unique = compareOccurrences(List.of(nonmatch), parsed, List.of(first));
        assertThat(unique.getFirst().coverageResult()).isEqualTo(CoverageResult.NOT_MATCHED);
        assertThat(unique.getFirst().actualAnchorReference()).isEqualTo("TABLE_CELL:block-1:0:1");

        var multipleActual = compareOccurrences(List.of(nonmatch), parsed, List.of(first, second));
        assertThat(multipleActual.getFirst().coverageResult()).isEqualTo(CoverageResult.NOT_OBSERVABLE);
        assertUnavailable(multipleActual.getFirst());

        var twoHuman = compareOccurrences(
                List.of(nonmatch, human("N-02", true, "奔腾公司", "奔腾公司")),
                parsed,
                List.of(first));
        assertThat(twoHuman).extracting(OccurrenceComparisonRow::coverageResult)
                .containsOnly(CoverageResult.NOT_OBSERVABLE);
        assertThat(twoHuman).allSatisfy(Task034MvpE2eAcceptanceHarnessTest::assertUnavailable);
    }

    @Test
    void contextCannotProjectAndNullPreviewReferenceStayNotObservable() {
        ParsedContractDocument parsed = parsedDocument(List.of(
                tableBlock("block-1", "table-1", 0, List.of("甲方", "奔腾公司"))));
        HumanOccurrence noContextProjection = new HumanOccurrence(
                "SYNTHETIC", "C-01", ReviewPointCode.PARTY_A_NAME_CONSISTENCY, true,
                "奔腾公司", "奔腾公司", "第1页", "TABLE_CELL",
                "合同封面", "甲方 | 奔腾公司（人工描述）", "右侧值单元格", null);

        var noProjection = compareOccurrences(
                List.of(noContextProjection), parsed, List.of(actualCell("block-1", "table-1", 0, 1)));
        assertThat(noProjection.getFirst().coverageResult()).isEqualTo(CoverageResult.NOT_OBSERVABLE);
        assertUnavailable(noProjection.getFirst());

        HumanOccurrence conflictingContext = new HumanOccurrence(
                "SYNTHETIC", "C-01-B", ReviewPointCode.PARTY_A_NAME_CONSISTENCY, true,
                "奔腾公司", "奔腾公司", "", "TABLE_CELL",
                "", "明确错误的行上下文", "奔腾公司", null);
        var conflicting = compareOccurrences(
                List.of(conflictingContext), parsed, List.of(actualCell("block-1", "table-1", 0, 1)));
        assertThat(conflicting.getFirst().coverageResult()).isEqualTo(CoverageResult.NOT_OBSERVABLE);
        assertUnavailable(conflicting.getFirst());

        ActualAnchor nullPreview = new ActualAnchor(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                new SourceAnchorSummary(
                        "block-1", "NATIVE_WORD", "STRUCTURED", "NORMAL", "证据",
                        List.of(), "BODY", "HIGH", "BLOCK_LEVEL", null));
        var nullReference = compareOccurrences(
                List.of(human("C-02", true, "奔腾公司", "奔腾公司")),
                parsed,
                List.of(nullPreview));
        assertThat(nullReference.getFirst().coverageResult()).isEqualTo(CoverageResult.NOT_OBSERVABLE);
        assertUnavailable(nullReference.getFirst());
    }

    @Test
    void candidateComparisonUsesOnlyStringStripAndPreservesNull() {
        assertThat(compareCandidate("70%", "70")).isEqualTo(CandidateComparison.MISMATCH);
        assertThat(compareCandidate("1,130", "1130")).isEqualTo(CandidateComparison.MISMATCH);
        assertThat(compareCandidate("ABC", "abc")).isEqualTo(CandidateComparison.MISMATCH);
        assertThat(compareCandidate("  奔腾公司  ", "奔腾公司")).isEqualTo(CandidateComparison.MATCH);
        assertThat(compareCandidate("奔腾公司", null)).isEqualTo(CandidateComparison.NOT_OBSERVABLE);
    }

    @Test
    void anchorNormalizationCollapsesButDoesNotDeleteUnicodeWhitespace() {
        assertThat(normalizeAnchorText("甲 方")).isNotEqualTo(normalizeAnchorText("甲方"));
        assertThat(normalizeAnchorText("  甲\r\n方\t公司  "))
                .isEqualTo("甲 方 公司");
        assertThat(normalizeAnchorText("甲\u00a0\u00a0方"))
                .isEqualTo("甲 方");
    }

    @Test
    void frozenHumanFixturesRemain63Included57Excluded6AndProvideUniqueExpectedPerPoint()
            throws Exception {
        var allRows = new ArrayList<OccurrenceComparisonRow>();
        var counts = new LinkedHashMap<String, List<Integer>>();
        for (String sampleId : FROZEN_SAMPLE_IDS) {
            List<HumanOccurrence> occurrences = loadHumanOccurrences(sampleId, FIXTURE_ROOT);
            Map<ReviewPointCode, String> expected = expectedCandidatesFromHumanFixture(occurrences);
            assertThat(expected).isNotEmpty();
            List<OccurrenceComparisonRow> rows = compareOccurrences(
                    occurrences, parsedDocument(List.of()), List.of());
            allRows.addAll(rows);
            int included = (int) occurrences.stream().filter(HumanOccurrence::included).count();
            counts.put(sampleId, List.of(occurrences.size(), included, occurrences.size() - included));
        }

        assertThat(allRows).hasSize(63);
        assertThat(allRows.stream().filter(row -> row.coverageResult() == CoverageResult.EXCLUDED)).hasSize(6);
        assertThat(allRows.stream().filter(row -> row.coverageResult() != CoverageResult.EXCLUDED)).hasSize(57);
        assertThat(counts).containsExactly(
                Map.entry("CQCP-MVP-DOCX-001", List.of(22, 20, 2)),
                Map.entry("CQCP-MVP-DOCX-002", List.of(19, 17, 2)),
                Map.entry("CQCP-MVP-DOCX-003", List.of(22, 20, 2)));
        assertThat(allRows.stream().filter(row -> !row.includedInConsistencyEvaluation()))
                .allMatch(row -> row.coverageResult() == CoverageResult.EXCLUDED);
    }

    @Test
    void defaultModeWritesNothingAndFormalMetadataIsMandatory(@TempDir Path outputRoot) throws Exception {
        HarnessRunInput disabled = new HarnessRunInput(
                FROZEN_SAMPLE_IDS, FIXTURE_ROOT, outputRoot, false,
                new RunMetadata("", "", "", "TEST_ONLY"));

        HarnessRunResult skipped = runAcceptance(disabled, false);

        assertThat(skipped.overallVerdict()).isEqualTo(OverallVerdict.SKIPPED);
        try (var files = Files.list(outputRoot)) {
            assertThat(files).isEmpty();
        }

        HarnessRunInput missingMetadata = new HarnessRunInput(
                FROZEN_SAMPLE_IDS, FIXTURE_ROOT, outputRoot, true,
                new RunMetadata("", "", "", "FORMAL_SYSTEM_PROPERTIES"));
        assertThatThrownBy(() -> validateFormalGate(missingMetadata, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("formal metadata");

        HarnessRunInput wrongOrder = new HarnessRunInput(
                List.of("CQCP-MVP-DOCX-002", "CQCP-MVP-DOCX-001", "CQCP-MVP-DOCX-003"),
                FIXTURE_ROOT, outputRoot, true,
                new RunMetadata("commit", "branch", "gradle", "FORMAL_SYSTEM_PROPERTIES"));
        assertThatThrownBy(() -> validateFormalGate(wrongOrder, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fixed sample order");
    }

    @Test
    void formalPropertyAndInputOptInAreIndependentAndBothRequired(@TempDir Path outputRoot)
            throws Exception {
        RunMetadata missingMetadata = new RunMetadata("", "", "", "FORMAL_SYSTEM_PROPERTIES");
        HarnessRunInput inputFalse = new HarnessRunInput(
                FROZEN_SAMPLE_IDS, FIXTURE_ROOT, outputRoot, false, missingMetadata);
        HarnessRunInput inputTrue = new HarnessRunInput(
                FROZEN_SAMPLE_IDS, FIXTURE_ROOT, outputRoot, true, missingMetadata);

        assertThat(runAcceptance(inputFalse, false).overallVerdict()).isEqualTo(OverallVerdict.SKIPPED);
        assertThat(runAcceptance(inputFalse, true).overallVerdict()).isEqualTo(OverallVerdict.SKIPPED);
        assertThat(runAcceptance(inputTrue, false).overallVerdict()).isEqualTo(OverallVerdict.SKIPPED);
        try (var files = Files.list(outputRoot)) {
            assertThat(files).isEmpty();
        }

        assertThatThrownBy(() -> runAcceptance(inputTrue, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("formal metadata");
        try (var files = Files.list(outputRoot)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void formalVerdictFailsOnCandidateEvenWhenOccurrenceCountsArePerfect() {
        List<OccurrenceComparisonRow> rows = perfectCountRows();
        List<SampleAcceptanceResult> samples = new ArrayList<>();
        for (String sampleId : FROZEN_SAMPLE_IDS) {
            CandidateComparison comparison = sampleId.endsWith("001")
                    ? CandidateComparison.MISMATCH : CandidateComparison.MATCH;
            samples.add(verdictSample(sampleId, comparison));
        }

        assertThat(rows).hasSize(63);
        assertThat(determineOverallVerdict(samples, rows, 57, 6)).isEqualTo(OverallVerdict.FAIL);
    }

    @Test
    void aggregateManifestPreservesEverySampleIdentityAndVersionReference() {
        Instant started = Instant.parse("2026-07-14T01:00:00Z");
        Instant completed = Instant.parse("2026-07-14T01:00:05Z");
        RunMetadata metadata = new RunMetadata(
                "TEST_ONLY_COMMIT", "TEST_ONLY_BRANCH", "TEST_ONLY_GRADLE", "TEST_ONLY");
        HarnessRunInput input = new HarnessRunInput(
                FROZEN_SAMPLE_IDS, FIXTURE_ROOT, Path.of("TEST_ONLY_OUTPUT"), true, metadata);
        var samples = new ArrayList<SampleAcceptanceResult>();
        var expectedSnapshotTimes = new ArrayList<Instant>();
        for (int index = 0; index < FROZEN_SAMPLE_IDS.size(); index++) {
            Instant snapshotCreatedAt = Instant.parse("2026-07-14T01:00:0" + index + "Z");
            expectedSnapshotTimes.add(snapshotCreatedAt);
            samples.add(verdictSample(
                    FROZEN_SAMPLE_IDS.get(index), CandidateComparison.MATCH, versions("sample-" + index),
                    index == 1 ? SnapshotStatus.PARTIAL_SUCCESS : SnapshotStatus.SUCCESS,
                    snapshotCreatedAt));
        }

        RunManifest manifest = aggregateRunManifest(
                input, samples, started, completed, "TEST_ONLY_EXPLICIT_TIMES");

        assertThat(manifest.sampleOrder()).containsExactlyElementsOf(FROZEN_SAMPLE_IDS);
        assertThat(manifest.samples()).extracting(entry -> entry.versionReferences().schemaVersion())
                .containsExactly("sample-0", "sample-1", "sample-2");
        assertThat(manifest.samples()).extracting(SampleManifestEntry::snapshotStatus)
                .containsExactly(SnapshotStatus.SUCCESS, SnapshotStatus.PARTIAL_SUCCESS, SnapshotStatus.SUCCESS);
        assertThat(manifest.samples()).extracting(SampleManifestEntry::snapshotCreatedAt)
                .containsExactlyElementsOf(expectedSnapshotTimes);
        assertThat(manifest.startedAt()).isEqualTo(started);
        assertThat(manifest.completedAt()).isEqualTo(completed);
        assertThat(manifest.timingSource()).isEqualTo("TEST_ONLY_EXPLICIT_TIMES");
    }

    @Test
    void runManifestWriterRoundTripsInstantsInNonFormalMode(@TempDir Path outputRoot)
            throws Exception {
        Instant started = Instant.parse("2026-07-14T02:00:00Z");
        Instant completed = Instant.parse("2026-07-14T02:00:05Z");
        Instant snapshotCreatedAt = Instant.parse("2026-07-14T02:00:03Z");
        RunMetadata metadata = new RunMetadata(
                "TEST_ONLY_COMMIT", "TEST_ONLY_BRANCH", "TEST_ONLY_GRADLE", "TEST_ONLY");
        HarnessRunInput input = new HarnessRunInput(
                FROZEN_SAMPLE_IDS, FIXTURE_ROOT, outputRoot, false, metadata);
        List<SampleAcceptanceResult> samples = List.of(verdictSample(
                FROZEN_SAMPLE_IDS.get(0), CandidateComparison.MATCH, versions("writer-round-trip"),
                SnapshotStatus.SUCCESS, snapshotCreatedAt));
        RunManifest manifest = aggregateRunManifest(
                input, samples, started, completed, "TEST_ONLY_WRITER_ROUND_TRIP");

        writeFormalEvidence(outputRoot, samples, List.of(), manifest);

        Path manifestPath = outputRoot.resolve("run-manifest.json");
        RunManifest reloaded = objectMapper.readValue(manifestPath.toFile(), RunManifest.class);
        assertThat(reloaded.formalMode()).isFalse();
        assertThat(reloaded.startedAt()).isEqualTo(started);
        assertThat(reloaded.completedAt()).isEqualTo(completed);
        assertThat(reloaded.timingSource()).isEqualTo("TEST_ONLY_WRITER_ROUND_TRIP");
        assertThat(reloaded.samples()).singleElement()
                .extracting(SampleManifestEntry::sampleId, SampleManifestEntry::snapshotCreatedAt)
                .containsExactly(FROZEN_SAMPLE_IDS.get(0), snapshotCreatedAt);
    }

    @Test
    void formalAcceptanceEntryPointIsPropertyAndInputGated() throws Exception {
        boolean propertyEnabled = Boolean.getBoolean(FORMAL_PROPERTY);
        boolean inputOptIn = Boolean.getBoolean(FORMAL_INPUT_PROPERTY);
        RunMetadata metadata = new RunMetadata(
                System.getProperty("cqcp.task034.commit", ""),
                System.getProperty("cqcp.task034.branch", ""),
                System.getProperty("cqcp.task034.gradleVersion", ""),
                "FORMAL_SYSTEM_PROPERTIES");
        HarnessRunInput input = new HarnessRunInput(
                FROZEN_SAMPLE_IDS, FIXTURE_ROOT, FORMAL_OUTPUT_ROOT, inputOptIn, metadata);

        HarnessRunResult result = runAcceptance(input, propertyEnabled);

        if (!propertyEnabled || !inputOptIn) {
            assertThat(result.overallVerdict()).isEqualTo(OverallVerdict.SKIPPED);
            assertThat(result.samples()).isEmpty();
        } else {
            assertThat(result.samples()).hasSize(3);
            assertThat(result.occurrences()).hasSize(63);
            assertThat(result.overallVerdict())
                    .as("formal evidence is already written; FAIL must fail the Gradle acceptance run")
                    .isEqualTo(OverallVerdict.PASS);
        }
    }

    private HarnessRunResult runAcceptance(HarnessRunInput input, boolean propertyEnabled) throws Exception {
        if (!propertyEnabled || !input.formalMode()) {
            return new HarnessRunResult(List.of(), List.of(), 0, 0, OverallVerdict.SKIPPED, null);
        }
        validateFormalGate(input, true);
        Instant startedAt = Instant.now();

        var samples = new ArrayList<SampleAcceptanceResult>();
        var occurrences = new ArrayList<OccurrenceComparisonRow>();
        for (String sampleId : input.sampleIds()) {
            FormalFixture fixture = loadFormalFixture(sampleId, input.fixtureRoot());
            Map<ReviewPointCode, String> expected = expectedCandidatesFromHumanFixture(fixture.occurrences());
            ObservedSampleRun run = executeOne(fixture.input(), expected);
            samples.add(run.sampleResult());
            occurrences.addAll(compareOccurrences(
                    fixture.occurrences(),
                    run.observation().parsedDocument(),
                    actualAnchors(run.querySnapshot())));
        }
        Instant completedAt = Instant.now();

        int included = (int) occurrences.stream()
                .filter(OccurrenceComparisonRow::includedInConsistencyEvaluation).count();
        int excluded = occurrences.size() - included;
        if (occurrences.size() != 63 || included != 57 || excluded != 6) {
            throw new IllegalStateException("Frozen occurrence contract violated");
        }
        RunManifest manifest = aggregateRunManifest(
                input, samples, startedAt, completedAt, "Instant.now around formal harness run");
        writeFormalEvidence(
                input.outputRoot(), List.copyOf(samples), List.copyOf(occurrences), manifest);
        OverallVerdict verdict = determineOverallVerdict(samples, occurrences, included, excluded);
        HarnessRunResult result = new HarnessRunResult(
                List.copyOf(samples), List.copyOf(occurrences), included, excluded, verdict, manifest);
        writeFormalConsoleSummary(input.outputRoot(), result);
        return result;
    }

    private static void validateFormalGate(HarnessRunInput input, boolean propertyEnabled) {
        if (!propertyEnabled || !input.formalMode()) {
            throw new IllegalStateException("formal mode requires property and HarnessRunInput.formalMode");
        }
        if (!input.sampleIds().equals(FROZEN_SAMPLE_IDS)) {
            throw new IllegalStateException("formal mode requires fixed sample order 001 -> 002 -> 003");
        }
        RunMetadata metadata = input.metadata();
        if (metadata == null
                || isBlank(metadata.commit())
                || isBlank(metadata.branch())
                || isBlank(metadata.gradleVersion())
                || !"FORMAL_SYSTEM_PROPERTIES".equals(metadata.provenance())) {
            throw new IllegalStateException("formal metadata commit/branch/Gradle must be explicitly injected");
        }
    }

    private static RunManifest aggregateRunManifest(
            HarnessRunInput input,
            List<SampleAcceptanceResult> samples,
            Instant startedAt,
            Instant completedAt,
            String timingSource) {
        if (startedAt == null || completedAt == null || completedAt.isBefore(startedAt)
                || isBlank(timingSource)) {
            throw new IllegalStateException("run timing must surround the harness execution");
        }
        List<SampleManifestEntry> entries = samples.stream()
                .map(SampleAcceptanceResult::executionMetadata)
                .toList();
        return new RunManifest(
                SCHEMA_VERSION,
                input.sampleIds(),
                input.formalMode(),
                input.metadata().commit(),
                input.metadata().branch(),
                System.getProperty("java.version"),
                input.metadata().gradleVersion(),
                input.metadata().provenance(),
                startedAt,
                completedAt,
                timingSource,
                entries,
                "same-run ReviewEngineInput.pointEvidences",
                "same-store TaskResultQueryService snapshot",
                "same-run parsed document + queried snapshot SourceAnchorSummary",
                "accepted human-anchor fixture grouped uniquely by reviewPointCode");
    }

    private ObservedSampleRun executeOne(
            SampleInput input,
            Map<ReviewPointCode, String> expectedCandidates) {
        var parsedOutput = new AtomicReference<ParsedContractDocument>();
        var indexInput = new AtomicReference<ParsedContractDocument>();
        var indexOutput = new AtomicReference<ContractEvidenceIndex>();
        var planInput = new AtomicReference<ContractEvidenceIndex>();
        var planOutput = new AtomicReference<EvidenceBuildPlan>();
        var buildPlanInput = new AtomicReference<EvidenceBuildPlan>();
        var buildOutput = new AtomicReference<ReviewEngineInput>();
        var preparer = spy(new ParserBackedReviewInputPreparer(new DocxWordParserSpike()));

        doAnswer(invocation -> {
            ParsedContractDocument value = (ParsedContractDocument) invocation.callRealMethod();
            parsedOutput.set(value);
            return value;
        }).when(preparer).parse(any(TaskExecutionDocumentReference.class));
        doAnswer(invocation -> {
            indexInput.set(invocation.getArgument(0));
            ContractEvidenceIndex value = (ContractEvidenceIndex) invocation.callRealMethod();
            indexOutput.set(value);
            return value;
        }).when(preparer).index(any(ParsedContractDocument.class));
        doAnswer(invocation -> {
            planInput.set(invocation.getArgument(0));
            EvidenceBuildPlan value = (EvidenceBuildPlan) invocation.callRealMethod();
            planOutput.set(value);
            return value;
        }).when(preparer).plan(any(ContractEvidenceIndex.class));
        doAnswer(invocation -> {
            buildPlanInput.set(invocation.getArgument(1));
            ReviewEngineInput value = (ReviewEngineInput) invocation.callRealMethod();
            buildOutput.set(value);
            return value;
        }).when(preparer).build(any(TaskExecutionRequest.class), any(EvidenceBuildPlan.class));

        var engine = new CountingReviewEngine();
        var persistence = new RecordingPersistence();
        var request = TaskExecutionRequest.forDocument(
                new ReviewTaskRecord(input.taskId(), input.contractName(), input.structuredFields().asMap()),
                new TaskExecutionRecord(
                        input.executionId(), input.taskId(), ExecutionStatus.CREATED, "CREATED",
                        defaultVersionReferences(), "default-model-profile", "MOCK", "gemma-local",
                        "local-gemma", null, null),
                new TaskExecutionDocumentReference(input.docxPath(), input.sampleId()),
                enabledPoints(),
                List.of());
        var machine = new TaskExecutionStateMachine(engine, new ResultComposer(), preparer, FIXED_CLOCK);

        TaskExecutionRunResult runResult = machine.execute(request, persistence);
        ReviewResultSnapshot queried = new TaskResultQueryService(persistence.store).getResult(input.taskId());

        verify(preparer, times(1)).parse(any(TaskExecutionDocumentReference.class));
        verify(preparer, times(1)).index(any(ParsedContractDocument.class));
        verify(preparer, times(1)).plan(any(ContractEvidenceIndex.class));
        verify(preparer, times(1)).build(any(TaskExecutionRequest.class), any(EvidenceBuildPlan.class));
        assertThat(parsedOutput.get()).isSameAs(indexInput.get());
        assertThat(indexOutput.get()).isSameAs(planInput.get());
        assertThat(planOutput.get()).isSameAs(buildPlanInput.get());
        assertThat(indexOutput.get().parsedDocument()).isSameAs(parsedOutput.get());
        assertThat(planOutput.get().evidenceIndex()).isSameAs(indexOutput.get());
        assertThat(engine.input).isSameAs(buildOutput.get());
        assertThat(engine.invocationCount).isEqualTo(1);
        assertThat(queried).isSameAs(runResult.snapshot());
        assertThat(queried.taskId()).isEqualTo(input.taskId());
        assertThat(queried.executionId()).isEqualTo(input.executionId());
        assertThat(persistence.stageEvents()).containsExactlyElementsOf(EXPECTED_STAGE_EVENTS);

        SameRunObservation observation = new SameRunObservation(parsedOutput.get(), buildOutput.get());
        SampleAcceptanceResult sample = buildSampleResult(input, queried, buildOutput.get(), expectedCandidates);
        return new ObservedSampleRun(
                runResult, queried, observation, engine.invocationCount,
                persistence.stageEvents(), sample);
    }

    private SampleAcceptanceResult buildSampleResult(
            SampleInput input,
            ReviewResultSnapshot snapshot,
            ReviewEngineInput reviewInput,
            Map<ReviewPointCode, String> expectedCandidates) {
        var points = new ArrayList<PointAcceptanceResult>();
        for (PointReviewResult point : snapshot.pointResults()) {
            PointEvidence evidence = reviewInput.pointEvidences().get(point.reviewPointCode());
            String expected = expectedCandidates.get(point.reviewPointCode());
            String actual = evidence == null ? null : evidence.candidateValue();
            List<String> evidenceSummary = point.sourceAnchors().stream()
                    .map(SourceAnchorSummary::evidenceSummary)
                    .toList();
            List<PointDiagnostic> sysDiagnostics = snapshot.diagnostics().stream()
                    .filter(diagnostic -> point.reviewPointCode().name().equals(diagnostic.reviewPointCode()))
                    .filter(Task034MvpE2eAcceptanceHarnessTest::isSysDiagnostic)
                    .toList();
            points.add(new PointAcceptanceResult(
                    point.reviewPointCode(), expected, actual, compareCandidate(expected, actual),
                    point.pointStatus(), evidenceSummary, point.sourceAnchors(), sysDiagnostics));
        }
        SampleManifestEntry executionMetadata = new SampleManifestEntry(
                input.sampleId(), snapshot.taskId(), snapshot.executionId(),
                QUERY_PATH_TEMPLATE.formatted(snapshot.taskId()),
                snapshot.taskId() + ":" + snapshot.executionId() + ":" + snapshot.createdAt(),
                snapshot.status(), snapshot.createdAt(), snapshotVersions(snapshot));
        return new SampleAcceptanceResult(
                input.sampleId(), snapshot.taskId(), snapshot.executionId(),
                QUERY_PATH_TEMPLATE.formatted(snapshot.taskId()),
                snapshot.taskId() + ":" + snapshot.executionId() + ":" + snapshot.createdAt(),
                List.copyOf(points), snapshot.findings(), snapshot.diagnostics(), executionMetadata);
    }

    private static void assertQueriedFieldsAreUnmodified(
            SampleAcceptanceResult sample,
            ReviewResultSnapshot snapshot) {
        assertThat(sample.findings()).isEqualTo(snapshot.findings());
        assertThat(sample.queryDiagnostics()).isEqualTo(snapshot.diagnostics());
        for (PointAcceptanceResult output : sample.points()) {
            PointReviewResult queriedPoint = snapshot.pointResults().stream()
                    .filter(point -> point.reviewPointCode() == output.reviewPointCode())
                    .findFirst().orElseThrow();
            assertThat(output.pointStatus()).isEqualTo(queriedPoint.pointStatus());
            assertThat(output.actualAnchors()).isEqualTo(queriedPoint.sourceAnchors());
            assertThat(output.evidenceSummary()).containsExactlyElementsOf(
                    queriedPoint.sourceAnchors().stream().map(SourceAnchorSummary::evidenceSummary).toList());
        }
        Map<ReviewPointCode, PointStatus> statusByPoint = snapshot.pointResults().stream()
                .collect(Collectors.toMap(PointReviewResult::reviewPointCode, PointReviewResult::pointStatus));
        Set<ReviewPointCode> findingPoints = snapshot.findings().stream()
                .map(ReviewFinding::reviewPointCode)
                .collect(Collectors.toSet());
        Set<ReviewPointCode> businessRiskPoints = statusByPoint.entrySet().stream()
                .filter(entry -> entry.getValue() == PointStatus.WARNING || entry.getValue() == PointStatus.ERROR)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        assertThat(findingPoints).isEqualTo(businessRiskPoints);
        assertThat(snapshot.findings()).allSatisfy(finding ->
                assertThat(statusByPoint.get(finding.reviewPointCode()))
                        .isIn(PointStatus.WARNING, PointStatus.ERROR));

        List<PointDiagnostic> queriedSys = snapshot.diagnostics().stream()
                .filter(Task034MvpE2eAcceptanceHarnessTest::isSysDiagnostic)
                .toList();
        List<PointDiagnostic> pointScopedSys = sample.points().stream()
                .flatMap(point -> point.sysDiagnostics().stream())
                .toList();
        assertThat(pointScopedSys).containsExactlyInAnyOrderElementsOf(queriedSys);
        assertThat(queriedSys).allSatisfy(diagnostic -> {
            assertThat(diagnostic.reviewPointCode()).isNotBlank();
            ReviewPointCode code = ReviewPointCode.valueOf(diagnostic.reviewPointCode());
            assertThat(statusByPoint).containsKey(code);
            assertThat(findingPoints).doesNotContain(code);
        });
    }

    static List<OccurrenceComparisonRow> compareOccurrences(
            List<HumanOccurrence> humans,
            ParsedContractDocument parsed,
            List<ActualAnchor> actuals) {
        var resolved = new ArrayList<ResolvedActual>();
        for (int actualIndex = 0; actualIndex < actuals.size(); actualIndex++) {
            ResolvedElement element = resolveElement(parsed, actuals.get(actualIndex).anchor());
            if (element != null) {
                resolved.add(new ResolvedActual(actualIndex, actuals.get(actualIndex).reviewPointCode(), element));
            }
        }

        Map<Integer, Set<Integer>> projectionEdges = new HashMap<>();
        Map<Integer, Integer> projectionActualDegree = new HashMap<>();
        for (int humanIndex = 0; humanIndex < humans.size(); humanIndex++) {
            HumanOccurrence human = humans.get(humanIndex);
            if (!human.included()) {
                continue;
            }
            for (ResolvedActual actual : resolved) {
                if (samePointAndGranularity(human, actual)
                        && contextProjects(human, actual.element())) {
                    projectionEdges.computeIfAbsent(humanIndex, ignored -> new LinkedHashSet<>())
                            .add(actual.identity());
                    projectionActualDegree.merge(actual.identity(), 1, Integer::sum);
                }
            }
        }

        Map<Integer, ComparisonDecision> decisions = new HashMap<>();
        for (int humanIndex = 0; humanIndex < humans.size(); humanIndex++) {
            HumanOccurrence human = humans.get(humanIndex);
            if (!human.included()) {
                decisions.put(humanIndex, ComparisonDecision.excluded());
                continue;
            }
            Set<Integer> edges = projectionEdges.getOrDefault(humanIndex, Set.of());
            if (edges.size() == 1) {
                int actualId = edges.iterator().next();
                if (projectionActualDegree.getOrDefault(actualId, 0) == 1) {
                    ResolvedElement actual = byIdentity(resolved, actualId).element();
                    if (normalizeAnchorText(human.humanAnchorText())
                            .equals(normalizeAnchorText(actual.text()))) {
                        decisions.put(humanIndex, ComparisonDecision.matched(actual));
                    } else {
                        decisions.put(humanIndex, ComparisonDecision.notMatched(actual));
                    }
                } else {
                    decisions.put(humanIndex, ComparisonDecision.notObservable("全局投影图存在多对一歧义"));
                }
            } else if (edges.size() > 1) {
                decisions.put(humanIndex, ComparisonDecision.notObservable("全局投影图存在一对多或多对多歧义"));
            } else {
                decisions.put(humanIndex, ComparisonDecision.notObservable("human location/context 无法唯一投影到 actual element"));
            }
        }

        var rows = new ArrayList<OccurrenceComparisonRow>();
        for (int humanIndex = 0; humanIndex < humans.size(); humanIndex++) {
            rows.add(toRow(humans.get(humanIndex), decisions.get(humanIndex)));
        }
        return List.copyOf(rows);
    }

    private static boolean samePointAndGranularity(HumanOccurrence human, ResolvedActual actual) {
        return human.reviewPointCode() == actual.reviewPointCode()
                && human.humanAnchorGranularity().equals(actual.element().granularity());
    }

    private static boolean contextProjects(HumanOccurrence human, ResolvedElement element) {
        boolean hasApplicableSignal = isApplicableContext(human.humanLocationDescription())
                || isApplicableContext(human.tableContext())
                || isApplicableContext(human.rowContext())
                || isApplicableContext(human.cellContext());
        return hasApplicableSignal
                && matchesWhenApplicable(
                        human.humanLocationDescription(), element.reference(), element.blockText())
                && matchesWhenApplicable(human.tableContext(), element.tableId())
                && matchesWhenApplicable(human.rowContext(), element.rowText(), element.blockText())
                && matchesWhenApplicable(human.cellContext(), element.cellText());
    }

    private static boolean matchesWhenApplicable(String humanValue, String... parserValues) {
        if (!isApplicableContext(humanValue)) {
            return true;
        }
        String normalizedHuman = normalizeAnchorText(humanValue);
        for (String parserValue : parserValues) {
            if (parserValue != null && normalizedHuman.equals(normalizeAnchorText(parserValue))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isApplicableContext(String value) {
        return value != null && !value.isBlank() && !"不适用".equals(value.strip());
    }

    private static OccurrenceComparisonRow toRow(HumanOccurrence human, ComparisonDecision decision) {
        ResolvedElement actual = decision.actual();
        String actualText = actual == null ? null : actual.text();
        String actualGranularity = actual == null ? "UNAVAILABLE" : actual.granularity();
        String actualReference = actual == null ? null : actual.reference();
        return new OccurrenceComparisonRow(
                human.occurrenceNo(), human.included(), human.humanAnchorText(),
                human.humanLocationDescription(), human.humanAnchorGranularity(),
                actualText, actualGranularity, actualReference,
                decision.coverageResult(), decision.notes());
    }

    private static ResolvedActual byIdentity(List<ResolvedActual> actuals, int identity) {
        return actuals.stream().filter(actual -> actual.identity() == identity).findFirst().orElseThrow();
    }

    private static ResolvedElement resolveElement(
            ParsedContractDocument parsed,
            SourceAnchorSummary anchor) {
        if (anchor.previewElementRef() == null || anchor.previewElementRef().isBlank()) {
            return null;
        }
        List<WordParserSpikeDocument.DocumentBlock> blocks = parsed.document().blocks().stream()
                .filter(block -> Objects.equals(block.blockId(), anchor.blockId()))
                .toList();
        if (blocks.size() != 1) {
            return null;
        }
        WordParserSpikeDocument.DocumentBlock block = blocks.getFirst();
        Matcher blockMatcher = BLOCK_REF.matcher(anchor.previewElementRef());
        if (blockMatcher.matches()) {
            if (!"BLOCK_LEVEL".equals(anchor.locationLevel())
                    || !block.blockId().equals(blockMatcher.group(1))) {
                return null;
            }
            return new ResolvedElement(
                    block.text(), "BLOCK", "BLOCK:" + block.blockId(), block.text(),
                    null, null, null);
        }

        Matcher tableMatcher = TABLE_REF.matcher(anchor.previewElementRef());
        if (!tableMatcher.matches()
                || anchor.locationLevel() == null
                || !Objects.equals(block.tableId(), tableMatcher.group(1))
                || !Objects.equals(block.rowIndex(), Integer.valueOf(tableMatcher.group(2)))) {
            return null;
        }
        String rowText = String.join(" | ", block.tableCells().stream()
                .map(WordParserSpikeDocument.TableCellSpan::text).toList());
        if (tableMatcher.group(3) == null) {
            return new ResolvedElement(
                    block.text(), "TABLE_ROW", "TABLE_ROW:" + block.blockId() + ":" + block.rowIndex(),
                    block.text(), block.tableId(), rowText, null);
        }
        int cellIndex = Integer.parseInt(tableMatcher.group(3));
        List<WordParserSpikeDocument.TableCellSpan> cells = block.tableCells().stream()
                .filter(cell -> cell.cellIndex() == cellIndex)
                .toList();
        if (cells.size() != 1) {
            return null;
        }
        String cellText = cells.getFirst().text();
        return new ResolvedElement(
                cellText, "TABLE_CELL",
                "TABLE_CELL:" + block.blockId() + ":" + block.rowIndex() + ":" + cellIndex,
                block.text(), block.tableId(), rowText, cellText);
    }

    private List<HumanOccurrence> loadHumanOccurrences(String sampleId, Path fixtureRoot) throws IOException {
        JsonNode root = objectMapper.readTree(Files.readString(
                fixtureRoot.resolve("human-anchors").resolve(sampleId + ".json")));
        var result = new ArrayList<HumanOccurrence>();
        for (JsonNode node : root.withArray("occurrences")) {
            result.add(new HumanOccurrence(
                    sampleId,
                    node.path("occurrenceNo").asText(),
                    ReviewPointCode.valueOf(node.path("reviewPointCode").asText()),
                    node.path("includedInConsistencyEvaluation").asBoolean(),
                    textOrNull(node.get("expectedCandidateValue")),
                    node.path("humanAnchorText").asText(),
                    node.path("humanLocationDescription").asText(),
                    node.path("anchorGranularity").asText(),
                    node.path("tableContext").asText(),
                    node.path("rowContext").asText(),
                    node.path("cellContext").asText(),
                    textOrNull(node.get("exclusionReason"))));
        }
        int included = (int) result.stream().filter(HumanOccurrence::included).count();
        if (result.size() != FROZEN_TOTALS.getOrDefault(sampleId, -1)
                || included != FROZEN_INCLUDED.getOrDefault(sampleId, -1)) {
            throw new IllegalStateException("Human fixture contract changed for " + sampleId);
        }
        return List.copyOf(result);
    }

    private static Map<ReviewPointCode, String> expectedCandidatesFromHumanFixture(
            List<HumanOccurrence> occurrences) {
        var values = new EnumMap<ReviewPointCode, Set<String>>(ReviewPointCode.class);
        for (HumanOccurrence occurrence : occurrences) {
            if (occurrence.included()) {
                values.computeIfAbsent(occurrence.reviewPointCode(), ignored -> new LinkedHashSet<>())
                        .add(occurrence.expectedCandidateValue());
            }
        }
        var result = new EnumMap<ReviewPointCode, String>(ReviewPointCode.class);
        values.forEach((code, candidates) -> {
            if (candidates.size() != 1) {
                throw new IllegalStateException("Human expected candidate is not unique for " + code);
            }
            result.put(code, candidates.iterator().next());
        });
        return Map.copyOf(result);
    }

    private FormalFixture loadFormalFixture(String sampleId, Path fixtureRoot) throws IOException {
        JsonNode expected = objectMapper.readTree(Files.readString(
                fixtureRoot.resolve("expected").resolve(sampleId + ".json")));
        StructuredFieldSet fields = readStructuredFields(expected.at("/goldenExpected/structuredFields"));
        Path docx = fixtureRoot.resolve(expected.path("sourceDocx").asText()).normalize();
        SampleInput input = new SampleInput(
                sampleId, "task-034-" + sampleId, "execution-034-" + sampleId,
                expected.at("/goldenExpected/structuredFields/contractName").asText(), docx, fields);
        return new FormalFixture(input, loadHumanOccurrences(sampleId, fixtureRoot));
    }

    private StructuredFieldSet readStructuredFields(JsonNode node) {
        var builder = StructuredFieldSet.builder();
        node.fields().forEachRemaining(entry -> builder.put(entry.getKey(), entry.getValue().asText()));
        return builder.build();
    }

    private void writeFormalEvidence(
            Path outputRoot,
            List<SampleAcceptanceResult> samples,
            List<OccurrenceComparisonRow> occurrences,
            RunManifest manifest) throws IOException {
        Files.createDirectories(outputRoot.resolve("sample-results"));
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(outputRoot.resolve("run-manifest.json").toFile(), manifest);
        for (SampleAcceptanceResult sample : samples) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    outputRoot.resolve("sample-results").resolve(sample.sampleId() + ".json").toFile(), sample);
        }
        var csv = new StringBuilder();
        csv.append("occurrenceNo,includedInConsistencyEvaluation,humanAnchorText,humanLocationDescription,")
                .append("humanAnchorGranularity,actualEvidenceText,actualAnchorGranularity,")
                .append("actualAnchorReference,coverageResult,notes\n");
        for (OccurrenceComparisonRow row : occurrences) {
            csv.append(csv(row.occurrenceNo())).append(',')
                    .append(row.includedInConsistencyEvaluation()).append(',')
                    .append(csv(row.humanAnchorText())).append(',')
                    .append(csv(row.humanLocationDescription())).append(',')
                    .append(csv(row.humanAnchorGranularity())).append(',')
                    .append(csv(row.actualEvidenceText())).append(',')
                    .append(csv(row.actualAnchorGranularity())).append(',')
                    .append(csv(row.actualAnchorReference())).append(',')
                    .append(row.coverageResult()).append(',')
                    .append(csv(row.notes())).append('\n');
        }
        Files.writeString(outputRoot.resolve("occurrence-comparison.csv"), csv, StandardCharsets.UTF_8);
    }

    private static void writeFormalConsoleSummary(Path outputRoot, HarnessRunResult result) throws IOException {
        String summary = "# TASK-034 MVP E2E console summary\n\n"
                + "- overallVerdict: " + result.overallVerdict() + "\n"
                + "- samples: " + result.samples().size() + "\n"
                + "- occurrences: " + result.occurrences().size() + "\n"
                + "- included: " + result.includedCount() + "\n"
                + "- excluded: " + result.excludedCount() + "\n";
        Files.writeString(outputRoot.resolve("console-summary.md"), summary, StandardCharsets.UTF_8);
    }

    private static OverallVerdict determineOverallVerdict(
            List<SampleAcceptanceResult> samples,
            List<OccurrenceComparisonRow> rows,
            int included,
            int excluded) {
        boolean identityContract = samples.size() == 3
                && samples.stream().map(SampleAcceptanceResult::sampleId).toList().equals(FROZEN_SAMPLE_IDS);
        boolean pointContract = samples.stream().allMatch(Task034MvpE2eAcceptanceHarnessTest::samplePasses);
        boolean occurrenceContract = rows.size() == 63 && included == 57 && excluded == 6
                && rows.stream().allMatch(row -> row.includedInConsistencyEvaluation()
                        ? row.coverageResult() == CoverageResult.MATCHED
                        : row.coverageResult() == CoverageResult.EXCLUDED);
        return identityContract && pointContract && occurrenceContract
                ? OverallVerdict.PASS : OverallVerdict.FAIL;
    }

    private static boolean samplePasses(SampleAcceptanceResult sample) {
        return sample.points().stream().allMatch(point ->
                        point.candidateComparison() == CandidateComparison.MATCH
                                && point.pointStatus() == PointStatus.PASS
                                && !point.actualAnchors().isEmpty()
                                && point.sysDiagnostics().isEmpty())
                && sample.findings().isEmpty()
                && sample.queryDiagnostics().stream().noneMatch(Task034MvpE2eAcceptanceHarnessTest::isSysDiagnostic)
                && !isBlank(sample.queryPath())
                && !isBlank(sample.snapshotIdentity());
    }

    private static boolean isSysDiagnostic(PointDiagnostic diagnostic) {
        String code = diagnostic.diagnosticCode();
        return code != null && (code.startsWith("SYS-") || code.startsWith("SYS_"));
    }

    private static CandidateComparison compareCandidate(String expected, String actual) {
        if (actual == null) {
            return CandidateComparison.NOT_OBSERVABLE;
        }
        if (expected == null) {
            return CandidateComparison.MISMATCH;
        }
        return expected.strip().equals(actual.strip())
                ? CandidateComparison.MATCH : CandidateComparison.MISMATCH;
    }

    private static String normalizeAnchorText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u00a0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("(?U)\\s", " ")
                .replaceAll(" +", " ")
                .strip();
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static List<ActualAnchor> actualAnchors(ReviewResultSnapshot snapshot) {
        return snapshot.pointResults().stream()
                .flatMap(point -> point.sourceAnchors().stream()
                        .map(anchor -> new ActualAnchor(point.reviewPointCode(), anchor)))
                .toList();
    }

    private static Map<String, CoverageResult> coverageByOccurrence(List<OccurrenceComparisonRow> rows) {
        return rows.stream().collect(Collectors.toMap(
                OccurrenceComparisonRow::occurrenceNo,
                OccurrenceComparisonRow::coverageResult));
    }

    private static void assertUnavailable(OccurrenceComparisonRow row) {
        assertThat(row.actualEvidenceText()).isNull();
        assertThat(row.actualAnchorGranularity()).isEqualTo("UNAVAILABLE");
        assertThat(row.actualAnchorReference()).isNull();
    }

    private static ActualAnchor actualCell(String blockId, String tableId, int row, int cell) {
        return new ActualAnchor(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                new SourceAnchorSummary(
                        blockId, "NATIVE_WORD", "STRUCTURED", "NORMAL", "证据",
                        List.of(), "BODY", "HIGH", "BLOCK_LEVEL",
                        "table:" + tableId + "/row:" + row + "/cell:" + cell));
    }

    private static HumanOccurrence human(
            String occurrenceNo, boolean included, String humanText, String explicitCellContext) {
        return new HumanOccurrence(
                "SYNTHETIC", occurrenceNo, ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                included, "奔腾公司", humanText, "", "TABLE_CELL",
                "", "", explicitCellContext, included ? null : "TEST_ONLY exclusion");
    }

    private static SampleInput syntheticInput(Path docx) {
        var fields = StructuredFieldSet.builder()
                .put("partyAName", "奔腾公司")
                .put("partyBName", "前水公司")
                .put("contractTotalAmount", "1130")
                .put("taxExcludedAmount", "1000")
                .put("taxAmount", "130")
                .put("taxRate", "13")
                .put("paymentMethod", "MONTHLY")
                .put("prepaymentRatio", "20")
                .put("progressPaymentRatio", "70")
                .put("completionPaymentRatio", "80")
                .put("settlementPaymentRatio", "97")
                .put("warrantyRetentionRatio", "3")
                .build();
        return new SampleInput(
                "TASK-034-SYNTHETIC", "task-034-synthetic", "execution-034-synthetic",
                "TASK-034 synthetic contract", docx, fields);
    }

    private static Map<ReviewPointCode, String> testOnlyExpectedCandidates() {
        var expected = new EnumMap<ReviewPointCode, String>(ReviewPointCode.class);
        expected.put(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "奔腾公司");
        expected.put(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "前水公司");
        expected.put(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, "1130");
        expected.put(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, "130");
        expected.put(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, "20");
        expected.put(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, "70");
        expected.put(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, "80");
        expected.put(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, "97");
        expected.put(ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, "3");
        return Map.copyOf(expected);
    }

    private static Path writeSyntheticDocx(Path path) throws IOException {
        try (var document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("建设工程合同");
            document.createParagraph().createRun().setText("发包方（甲方）：奔腾公司");
            document.createParagraph().createRun().setText("承包方（乙方）：前水公司");
            document.createParagraph().createRun().setText("合同固定总价：1130元");
            document.createParagraph().createRun().setText("不含税金额1000元，税额130元，税率13%");
            document.createParagraph().createRun().setText("预付款比例20%");
            document.createParagraph().createRun().setText("月度付款：进度款支付至70%");
            document.createParagraph().createRun().setText("竣工款支付至80%");
            document.createParagraph().createRun().setText("结算款支付至97%");
            document.createParagraph().createRun().setText("质保金为3%");
            try (OutputStream output = Files.newOutputStream(path)) {
                document.write(output);
            }
        }
        return path;
    }

    private static List<ReviewPointSnapshot> enabledPoints() {
        var result = new ArrayList<ReviewPointSnapshot>();
        ReviewPointCode[] codes = ReviewPointCode.values();
        for (int index = 0; index < codes.length; index++) {
            ReviewPointCode code = codes[index];
            result.add(new ReviewPointSnapshot(
                    code, "P%03d".formatted(index + 1), code.name(), familyOf(code),
                    "ENGINEERING_PROCUREMENT",
                    code == ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY
                            ? FindingSeverity.WARNING : FindingSeverity.ERROR,
                    index + 1));
        }
        return List.copyOf(result);
    }

    private static String familyOf(ReviewPointCode code) {
        return switch (code) {
            case PARTY_A_NAME_CONSISTENCY, PARTY_B_NAME_CONSISTENCY -> "PARTY_FIELDS";
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY, TAX_AMOUNT_FORMULA_CONSISTENCY -> "AMOUNT_TAX";
            default -> "PAYMENT_TERMS";
        };
    }

    private static VersionReferences defaultVersionReferences() {
        return new VersionReferences(
                "contract-type-v1", "ruleset-v1", "budget-v1", "model-v1", "parser-v1",
                "prompt-v1", "schema-v1", "pattern-v1", "lexicon-v1", "selector-v1");
    }

    private static VersionReferences snapshotVersions(ReviewResultSnapshot snapshot) {
        return new VersionReferences(
                snapshot.contractTypeProfileVersion(), snapshot.ruleSetVersion(),
                snapshot.reviewBudgetProfileVersion(), snapshot.modelProfileVersion(),
                snapshot.parserVersion(), snapshot.promptVersion(), snapshot.schemaVersion(),
                snapshot.patternLibraryVersion(), snapshot.fieldLexiconVersion(),
                snapshot.evidenceSelectorVersion());
    }

    private static ParsedContractDocument parsedDocument(List<WordParserSpikeDocument.DocumentBlock> blocks) {
        var quality = new WordParserSpikeDocument.ParseQualityReport(
                "DOCX", "TEST_ONLY", "zh-CN", 0, blocks.size(), 0, 0, 0, 0,
                false, WordParserSpikeDocument.ParseStatus.GOOD, "HIGH", 0, 0, 0, List.of());
        var document = new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("synthetic", "synthetic.docx"),
                blocks, List.of(), List.of(), quality);
        return new ParsedContractDocument(
                new TaskExecutionDocumentReference(Path.of("synthetic.docx"), "SYNTHETIC"), document);
    }

    private static WordParserSpikeDocument.DocumentBlock tableBlock(
            String blockId, String tableId, int rowIndex, List<String> cells) {
        String text = String.join(" | ", cells);
        var spans = new ArrayList<WordParserSpikeDocument.TableCellSpan>();
        int offset = 0;
        for (int index = 0; index < cells.size(); index++) {
            String cell = cells.get(index);
            spans.add(new WordParserSpikeDocument.TableCellSpan(index, cell, offset, offset + cell.length()));
            offset += cell.length() + 3;
        }
        return new WordParserSpikeDocument.DocumentBlock(
                blockId, WordParserSpikeDocument.BlockType.TABLE_ROW, text, text,
                List.of(), WordParserSpikeDocument.RegionType.BODY,
                WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "synthetic", tableId, rowIndex, spans,
                WordParserSpikeDocument.ConfidenceLevel.HIGH,
                WordParserSpikeDocument.PreviewAnchorLevel.TABLE_CELL);
    }

    private static List<OccurrenceComparisonRow> perfectCountRows() {
        var rows = new ArrayList<OccurrenceComparisonRow>();
        for (int index = 0; index < 63; index++) {
            boolean included = index < 57;
            rows.add(new OccurrenceComparisonRow(
                    "V-" + index, included, "human", "location", "BLOCK",
                    included ? "actual" : null,
                    included ? "BLOCK" : "UNAVAILABLE",
                    included ? "BLOCK:block" + index : null,
                    included ? CoverageResult.MATCHED : CoverageResult.EXCLUDED,
                    "TEST_ONLY verdict fixture"));
        }
        return List.copyOf(rows);
    }

    private static SampleAcceptanceResult verdictSample(
            String sampleId, CandidateComparison candidateComparison) {
        return verdictSample(
                sampleId, candidateComparison, defaultVersionReferences(), SnapshotStatus.SUCCESS,
                Instant.parse("2026-07-14T00:00:00Z"));
    }

    private static SampleAcceptanceResult verdictSample(
            String sampleId,
            CandidateComparison candidateComparison,
            VersionReferences versions,
            SnapshotStatus snapshotStatus,
            Instant snapshotCreatedAt) {
        var anchor = new SourceAnchorSummary(
                "block", "NATIVE_WORD", "STRUCTURED", "NORMAL", "summary",
                List.of(), "BODY", "HIGH", "BLOCK_LEVEL", "block:block");
        var point = new PointAcceptanceResult(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "expected", "actual",
                candidateComparison, PointStatus.PASS, List.of("summary"), List.of(anchor), List.of());
        return new SampleAcceptanceResult(
                sampleId, "task", "execution", "/api/v1/tasks/task/result", "snapshot",
                List.of(point), List.of(), List.of(),
                new SampleManifestEntry(
                        sampleId, "task", "execution", "/api/v1/tasks/task/result", "snapshot",
                        snapshotStatus, snapshotCreatedAt, versions));
    }

    private static VersionReferences versions(String schemaVersion) {
        return new VersionReferences(
                "contract-" + schemaVersion, "rule-" + schemaVersion, "budget-" + schemaVersion,
                "model-" + schemaVersion, "parser-" + schemaVersion, "prompt-" + schemaVersion,
                schemaVersion, "pattern-" + schemaVersion, "lexicon-" + schemaVersion,
                "selector-" + schemaVersion);
    }

    private static final class CountingReviewEngine extends MinimalReviewEngine {
        private int invocationCount;
        private ReviewEngineInput input;

        @Override
        public ReviewEngineResult review(ReviewEngineInput reviewInput) {
            invocationCount++;
            input = reviewInput;
            return super.review(reviewInput);
        }
    }

    private static final class RecordingPersistence implements TaskExecutionPersistence {
        private final InMemoryTaskResultStore store = new InMemoryTaskResultStore();
        private final List<TaskStageLogEntry> stageLogs = new ArrayList<>();

        @Override
        public void saveExecution(TaskExecutionRecord execution) {
            store.saveExecution(execution);
        }

        @Override
        public void appendStageLog(TaskStageLogEntry entry) {
            stageLogs.add(entry);
            store.appendStageLog(entry);
        }

        @Override
        public void saveSnapshot(ReviewResultSnapshot snapshot) {
            store.saveSnapshot(snapshot);
        }

        List<String> stageEvents() {
            return stageLogs.stream()
                    .map(log -> log.stageName() + ":" + log.eventType())
                    .toList();
        }
    }

    record HarnessRunInput(
            List<String> sampleIds,
            Path fixtureRoot,
            Path outputRoot,
            boolean formalMode,
            RunMetadata metadata) {
        HarnessRunInput {
            sampleIds = List.copyOf(sampleIds);
        }
    }

    record RunMetadata(String commit, String branch, String gradleVersion, String provenance) {
    }

    private record SampleInput(
            String sampleId,
            String taskId,
            String executionId,
            String contractName,
            Path docxPath,
            StructuredFieldSet structuredFields) {
    }

    private record FormalFixture(SampleInput input, List<HumanOccurrence> occurrences) {
    }

    private record SameRunObservation(
            ParsedContractDocument parsedDocument,
            ReviewEngineInput reviewInput) {
    }

    private record ObservedSampleRun(
            TaskExecutionRunResult result,
            ReviewResultSnapshot querySnapshot,
            SameRunObservation observation,
            int reviewInvocationCount,
            List<String> stageEvents,
            SampleAcceptanceResult sampleResult) {
    }

    record HarnessRunResult(
            List<SampleAcceptanceResult> samples,
            List<OccurrenceComparisonRow> occurrences,
            int includedCount,
            int excludedCount,
            OverallVerdict overallVerdict,
            RunManifest manifest) {
    }

    record SampleAcceptanceResult(
            String sampleId,
            String taskId,
            String executionId,
            String queryPath,
            String snapshotIdentity,
            List<PointAcceptanceResult> points,
            List<ReviewFinding> findings,
            List<PointDiagnostic> queryDiagnostics,
            SampleManifestEntry executionMetadata) {
    }

    record SampleManifestEntry(
            String sampleId,
            String taskId,
            String executionId,
            String queryPath,
            String snapshotIdentity,
            SnapshotStatus snapshotStatus,
            Instant snapshotCreatedAt,
            VersionReferences versionReferences) {
    }

    record PointAcceptanceResult(
            ReviewPointCode reviewPointCode,
            String expectedCandidateValue,
            String actualCandidateValue,
            CandidateComparison candidateComparison,
            PointStatus pointStatus,
            List<String> evidenceSummary,
            List<SourceAnchorSummary> actualAnchors,
            List<PointDiagnostic> sysDiagnostics) {
    }

    record HumanOccurrence(
            String sampleId,
            String occurrenceNo,
            ReviewPointCode reviewPointCode,
            boolean included,
            String expectedCandidateValue,
            String humanAnchorText,
            String humanLocationDescription,
            String humanAnchorGranularity,
            String tableContext,
            String rowContext,
            String cellContext,
            String exclusionReason) {
    }

    record OccurrenceComparisonRow(
            String occurrenceNo,
            boolean includedInConsistencyEvaluation,
            String humanAnchorText,
            String humanLocationDescription,
            String humanAnchorGranularity,
            String actualEvidenceText,
            String actualAnchorGranularity,
            String actualAnchorReference,
            CoverageResult coverageResult,
            String notes) {
    }

    private record ActualAnchor(ReviewPointCode reviewPointCode, SourceAnchorSummary anchor) {
    }

    private record ResolvedActual(int identity, ReviewPointCode reviewPointCode, ResolvedElement element) {
    }

    private record ResolvedElement(
            String text,
            String granularity,
            String reference,
            String blockText,
            String tableId,
            String rowText,
            String cellText) {
    }

    private record ComparisonDecision(
            CoverageResult coverageResult,
            ResolvedElement actual,
            String notes) {
        static ComparisonDecision matched(ResolvedElement actual) {
            return new ComparisonDecision(CoverageResult.MATCHED, actual, "互相唯一严格边命中");
        }

        static ComparisonDecision notMatched(ResolvedElement actual) {
            return new ComparisonDecision(
                    CoverageResult.NOT_MATCHED, actual, "human location/context 唯一投影，但文本不一致");
        }

        static ComparisonDecision notObservable(String notes) {
            return new ComparisonDecision(CoverageResult.NOT_OBSERVABLE, null, notes);
        }

        static ComparisonDecision excluded() {
            return new ComparisonDecision(
                    CoverageResult.EXCLUDED, null, "人工冻结排除项；不读取 actual、不影响 PointStatus");
        }
    }

    record RunManifest(
            String schemaVersion,
            List<String> sampleOrder,
            boolean formalMode,
            String commit,
            String branch,
            String javaVersion,
            String gradleVersion,
            String metadataProvenance,
            Instant startedAt,
            Instant completedAt,
            String timingSource,
            List<SampleManifestEntry> samples,
            String candidateSource,
            String statusAndDiagnosticsSource,
            String anchorSource,
            String groundTruthSource) {
        RunManifest {
            sampleOrder = List.copyOf(sampleOrder);
            samples = List.copyOf(samples);
        }
    }

    enum CandidateComparison {
        MATCH,
        MISMATCH,
        NOT_OBSERVABLE
    }

    enum CoverageResult {
        MATCHED,
        NOT_MATCHED,
        NOT_OBSERVABLE,
        EXCLUDED
    }

    enum OverallVerdict {
        PASS,
        FAIL,
        SKIPPED
    }
}
