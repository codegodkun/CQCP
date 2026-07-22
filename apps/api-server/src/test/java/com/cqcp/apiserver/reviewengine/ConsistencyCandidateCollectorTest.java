package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollector.BlockScanLedger;
import com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollector.ConsistencyCandidateBatch;
import com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollector.LedgerStatus;
import com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollector.ProbeDescriptor;
import com.cqcp.apiserver.wordparser.WordParserSpikeDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsistencyCandidateCollectorTest {

    private static final ConsistencyPolicySnapshot FULL_POLICY = new ConsistencyPolicySnapshot(
            "CONSISTENCY_SET", 1, 8, 64,
            "consistency-scope-v20260715.1",
            List.of("BODY", "APPENDIX"),
            List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"),
            List.of("SOURCE_CONFIDENCE", "PARSE_CONFIDENCE", "VALUE_GRAMMAR",
                    "ROLE_LABEL", "REGION_CONTEXT", "ANCHOR_IDENTITY"),
            List.of("CONTRACT_TITLE_NAME_MENTION", "AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION"),
            "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
            "mvp-occurrence-identity-v1",
            List.of("reviewPointCode", "blockId"),
            List.of("reviewPointCode", "blockId", "previewElementRef"));

    private static final ConsistencyPolicySnapshot NO_SEMANTIC_POLICY = new ConsistencyPolicySnapshot(
            "CONSISTENCY_SET", 1, 8, 64,
            "consistency-scope-v20260715.1",
            List.of("BODY", "APPENDIX"),
            List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"),
            List.of("SOURCE_CONFIDENCE", "PARSE_CONFIDENCE", "VALUE_GRAMMAR",
                    "ROLE_LABEL", "REGION_CONTEXT", "ANCHOR_IDENTITY"),
            List.of(),
            "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
            "mvp-occurrence-identity-v1",
            List.of("reviewPointCode", "blockId"),
            List.of("reviewPointCode", "blockId", "previewElementRef"));

    private static final ProbeDescriptor EMPTY_PROBE = (code, role, blocks) -> List.of();

    @Test
    void rejectsUnsupportedScopeVersion() {
        var badPolicy = new ConsistencyPolicySnapshot(
                "CONSISTENCY_SET", 1, 8, 64,
                "unsupported-version", List.of(), List.of(), List.of(), List.of(),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1", List.of(), List.of());
        var doc = docWith(body("b1", "NORMAL", "test"));
        var collector = new ConsistencyCandidateCollector(EMPTY_PROBE);

        assertThatThrownBy(() -> collector.collect(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, badPolicy))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scope version");
    }

    @Test
    void eachBlockGetsExactlyOneLedgerEntry() {
        var doc = docWith(
                body("b1", "NORMAL", "甲方：测试公司"),
                body("b2", "NORMAL", "合同条款"),
                appendix("b3", "NORMAL", "附件内容"));
        var collector = new ConsistencyCandidateCollector(EMPTY_PROBE);

        var batch = collector.collect(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, FULL_POLICY);

        assertThat(batch.blockScanLedger()).hasSize(3);
        assertThat(batch.blockScanLedger())
                .extracting(BlockScanLedger::blockId)
                .containsExactly("b1", "b2", "b3");
    }

    @Test
    void tocBlockLedgerShowsExcluded() {
        var doc = docWith(
                tocBlock("b-toc", "目录"),
                body("b-body", "NORMAL", "正文"));
        var collector = new ConsistencyCandidateCollector(EMPTY_PROBE);

        var batch = collector.collect(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, FULL_POLICY);

        assertThat(batch.blockScanLedger())
                .filteredOn(l -> "b-toc".equals(l.blockId()))
                .singleElement()
                .satisfies(l -> assertThat(l.status()).isEqualTo(LedgerStatus.EXCLUDED));
    }

    @Test
    void bodyAndAppendixBlocksAreScanned() {
        var doc = docWith(
                body("b-body", "NORMAL", "body text"),
                appendix("b-app", "NORMAL", "appendix text"));
        var collector = new ConsistencyCandidateCollector(EMPTY_PROBE);

        var batch = collector.collect(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, FULL_POLICY);

        assertThat(batch.blockScanLedger())
                .filteredOn(l -> l.status() == LedgerStatus.SCANNED_NO_MATCH)
                .hasSize(2);
    }

    @Test
    void probeReturnsCandidateMarksCandiateEmitted() {
        var doc = docWith(body("b1", "NORMAL", "甲方：测试公司"));
        var collector = new ConsistencyCandidateCollector(
                (code, role, blocks) -> List.of(candidate("b1", "测试公司")));

        var batch = collector.collect(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", doc, FULL_POLICY);

        assertThat(batch.rawCandidates()).hasSize(1);
        assertThat(batch.blockScanLedger())
                .filteredOn(l -> "b1".equals(l.blockId()))
                .singleElement()
                .satisfies(l -> assertThat(l.status()).isEqualTo(LedgerStatus.CANDIDATE_EMITTED));
    }

    @Test
    void contractTitleNameMentionExcludesForPartyA() {
        var doc = docWith(body("b1", "NORMAL", "某某工程合同"));
        var collector = new ConsistencyCandidateCollector(EMPTY_PROBE);

        var batch = collector.collect(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", doc, FULL_POLICY);

        assertThat(batch.handledSemanticContextTypes()).contains("CONTRACT_TITLE_NAME_MENTION");
        assertThat(batch.blockScanLedger())
                .filteredOn(l -> "b1".equals(l.blockId()))
                .singleElement()
                .satisfies(l -> assertThat(l.status()).isEqualTo(LedgerStatus.EXCLUDED));
    }

    @Test
    void agreementPreambleExcludesForPartyA() {
        var doc = docWith(body("b1", "NORMAL", "甲乙双方签署《施工合同》约定如下"));
        var collector = new ConsistencyCandidateCollector(EMPTY_PROBE);

        var batch = collector.collect(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", doc, FULL_POLICY);

        assertThat(batch.handledSemanticContextTypes()).contains("AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION");
        assertThat(batch.blockScanLedger())
                .filteredOn(l -> "b1".equals(l.blockId()))
                .singleElement()
                .satisfies(l -> assertThat(l.status()).isEqualTo(LedgerStatus.EXCLUDED));
    }

    @Test
    void semanticExclusionOnlyForPartyANotPartyB() {
        var doc = docWith(body("b1", "NORMAL", "某某工程合同"));
        var collector = new ConsistencyCandidateCollector(EMPTY_PROBE);

        var batch = collector.collect(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, FULL_POLICY);

        assertThat(batch.handledSemanticContextTypes()).isEmpty();
        assertThat(batch.blockScanLedger())
                .filteredOn(l -> "b1".equals(l.blockId()))
                .singleElement()
                .satisfies(l -> assertThat(l.status()).isEqualTo(LedgerStatus.SCANNED_NO_MATCH));
    }

    @Test
    void semanticContextNotHandledWhenPolicyMissing() {
        var doc = docWith(body("b1", "NORMAL", "某某工程合同"));
        var collector = new ConsistencyCandidateCollector(EMPTY_PROBE);

        var batch = collector.collect(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", doc, NO_SEMANTIC_POLICY);

        assertThat(batch.handledSemanticContextTypes()).isEmpty();
    }

    @Test
    void semanticClassifierMarkedHandledEvenIfNoBlockMatches() {
        var doc = docWith(body("b1", "NORMAL", "普通段落无合同字样"));
        var collector = new ConsistencyCandidateCollector(EMPTY_PROBE);

        var batch = collector.collect(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", doc, FULL_POLICY);

        // Classifiers were executed (marked handled) even though no match
        assertThat(batch.handledSemanticContextTypes()).contains(
                "CONTRACT_TITLE_NAME_MENTION", "AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION");
    }

    @Test
    void rejectedCandidatesFilledForValueGrammarRejection() {
        var doc = docWith(body("b1", "NORMAL", "测试"));
        // Probe returns a candidate with valueFormatSignal=false
        var badCandidate = new EvidenceCandidate(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", "150",
                "b1", "测试", true, false, true);
        var collector = new ConsistencyCandidateCollector(
                (code, role, blocks) -> List.of(badCandidate));

        var batch = collector.collect(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", doc, FULL_POLICY);

        assertThat(batch.rejectedCandidates()).isNotEmpty();
        assertThat(batch.rejectedCandidates())
                .anyMatch(r -> "VALUE_GRAMMAR_REJECTED".equals(r.reason()));
    }

    @Test
    void fullPolicyScopeScannedFalseWhenUnverifiedScope() {
        var doc = new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"),
                List.of(body("b1", "NORMAL", "test")), List.of(), List.of(),
                new WordParserSpikeDocument.ParseQualityReport(
                        "DOCX", "t", "zh-CN", 0, 1, 0, 0, 0, 0, false,
                        WordParserSpikeDocument.ParseStatus.GOOD, "HIGH", 0, 0, 0, List.of()),
                new WordParserSpikeDocument.ScopeCoverageReport(
                        false, List.of(), List.of(), List.of()));
        var collector = new ConsistencyCandidateCollector(EMPTY_PROBE);

        var batch = collector.collect(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, NO_SEMANTIC_POLICY);

        assertThat(batch.fullPolicyScopeScanned()).isFalse();
    }

    @Test
    void bothSemanticClassifiersExecuteNoShortCircuit() {
        var doc = docWith(body("b1", "NORMAL", "甲乙双方签署《某某工程合同》"));
        var titleCount = new java.util.concurrent.atomic.AtomicInteger(0);
        var preambleCount = new java.util.concurrent.atomic.AtomicInteger(0);
        var classifiers = List.of(
                new ConsistencyCandidateCollector.SemanticClassifier("CONTRACT_TITLE_NAME_MENTION",
                        block -> { titleCount.incrementAndGet(); return true; }),
                new ConsistencyCandidateCollector.SemanticClassifier("AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION",
                        block -> { preambleCount.incrementAndGet(); return true; }));
        var collector = new ConsistencyCandidateCollector(EMPTY_PROBE, classifiers);
        collector.collect(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", doc, FULL_POLICY);
        assertThat(titleCount.get()).isEqualTo(1);
        assertThat(preambleCount.get()).isEqualTo(1);
    }

    @Test
    void validateLedgerCoverageDetectsDuplicateAndMissing() {
        var blocks = List.of(
                body("b1", "NORMAL", "one"),
                body("b2", "NORMAL", "two"));
        var good = List.of(
                new BlockScanLedger("b1", "BODY", "NORMAL", LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH"),
                new BlockScanLedger("b2", "BODY", "NORMAL", LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH"));
        assertThat(ConsistencyCandidateCollector.validateLedgerCoverage(blocks, good)).isNull();
        var dup = List.of(
                new BlockScanLedger("b1", "BODY", "NORMAL", LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH"),
                new BlockScanLedger("b1", "BODY", "NORMAL", LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH"));
        assertThat(ConsistencyCandidateCollector.validateLedgerCoverage(blocks, dup)).isNotNull();
        var missing = List.of(
                new BlockScanLedger("b1", "BODY", "NORMAL", LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH"));
        assertThat(ConsistencyCandidateCollector.validateLedgerCoverage(blocks, missing)).isNotNull();
    }

    @Test
    void semanticClassifierExceptionProducesUncertainLedger() {
        var doc = docWith(body("b1", "NORMAL", "某某工程合同"));
        var failingClassifier = new ConsistencyCandidateCollector.SemanticClassifier(
                "CONTRACT_TITLE_NAME_MENTION",
                block -> { throw new RuntimeException("SIMULATED_CLASSIFIER_FAILURE"); });
        var secondClassifier = new ConsistencyCandidateCollector.SemanticClassifier(
                "AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION",
                block -> false);
        var collector = new ConsistencyCandidateCollector(
                ConsistencyCandidateCollectorTest.EMPTY_PROBE,
                List.of(failingClassifier, secondClassifier));
        var batch = collector.collect(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", doc, FULL_POLICY);

        // Failed classifier not in handled set
        assertThat(batch.handledSemanticContextTypes()).doesNotContain("CONTRACT_TITLE_NAME_MENTION");
        // The block with the failing classifier should have UNCERTAIN ledger
        assertThat(batch.blockScanLedger()).filteredOn(l -> "b1".equals(l.blockId())).singleElement()
                .satisfies(l -> {
                    assertThat(l.status()).isEqualTo(LedgerStatus.UNCERTAIN);
                    assertThat(l.reason()).isEqualTo("SEMANTIC_CLASSIFIER_FAILED");
                });
        assertThat(batch.fullPolicyScopeScanned()).isFalse();
    }

    @Test
    void probeExceptionProducesUncertainLedger() {
        var doc = docWith(body("b1", "NORMAL", "test"));
        var failingProbe = (ProbeDescriptor) (code, role, blocks) -> { throw new RuntimeException("fail"); };
        var collector = new ConsistencyCandidateCollector(failingProbe);
        var batch = collector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, NO_SEMANTIC_POLICY);
        assertThat(batch.blockScanLedger()).filteredOn(l -> "b1".equals(l.blockId())).singleElement()
                .satisfies(l -> { assertThat(l.status()).isEqualTo(LedgerStatus.UNCERTAIN); assertThat(l.reason()).isEqualTo("PROBE_FAILED"); });
        assertThat(batch.fullPolicyScopeScanned()).isFalse();
    }

    // ────────── helpers ──────────

    private EvidenceCandidate candidate(String blockId, String value) {
        return new EvidenceCandidate(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", value,
                blockId, value, true, true, true,
                List.of(), "BODY", null, null, null, null);
    }

    private WordParserSpikeDocument.DocumentBlock body(String id, String ctx, String text) {
        return new WordParserSpikeDocument.DocumentBlock(
                id, WordParserSpikeDocument.BlockType.PARAGRAPH, text, text,
                List.of("s"),
                WordParserSpikeDocument.RegionType.BODY,
                "NORMAL".equals(ctx) ? WordParserSpikeDocument.ContextType.NORMAL : WordParserSpikeDocument.ContextType.TOC,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", null, null, List.of(),
                WordParserSpikeDocument.ConfidenceLevel.HIGH,
                WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
    }

    private WordParserSpikeDocument.DocumentBlock appendix(String id, String ctx, String text) {
        return new WordParserSpikeDocument.DocumentBlock(
                id, WordParserSpikeDocument.BlockType.APPENDIX_TITLE, text, text,
                List.of("s"),
                WordParserSpikeDocument.RegionType.APPENDIX,
                WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", null, null, List.of(),
                WordParserSpikeDocument.ConfidenceLevel.HIGH,
                WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
    }

    private WordParserSpikeDocument.DocumentBlock tocBlock(String id, String text) {
        return new WordParserSpikeDocument.DocumentBlock(
                id, WordParserSpikeDocument.BlockType.TOC_ITEM, text, text,
                List.of("s"),
                WordParserSpikeDocument.RegionType.BODY,
                WordParserSpikeDocument.ContextType.TOC,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", null, null, List.of(),
                WordParserSpikeDocument.ConfidenceLevel.MEDIUM,
                WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
    }

    private WordParserSpikeDocument docWith(WordParserSpikeDocument.DocumentBlock... blocks) {
        return new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"),
                List.of(blocks), List.of(), List.of(),
                new WordParserSpikeDocument.ParseQualityReport(
                        "DOCX", "t", "zh-CN", 0, blocks.length, 0, 0, 0, 0, false,
                        WordParserSpikeDocument.ParseStatus.GOOD, "HIGH", 0, 0, 0, List.of()));
    }
}
