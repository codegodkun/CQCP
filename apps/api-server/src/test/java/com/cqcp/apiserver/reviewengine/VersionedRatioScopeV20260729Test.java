package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollector.LedgerStatus;
import com.cqcp.apiserver.wordparser.DocxWordParserSpike;
import com.cqcp.apiserver.wordparser.WordParserSpikeDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * TASK_SPEC-036-D1：v20260729.1 的精确 30 invocation 回归矩阵。
 *
 * <p>该类只消费生产 loader/classifier/probe/collector 与冻结证据，不复制生产正则。
 */
class VersionedRatioScopeV20260729Test {

    private static final String V15 = "v20260715.1";
    private static final String V29 = "v20260729.1";
    private static final Path PROJECT_ROOT = Path.of("..", "..").normalize();
    private static final Path V2_OUTPUT =
            resourcePath("/task-036-d1-evidence/task-034-mvp-e2e-acceptance-v2");
    private static final Path V3_OUTPUT =
            resourcePath("/task-036-d1-evidence/task-034-mvp-e2e-acceptance-v3");
    private static final List<ReviewPointCode> RATIO_POINTS = List.of(
            ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY,
            ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
            ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY,
            ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
            ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ParserBackedReviewInputPreparer preparer =
            new ParserBackedReviewInputPreparer(new DocxWordParserSpike());

    @TestFactory
    Stream<DynamicTest> exactThirtyInvocationGate() {
        List<NamedCheck> checks = List.of(
                check("01-loader-v29-identity", this::loaderV29Identity),
                check("02-loader-v15-v29-interleave", this::loaderInterleave),
                check("03-v29-ratio-policy-semantic-scope", this::ratioPolicyScope),
                check("04-v29-gate-not-ready-fail-closed", this::gateNotReady),
                check("05-v29-gate-ready", this::gateReady),
                check("06-runtime-release-accepts-both-versions", this::releaseAcceptsBoth),
                check("07-rollback-disabled-and-legacy-binding-unchanged", this::rollbackAndLegacy),
                check("08-v15-asset-hashes-unchanged", this::v15AssetHashes),
                check("09-v2-output-hashes-unchanged", this::v2OutputHashes),
                check("10-v2-three-docx-point-and-anchor-totals", this::v2PointAndAnchorTotals),
                check("11-v2-bridge-47-10-6", this::v2BridgeTotals),
                check("12-v15-synthetic-70-75-regression", this::v15SyntheticRatios),
                check("13-product-marker-valid-range", this::productMarkerValid),
                check("14-product-marker-missing-monthly", this::productMarkerMissingMonthly),
                check("15-product-marker-reversed", this::productMarkerReversed),
                check("16-product-marker-conflict", this::productMarkerConflict),
                check("17-engineering-marker-valid-range", this::engineeringMarkerValid),
                check("18-engineering-marker-missing-completion", this::engineeringMissingCompletion),
                check("19-engineering-marker-conflict", this::engineeringConflict),
                check("20-both-marker-families-fail-closed", this::bothFamiliesFailClosed),
                check("21-no-marker-family-is-certain-empty", this::noMarkerFamily),
                check("22-prepayment-grammar-positive-negative", this::prepaymentGrammar),
                check("23-engineering-progress-grammar-positive-negative", this::engineeringProgressGrammar),
                check("24-product-progress-same-block-grammar", this::productProgressSameBlock),
                check("25-product-progress-adjacent-block-grammar", this::productProgressAdjacent),
                check("26-completion-grammar-positive-negative", this::completionGrammar),
                check("27-settlement-grammar-positive-negative", this::settlementGrammar),
                check("28-warranty-grammar-positive-negative", this::warrantyGrammar),
                check("29-multi-percent-dataflow-and-uncertain-sys", this::multiPercentAndUncertainSys),
                check("30-v3-oracle-and-production-ledger-separation", this::v3OracleAndLedger));
        assertThat(checks).hasSize(30);
        return checks.stream().map(check ->
                DynamicTest.dynamicTest(check.name(), check.action()::run));
    }

    private void loaderV29Identity() {
        RuntimeRuleSetSnapshot snapshot = new RuntimeRuleSetLoader().load(V29);
        assertThat(snapshot.version()).isEqualTo(V29);
        assertThat(snapshot.assetId())
                .isEqualTo("cqcp.ruleset.mvp.consistency-set.v20260729.1");
        assertThat(snapshot.reviewPointDefinitionsAssetId())
                .isEqualTo("cqcp.review-points.mvp.consistency-set.v20260729.1");
        assertThat(snapshot.policyMap()).containsOnlyKeys(ReviewPointCode.values());
    }

    private void loaderInterleave() {
        var loader = new RuntimeRuleSetLoader();
        RuntimeRuleSetSnapshot before = loader.load(V15);
        RuntimeRuleSetSnapshot v29 = loader.load(V29);
        RuntimeRuleSetSnapshot after = loader.load(V15);
        assertThat(before).isEqualTo(after);
        assertThat(v29.version()).isEqualTo(V29);
        assertThat(before.version()).isEqualTo(V15);
    }

    private void ratioPolicyScope() {
        RuntimeRuleSetSnapshot snapshot = new RuntimeRuleSetLoader().load(V29);
        for (ReviewPointCode code : RATIO_POINTS) {
            ConsistencyPolicySnapshot policy = snapshot.policyMap().get(code);
            assertThat(policy.scopeVersion()).isEqualTo("consistency-scope-v20260729.1");
            assertThat(policy.strongExcludedSemanticContexts())
                    .containsExactly(InactivePaymentBranchClassifierV20260729.CLASSIFIER_ID);
        }
    }

    private void gateNotReady() {
        var result = new RuleSetActivationGate().request(V29, false);
        assertThat(result.status()).isEqualTo(RuleSetActivationGate.POLICY_NOT_READY);
        assertThat(result.snapshot()).isNull();
    }

    private void gateReady() {
        var result = new RuleSetActivationGate().request(V29, true);
        assertThat(result.status()).isEqualTo(RuleSetActivationGate.READY);
        assertThat(result.snapshot()).isNotNull();
        assertThat(result.snapshot().version()).isEqualTo(V29);
    }

    private void releaseAcceptsBoth() {
        ConsistencyRuntimeRelease release = ConsistencyRuntimeRelease.accepted();
        assertThat(release.readyFor(V15)).isTrue();
        assertThat(release.readyFor(V29)).isTrue();
    }

    private void rollbackAndLegacy() {
        ConsistencyRuntimeRelease disabled = ConsistencyRuntimeRelease.disabledForTest();
        assertThat(disabled.readyFor(V15)).isFalse();
        assertThat(disabled.readyFor(V29)).isFalse();
        var legacy = new RuleSetActivationGate().request("v20260705.1", false);
        assertThat(legacy.status()).isEqualTo(RuleSetActivationGate.LEGACY_ALLOWED);
        assertThat(legacy.snapshot()).isNull();
    }

    private void v15AssetHashes() throws Exception {
        assertThat(sha256(PROJECT_ROOT.resolve(
                "packages/review-assets/review-point-definitions/review-points-v20260715.1.json")))
                .isEqualTo("65bc666993d75afe8aaa9817633916a3614546970d1ca74453e43f2c529f7a98");
        assertThat(sha256(PROJECT_ROOT.resolve(
                "packages/review-assets/rule-sets/ruleset-v20260715.1.json")))
                .isEqualTo("22a86d933a6dfcee20e37979be1268fcc29d063687f0f287ce8a07cfda80bd14");
    }

    private void v2OutputHashes() throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("console-summary.md",
                "f6d7f7307c0599e1cc7207a5bb04b7b19ff164bf5e5a0eebfabc1e2aa0e8c099");
        expected.put("occurrence-comparison.csv",
                "4bf012bc8b6d0071584bab0d8d3cc916cfa7f93681cb4314d8280373648cdb8d");
        expected.put("run-manifest.json",
                "7635cf2158a8fcee371f5a5d082700ca85817b00f87ecb481b6adc78b8dd7cff");
        expected.put("sample-results/CQCP-MVP-DOCX-001.json",
                "db17e982e89553f17da807175b854746224c1186ceedcea2f139ce46abe1c734");
        expected.put("sample-results/CQCP-MVP-DOCX-002.json",
                "a8f52a16b26830d03287353b465260d432a4058c8641e67832546d29ac636be9");
        expected.put("sample-results/CQCP-MVP-DOCX-003.json",
                "646efeef88495d3cfccd556d90079126e6a1630946ce2ab3467ee516cd283903");
        expected.forEach((relative, hash) -> {
            try {
                assertThat(sha256(V2_OUTPUT.resolve(relative))).as(relative).isEqualTo(hash);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        });
    }

    private void v2PointAndAnchorTotals() throws Exception {
        int match = 0;
        int notObservable = 0;
        int anchors = 0;
        for (String sampleId : List.of(
                "CQCP-MVP-DOCX-001", "CQCP-MVP-DOCX-002", "CQCP-MVP-DOCX-003")) {
            JsonNode root = objectMapper.readTree(
                    V2_OUTPUT.resolve("sample-results/" + sampleId + ".json").toFile());
            assertThat(root.withArray("points").size()).isEqualTo(9);
            for (JsonNode point : root.withArray("points")) {
                if ("MATCH".equals(point.path("candidateComparison").asText())) match++;
                if ("NOT_OBSERVABLE".equals(point.path("candidateComparison").asText())) {
                    notObservable++;
                }
                anchors += point.withArray("actualAnchors").size();
                assertThat(point.path("reviewPointCode").asText()).isNotBlank();
            }
            assertThat(root.path("executionMetadata").path("versionReferences")
                    .path("ruleSetVersion").asText()).isEqualTo(V15);
        }
        assertThat(match).isEqualTo(18);
        assertThat(notObservable).isEqualTo(9);
        assertThat(anchors).isEqualTo(49);
    }

    private void v2BridgeTotals() throws Exception {
        String csv = Files.readString(
                V2_OUTPUT.resolve("occurrence-comparison.csv"), StandardCharsets.UTF_8);
        assertThat(count(csv, ",MATCHED,")).isEqualTo(47);
        assertThat(count(csv, ",NOT_OBSERVABLE,")).isEqualTo(10);
        assertThat(count(csv, ",EXCLUDED,")).isEqualTo(6);
    }

    private void v15SyntheticRatios() {
        var blocks = List.of(
                block("v15-70",
                        "A模式：按月形象进度付款，支付上月完成合格形象进度产值的70%"),
                block("v15-75",
                        "A模式：按月形象进度付款，支付上月完成合格形象进度产值的75%"));
        List<EvidenceCandidate> candidates = preparer.probeAllForPoint(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PROGRESS_PAYMENT_RATIO",
                blocks,
                ParserBackedReviewInputPreparer.ProbeExecutionMode.CONSISTENCY_FULL_SCAN,
                null,
                blocks);
        assertThat(values(candidates)).containsExactlyInAnyOrder("70", "75");
    }

    private void productMarkerValid() {
        var resolution = classify(
                block("p0", "□按节点付款"),
                block("p1", "到货节点付款条款"),
                block("p2", "结算节点付款条款"),
                block("p3", "√月度付款"));
        assertThat(resolution.certain()).isTrue();
        assertThat(resolution.excludedBlockIds()).containsExactlyInAnyOrder("p0", "p1", "p2");
    }

    private void productMarkerMissingMonthly() {
        assertThat(classify(
                block("p0", "□按节点付款"),
                block("p1", "到货节点付款条款")).certain()).isFalse();
    }

    private void productMarkerReversed() {
        assertThat(classify(
                block("p0", "√月度付款"),
                block("p1", "□按节点付款")).certain()).isFalse();
    }

    private void productMarkerConflict() {
        assertThat(classify(
                block("p0", "□按节点付款"),
                block("p1", "√按节点付款"),
                block("p2", "√月度付款")).certain()).isFalse();
    }

    private void engineeringMarkerValid() {
        var resolution = classify(
                block("e0", "进度款：A模式□B模式"),
                block("e1", "B模式：按节点付款："),
                block("e2", "节点1完成后付款70%"),
                block("e3", "竣工款：支付至80%"));
        assertThat(resolution.certain()).isTrue();
        assertThat(resolution.excludedBlockIds()).containsExactlyInAnyOrder("e1", "e2");
    }

    private void engineeringMissingCompletion() {
        assertThat(classify(
                block("e0", "进度款：A模式□B模式"),
                block("e1", "B模式：按节点付款："),
                block("e2", "节点1完成后付款70%")).certain()).isFalse();
    }

    private void engineeringConflict() {
        assertThat(classify(
                block("e0", "进度款：A模式□B模式"),
                block("e0b", "进度款：□A模式 B模式"),
                block("e1", "B模式：按节点付款："),
                block("e2", "竣工款：支付至80%")).certain()).isFalse();
    }

    private void bothFamiliesFailClosed() {
        assertThat(classify(
                block("p0", "□按节点付款"),
                block("p1", "√月度付款"),
                block("e0", "进度款：A模式□B模式"),
                block("e1", "B模式：按节点付款："),
                block("e2", "竣工款：支付至80%")).certain()).isFalse();
    }

    private void noMarkerFamily() {
        var resolution = classify(
                block("n0", "普通付款条款"),
                block("n1", "本工程无预付款。"));
        assertThat(resolution.certain()).isTrue();
        assertThat(resolution.excludedBlockIds()).isEmpty();
    }

    private void prepaymentGrammar() {
        assertProbe(
                ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY,
                "本工程无预付款。",
                List.of("0"));
        assertProbe(
                ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY,
                "无预付款但另行约定10%",
                List.of());
    }

    private void engineeringProgressGrammar() {
        assertProbe(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "A模式：按月形象进度付款，甲方支付上月完成合格形象进度产值的70%；",
                List.of("70"));
        assertProbe(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "B模式：按节点付款，节点完成后支付70%",
                List.of());
    }

    private void productProgressSameBlock() {
        assertProbe(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "到货验收款：支付至乙方到货总价的65%",
                List.of("65"));
        assertProbe(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "普通付款：支付至乙方到货总价的65%",
                List.of());
    }

    private void productProgressAdjacent() {
        var adjacent = List.of(
                block("a0", "到货验收款："),
                block("a1", "支付至乙方到货总价的65%"));
        assertThat(values(probe(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                List.of(adjacent.get(1)),
                adjacent))).containsExactly("65");

        var separated = List.of(
                block("s0", "到货验收款："),
                block("s1", "其他说明"),
                block("s2", "支付至乙方到货总价的65%"));
        assertThat(probe(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                List.of(separated.get(2)),
                separated)).isEmpty();
    }

    private void completionGrammar() {
        var blocks = List.of(
                block("c0", "竣工款：验收后支付至已完工程量的80%"),
                block("c1", "安装完工款：验收后支付至该批安装完工款金额的85%"),
                block("c2", "完成后支付90%"));
        assertThat(values(probe(
                ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY,
                blocks,
                blocks))).containsExactlyInAnyOrder("80", "85");
    }

    private void settlementGrammar() {
        assertProbe(
                ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
                "结算款：审核后支付至结算总价的97%",
                List.of("97"));
        assertProbe(
                ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
                "结算审核完成后支付97%",
                List.of());
    }

    private void warrantyGrammar() {
        var blocks = List.of(
                block("w0", "质保金：质保金为工程结算总价的3%"),
                block("w1", "乙方提交保证金额为结算价3%的《质量保函》"),
                block("w2", "普通保证金为5%"));
        assertThat(values(probe(
                ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY,
                blocks,
                blocks))).containsExactly("3", "3");
    }

    private void multiPercentAndUncertainSys() {
        String text = "A模式：按月形象进度付款，提交100%发票，"
                + "甲方支付上月完成合格形象进度产值的70%；";
        var target = block("m0", text);
        List<String> first = values(probe(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                List.of(target),
                List.of(target)));
        List<String> second = values(probe(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                List.of(target),
                List.copyOf(List.of(target))));
        assertThat(first).containsExactly("70");
        assertThat(second).isEqualTo(first);

        var uncertainDocument = document(block("u0", "□按节点付款"));
        var resolution =
                InactivePaymentBranchClassifierV20260729.classify(uncertainDocument.blocks());
        assertThat(resolution.certain()).isFalse();
        var collector = new ConsistencyCandidateCollector(
                (code, role, blocks) -> List.of(),
                List.of(new ConsistencyCandidateCollector.SemanticClassifier(
                        InactivePaymentBranchClassifierV20260729.CLASSIFIER_ID,
                        resolution::excludes)));
        ConsistencyPolicySnapshot policy = new RuntimeRuleSetLoader().load(V29)
                .policyMap().get(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY);
        var batch = collector.collect(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PROGRESS_PAYMENT_RATIO",
                uncertainDocument,
                policy);
        assertThat(batch.blockScanLedger()).allSatisfy(entry -> {
            assertThat(entry.status()).isEqualTo(LedgerStatus.UNCERTAIN);
            assertThat(entry.reason()).isEqualTo("SEMANTIC_CLASSIFIER_FAILED");
        });
        PointEvidence evidence = ConsistencySetCollector.collect(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PROGRESS_PAYMENT_RATIO",
                batch,
                uncertainDocument,
                policy);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(evidence.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    private void v3OracleAndLedger() throws Exception {
        assertThat(Task034MvpE2eAcceptanceHarnessTest
                .v3SameValueProgressPollutionResultForRegression())
                .isEqualTo(Task034MvpE2eAcceptanceHarnessTest.CoverageResult.NOT_MATCHED);

        JsonNode ledger = objectMapper.readTree(
                V3_OUTPUT.resolve("production-branch-scope-ledger.json").toFile());
        assertThat(ledger.path("humanGroundTruthExcludedCount").asInt()).isEqualTo(6);
        assertThat(ledger.path("productionEntryCount").asInt()).isEqualTo(70);
        assertThat(ledger.withArray("entries").size()).isEqualTo(70);
        for (JsonNode entry : ledger.withArray("entries")) {
            assertThat(entry.path("status").asText()).isEqualTo("EXCLUDED");
            assertThat(entry.path("reason").asText()).isEqualTo("SEMANTIC_EXCLUDED");
            assertThat(entry.path("blockTextSha256").asText()).matches("[0-9a-f]{64}");
        }
        JsonNode manifest =
                objectMapper.readTree(V3_OUTPUT.resolve("run-manifest.json").toFile());
        assertThat(manifest.path("humanGroundTruthIncludedCount").asInt()).isEqualTo(57);
        assertThat(manifest.path("humanGroundTruthExcludedCount").asInt()).isEqualTo(6);
        assertThat(manifest.path("productionInactiveBranchLedgerCount").asInt()).isEqualTo(70);
    }

    private void assertProbe(
            ReviewPointCode code,
            String text,
            List<String> expectedValues) {
        var target = block("probe", text);
        assertThat(values(probe(code, List.of(target), List.of(target))))
                .containsExactlyElementsOf(expectedValues);
    }

    private List<EvidenceCandidate> probe(
            ReviewPointCode code,
            List<WordParserSpikeDocument.DocumentBlock> target,
            List<WordParserSpikeDocument.DocumentBlock> context) {
        return preparer.probeAllForPoint(
                code,
                ratioRole(code),
                target,
                ParserBackedReviewInputPreparer.ProbeExecutionMode.CONSISTENCY_FULL_SCAN_V29,
                null,
                context);
    }

    private static List<String> values(List<EvidenceCandidate> candidates) {
        return candidates.stream().map(EvidenceCandidate::candidateValue).toList();
    }

    private static String ratioRole(ReviewPointCode code) {
        return switch (code) {
            case PREPAYMENT_RATIO_CONSISTENCY -> "PREPAYMENT_RATIO";
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> "PROGRESS_PAYMENT_RATIO";
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> "COMPLETION_PAYMENT_RATIO";
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> "SETTLEMENT_PAYMENT_RATIO";
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> "WARRANTY_RETENTION_RATIO";
            default -> throw new IllegalArgumentException("not a ratio point: " + code);
        };
    }

    private static InactivePaymentBranchClassifierV20260729.Resolution classify(
            WordParserSpikeDocument.DocumentBlock... blocks) {
        return InactivePaymentBranchClassifierV20260729.classify(List.of(blocks));
    }

    private static WordParserSpikeDocument.DocumentBlock block(String id, String text) {
        return new WordParserSpikeDocument.DocumentBlock(
                id,
                WordParserSpikeDocument.BlockType.PARAGRAPH,
                text,
                text,
                List.of("付款条款"),
                WordParserSpikeDocument.RegionType.BODY,
                WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "synthetic.docx",
                null,
                null,
                List.of(),
                WordParserSpikeDocument.ConfidenceLevel.HIGH,
                WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
    }

    private static WordParserSpikeDocument document(
            WordParserSpikeDocument.DocumentBlock... blocks) {
        return new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("synthetic", "synthetic.docx"),
                List.of(blocks),
                List.of(),
                List.of(),
                new WordParserSpikeDocument.ParseQualityReport(
                        "DOCX",
                        "test",
                        "zh-CN",
                        0,
                        blocks.length,
                        0,
                        0,
                        0,
                        0,
                        false,
                        WordParserSpikeDocument.ParseStatus.GOOD,
                        "HIGH",
                        0,
                        0,
                        0,
                        List.of()),
                new WordParserSpikeDocument.ScopeCoverageReport(
                        true,
                        List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"),
                        List.of(),
                        List.of()));
    }

    private static int count(String value, String token) {
        return (int) Pattern.compile(Pattern.quote(token)).matcher(value).results().count();
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }

    private static Path resourcePath(String path) {
        try {
            return Path.of(Objects.requireNonNull(
                    VersionedRatioScopeV20260729Test.class.getResource(path)).toURI());
        } catch (Exception error) {
            throw new IllegalStateException("Unable to resolve test resource " + path, error);
        }
    }

    private static NamedCheck check(String name, CheckedRunnable action) {
        return new NamedCheck(name, action);
    }

    private record NamedCheck(String name, CheckedRunnable action) {
        NamedCheck {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(action, "action");
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
