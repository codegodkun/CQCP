package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollector.BlockScanLedger;
import com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollector.ConsistencyCandidateBatch;
import com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollector.LedgerStatus;
import com.cqcp.apiserver.wordparser.WordParserSpikeDocument;
import com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsistencySetCollectorTest {

    private static final ConsistencyPolicySnapshot POLICY = new ConsistencyPolicySnapshot(
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

    private static final ConsistencyPolicySnapshot CNY_POLICY = new ConsistencyPolicySnapshot(
            "CONSISTENCY_SET", 1, 8, 64,
            "consistency-scope-v20260715.1",
            List.of("BODY", "APPENDIX"),
            List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"),
            List.of("SOURCE_CONFIDENCE", "PARSE_CONFIDENCE", "VALUE_GRAMMAR",
                    "ROLE_LABEL", "REGION_CONTEXT", "ANCHOR_IDENTITY"),
            List.of(),
            "consistency-canonicalization-v20260715.1", "DECIMAL", "CNY",
            "mvp-occurrence-identity-v1",
            List.of("reviewPointCode", "blockId"),
            List.of("reviewPointCode", "blockId", "previewElementRef"));

    private static final ConsistencyPolicySnapshot PERCENT_POLICY = new ConsistencyPolicySnapshot(
            "CONSISTENCY_SET", 1, 8, 64,
            "consistency-scope-v20260715.1",
            List.of("BODY", "APPENDIX"),
            List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"),
            List.of("SOURCE_CONFIDENCE", "PARSE_CONFIDENCE", "VALUE_GRAMMAR",
                    "ROLE_LABEL", "REGION_CONTEXT", "ANCHOR_IDENTITY"),
            List.of(),
            "consistency-canonicalization-v20260715.1", "DECIMAL", "PERCENT",
            "mvp-occurrence-identity-v1",
            List.of("reviewPointCode", "blockId"),
            List.of("reviewPointCode", "blockId", "previewElementRef"));

    @Test void parseFailedReturnsSystemFailureMissing() {
        var evidence = collect(parseDoc(ParseStatus.FAILED), List.of(), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(evidence.diagnosticCode()).isEqualTo("SYS_INDEX_INCOMPLETE");
        assertThat(evidence.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_NOT_FOUND);
    }

    @Test void parsePartialReturnsAmbiguousLowConfidence() {
        var evidence = collect(parseDoc(ParseStatus.PARTIAL), List.of(), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(evidence.diagnosticCode()).isEqualTo("SYS_PARSE_LOW_CONFIDENCE");
    }

    @Test void parseLowConfidenceReturnsAmbiguousLowConfidence() {
        var evidence = collect(parseDoc(ParseStatus.LOW_CONFIDENCE), List.of(), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(evidence.diagnosticCode()).isEqualTo("SYS_PARSE_LOW_CONFIDENCE");
    }

    @Test void lowConfidenceBlockCountReturnsAmbiguous() {
        var doc = docBuilder(ParseStatus.GOOD).lowConfidenceBlockCount(1).build();
        var evidence = collect(doc, List.of(), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
    }

    @Test void lowConfidenceRegionCountReturnsAmbiguous() {
        var doc = docBuilder(ParseStatus.GOOD).lowConfidenceRegionCount(2).build();
        var evidence = collect(doc, List.of(), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
    }

    @Test void lowConfidenceTableCountReturnsAmbiguous() {
        var doc = docBuilder(ParseStatus.GOOD).lowConfidenceTableCount(1).build();
        var evidence = collect(doc, List.of(), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
    }

    @Test void scopeNotVerifiedReturnsSystemFailure() {
        var doc = docBuilder(ParseStatus.GOOD).scopeVerified(false).build();
        var evidence = collect(doc, List.of(), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(evidence.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void scopeWithUnresolvedSignalsReturnsSystemFailure() {
        var doc = docBuilder(ParseStatus.GOOD).scopeVerified(true)
                .unresolvedSignals(List.of("scan failed")).build();
        var evidence = collect(doc, List.of(), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
    }

    @Test void missingBlockReturnsSystemFailure() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var cand = candidateWithBlock("nonexistent", "v");
        var evidence = collect(doc, List.of(cand), POLICY);
        assertThat(evidence.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void sourceOriginMismatchReturnsSystemFailure() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var cand = new EvidenceCandidate(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b1", "t", true, true, true,
                List.of(), "BODY", null, null, null, null,
                "NORMAL", "OTHER_ORIGIN", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        assertThat(collect(doc, List.of(cand), POLICY).diagnosticCode())
                .isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void extractionModeMismatchReturnsSystemFailure() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var cand = new EvidenceCandidate(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b1", "t", true, true, true,
                List.of(), "BODY", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "OTHER_MODE", "HIGH", "BLOCK_LEVEL", List.of());
        assertThat(collect(doc, List.of(cand), POLICY).diagnosticCode())
                .isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void noRoleLabelReturnsAmbiguousRoleConflict() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        assertThat(collect(doc, List.of(candidateWithSignals(false, true, true)), POLICY)
                .diagnosticCode()).isEqualTo("SYS_ROLE_CONFLICT");
    }

    @Test void noValueFormatReturnsAmbiguous() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        assertThat(collect(doc, List.of(candidateWithSignals(true, false, true)), POLICY)
                .diagnosticCode()).isEqualTo("SYS_EVIDENCE_AMBIGUOUS");
    }

    @Test void noBlockAttributionReturnsAmbiguousRoleConflict() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        assertThat(collect(doc, List.of(candidateWithSignals(true, true, false)), POLICY)
                .diagnosticCode()).isEqualTo("SYS_ROLE_CONFLICT");
    }

    @Test void sameBlockSameValueFoldsToOne() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var evidence = collect(doc, List.of(
                candidateWithBlock("b1", "vA"),
                candidateWithBlock("b1", "vA")), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.occurrences()).hasSize(1);
    }

    @Test void sameBlockDifferentValuesIsConflict() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var evidence = collect(doc, List.of(
                candidateWithBlock("b1", "vA"),
                candidateWithBlock("b1", "vB")), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(evidence.diagnosticCode()).isEqualTo("SYS_ROLE_CONFLICT");
    }

    @Test void twoBlocksSameValueFormTwoOccurrences() {
        var doc = docBuilder(ParseStatus.GOOD).addBlock("b2").build();
        var evidence = collect(doc, List.of(
                candidateWithBlock("b1", "vX"),
                candidateWithBlock("b2", "vX")), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.occurrences()).hasSize(2);
    }

    @Test void occurrenceBudgetExceededReturnsBudgetTruncated() {
        var doc = docBuilder(ParseStatus.GOOD).addBlock("b2").addBlock("b3").build();
        var evidence = collect(doc, List.of(
                candidateWithBlock("b1", "v1"),
                candidateWithBlock("b2", "v2"),
                candidateWithBlock("b3", "v3")), budgetPolicy(2, 8));
        assertThat(evidence.notConcludedReason()).isEqualTo(NotConcludedReasonCode.MODEL_BUDGET_EXCEEDED);
    }

    @Test void maxCandidatesExceededReturnsBudgetTruncated() {
        var doc = docBuilder(ParseStatus.GOOD).addBlock("b2").addBlock("b3").addBlock("b4").build();
        var evidence = collect(doc, List.of(
                candidateWithBlock("b1", "v1"),
                candidateWithBlock("b2", "v2"),
                candidateWithBlock("b3", "v3"),
                candidateWithBlock("b4", "v4")), budgetPolicy(64, 2));
        assertThat(evidence.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUDGET_EXCEEDED");
    }

    @Test void emptyCompleteSetReturnsMissing() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var evidence = collect(doc, List.of(), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.MISSING);
        assertThat(evidence.diagnosticCode()).isEqualTo("SYS_INDEX_INCOMPLETE");
    }

    @Test void readySingleValueProducesConfirmed() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var evidence = collect(doc, List.of(candidateWithBlock("b1", "test-val")), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.candidateValue()).isEqualTo("test-val");
        assertThat(evidence.occurrences()).hasSize(1);
    }

    @Test void textCanonicalizationStripsWhitespaceAndLowercases() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var evidence = collect(doc, List.of(candidateWithBlock("b1", "  Hello World  ")), POLICY);
        assertThat(evidence.candidateValue()).isEqualTo("helloworld");
    }

    @Test void cnyCanonicalizationRemovesCommasAndTrailingZeros() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var cand = fullCandidate(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, "CONTRACT_TOTAL_AMOUNT", "1,234,500.00", "b1", true, true, true);
        var evidence = collectForPoint(doc, List.of(cand), CNY_POLICY,
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, "CONTRACT_TOTAL_AMOUNT");
        assertThat(evidence.candidateValue()).isEqualTo("1234500");
    }

    @Test void percent70And70PercentGroupTogether() {
        var doc = docBuilder(ParseStatus.GOOD).addBlock("b2").build();
        var evidence = collectForPoint(doc, List.of(
                percentCandidate("b1", "70%"),
                percentCandidate("b2", "70")), PERCENT_POLICY,
                ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, "PREPAYMENT_RATIO");
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.occurrences()).hasSize(2);
        assertThat(evidence.candidateValue()).isEqualTo("70");
    }

    @Test void percent07Stays07() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var evidence = collectForPoint(doc, List.of(percentCandidate("b1", "0.7")), PERCENT_POLICY,
                ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, "PREPAYMENT_RATIO");
        assertThat(evidence.candidateValue()).isEqualTo("0.7");
    }

    @Test void percent07And70AreDifferent() {
        var doc = docBuilder(ParseStatus.GOOD).addBlock("b2").build();
        var evidence = collectForPoint(doc, List.of(
                percentCandidate("b1", "0.7"),
                percentCandidate("b2", "70")), PERCENT_POLICY,
                ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, "PREPAYMENT_RATIO");
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.candidateValue()).isNull();
    }

    @Test void tableCellWithoutExactRefFailsSystemFailure() {
        var doc = docBuilder(ParseStatus.GOOD).addBlock("b-tcell").build();
        var cand = new EvidenceCandidate(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b-tcell", "t", true, true, true,
                List.of(), "BODY", "table-1", 0, 0, "table:table-1/row:0",
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "TABLE_CELL", List.of());
        var batch = fullBatch(doc, List.of(cand));
        var evidence = ConsistencySetCollector.collect(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, POLICY);
        assertThat(evidence.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void candidateBatchImmutability() {
        var raw = new ArrayList<EvidenceCandidate>();
        var batch = new ConsistencyCandidateBatch(raw, List.of(), List.of(), true, List.of(), List.of());
        raw.add(candidateWithBlock("b1", "t"));
        assertThat(batch.rawCandidates()).isEmpty();
    }

    @Test void unsupportedCanonicalizationVersionThrows() {
        var bad = new ConsistencyPolicySnapshot(
                "CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1",
                List.of("BODY", "APPENDIX"), List.of("TOC"), List.of(), List.of(),
                "unsupported", "TEXT", "NONE",
                "mvp-occurrence-identity-v1", List.of(), List.of());
        var doc = docBuilder(ParseStatus.GOOD).build();
        assertThatThrownBy(() -> collect(doc, List.of(), bad))
                .isInstanceOf(IllegalStateException.class);
    }

    // ══════════ Real collector chain helpers ══════════

    private static WordParserSpikeDocument.ParseQualityReport reportGood(int blockCount) {
        return new WordParserSpikeDocument.ParseQualityReport(
                "DOCX", "t", "zh-CN", 0, blockCount, 0, 0, 0, 0, false,
                WordParserSpikeDocument.ParseStatus.GOOD, "HIGH", 0, 0, 0, List.of());
    }

    private static WordParserSpikeDocument.ScopeCoverageReport scopeFull() {
        return new WordParserSpikeDocument.ScopeCoverageReport(true,
                List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of(), List.of());
    }

    // ══════════ helpers ══════════

    private PointEvidence collect(
            WordParserSpikeDocument doc, List<EvidenceCandidate> raw, ConsistencyPolicySnapshot pol) {
        return collectForPoint(doc, raw, pol, ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B");
    }

    private PointEvidence collectForPoint(
            WordParserSpikeDocument doc, List<EvidenceCandidate> raw,
            ConsistencyPolicySnapshot pol, ReviewPointCode code, String role) {
        return ConsistencySetCollector.collect(
                code, role, fullBatch(doc, raw), doc, pol);
    }

    private ConsistencyCandidateBatch fullBatch(WordParserSpikeDocument doc, List<EvidenceCandidate> raw) {
        var ledger = new ArrayList<BlockScanLedger>();
        for (var block : doc.blocks()) {
            var matched = raw.stream().anyMatch(c -> block.blockId().equals(c.blockId()));
            ledger.add(new BlockScanLedger(block.blockId(),
                    block.regionType().name(), block.contextType().name(),
                    matched ? LedgerStatus.CANDIDATE_EMITTED : LedgerStatus.SCANNED_NO_MATCH,
                    matched ? "CANDIDATE_ACCEPTED" : "NO_MATCH"));
        }
        var scope = doc.scopeCoverageReport();
        boolean fullScanned = scope.verified()
                && scope.unresolvedSignals().isEmpty()
                && ledger.size() == doc.blocks().size()
                && ledger.stream().noneMatch(l -> l.status() == LedgerStatus.UNCERTAIN);
        return new ConsistencyCandidateBatch(List.copyOf(raw), List.copyOf(ledger), List.of(), fullScanned,
                scope.handledStrongContextTypes(), List.of());
    }

    private EvidenceCandidate candidateWithBlock(String blockId, String value) {
        return fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", value, blockId, true, true, true);
    }

    private EvidenceCandidate candidateWithSignals(boolean rl, boolean vf, boolean ba) {
        return fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v", "b1", rl, vf, ba);
    }

    private EvidenceCandidate percentCandidate(String blockId, String value) {
        return fullCandidate(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, "PREPAYMENT_RATIO", value, blockId, true, true, true);
    }

    private EvidenceCandidate fullCandidate(
            ReviewPointCode code, String role, String value, String blockId,
            boolean rl, boolean vf, boolean ba) {
        return new EvidenceCandidate(
                code, role, value, blockId, value, rl, vf, ba,
                List.of(), "BODY", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
    }

    @Test void exact64OccurrencesReady() {
        var doc = docForBlocks(64);
        var cands = new java.util.ArrayList<EvidenceCandidate>();
        for (int i = 0; i < 64; i++) cands.add(fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v", "b" + i, true, true, true));
        var pol = budgetPolicy(64, 8);
        var ev = collectForPoint(doc, cands, pol, ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B");
        assertThat(ev.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(ev.occurrences()).hasSize(64);
    }

    @Test void exact65OccurrencesBudgetTruncated() {
        var doc = docForBlocks(65);
        var cands = new java.util.ArrayList<EvidenceCandidate>();
        for (int i = 0; i < 65; i++) cands.add(fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v", "b" + i, true, true, true));
        var pol = budgetPolicy(64, 8);
        var ev = collectForPoint(doc, cands, pol, ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B");
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.MODEL_BUDGET_EXCEEDED);
    }

    @Test void exact8DistinctValuesReady() {
        var doc = docForBlocks(8);
        var cands = new java.util.ArrayList<EvidenceCandidate>();
        for (int i = 0; i < 8; i++) cands.add(fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v" + i, "b" + i, true, true, true));
        var pol = budgetPolicy(64, 8);
        var ev = collectForPoint(doc, cands, pol, ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B");
        assertThat(ev.status()).isEqualTo(EvidenceStatus.CONFIRMED);
    }

    @Test void exact9DistinctValuesBudgetTruncated() {
        var doc = docForBlocks(9);
        var cands = new java.util.ArrayList<EvidenceCandidate>();
        for (int i = 0; i < 9; i++) cands.add(fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v" + i, "b" + i, true, true, true));
        var pol = budgetPolicy(64, 8);
        var ev = collectForPoint(doc, cands, pol, ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B");
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUDGET_EXCEEDED");
    }

    @Test void goodAndBadGrammarFailsWholeSet() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var good = fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "good", "b1", true, true, true);
        var bad = fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "bad", "b1", true, false, true);
        var batch = fullBatch(doc, List.of(good, bad));
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_AMBIGUOUS");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
        assertThat(ev.slotCoverages())
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.coverageStatus()).isEqualTo(EvidenceSlotCoverageStatus.AMBIGUOUS);
                    assertThat(slot.diagnosticCode()).isEqualTo("SYS_EVIDENCE_AMBIGUOUS");
                });
    }

    @Test void realBlockMediumConfidenceDowngrades() {
        var block = new WordParserSpikeDocument.DocumentBlock("b1",
                WordParserSpikeDocument.BlockType.PARAGRAPH, "t", "t", List.of(),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", null, null, List.of(),
                WordParserSpikeDocument.ConfidenceLevel.MEDIUM,
                WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        var doc = new WordParserSpikeDocument(new WordParserSpikeDocument.Metadata("t", "t.docx"),
                List.of(block), List.of(), List.of(),
                new WordParserSpikeDocument.ParseQualityReport("DOCX", "t", "zh-CN", 0, 1, 0, 0, 0, 0, false,
                        WordParserSpikeDocument.ParseStatus.GOOD, "HIGH", 0, 0, 0, List.of()),
                new WordParserSpikeDocument.ScopeCoverageReport(true,
                        List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of(), List.of()));
        var cand = new EvidenceCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b1", "t", true, true, true, List.of(), "BODY", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        var pol = budgetPolicy(64, 8);
        var batch = fullBatch(doc, List.of(cand));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_PARSE_LOW_CONFIDENCE");
    }

    @Test void cellCellIdentityTwoOccurrencesAndAnchors() {
        var tableCells1 = List.of(new WordParserSpikeDocument.TableCellSpan(0, "v", 0, 1));
        var block1 = new WordParserSpikeDocument.DocumentBlock("b-cell0",
                WordParserSpikeDocument.BlockType.TABLE_ROW, "v", "v", List.of("s"),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", "table-1", 0, tableCells1,
                WordParserSpikeDocument.ConfidenceLevel.HIGH,
                WordParserSpikeDocument.PreviewAnchorLevel.TABLE_CELL);
        var tableCells2 = List.of(new WordParserSpikeDocument.TableCellSpan(1, "v", 0, 1));
        var block2 = new WordParserSpikeDocument.DocumentBlock("b-cell1",
                WordParserSpikeDocument.BlockType.TABLE_ROW, "v", "v", List.of("s"),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", "table-2", 1, tableCells2,
                WordParserSpikeDocument.ConfidenceLevel.HIGH,
                WordParserSpikeDocument.PreviewAnchorLevel.TABLE_CELL);
        var doc = docForBlocks(new String[]{}, List.of(block1, block2));
        var cand1 = fullTableCellCandidate("b-cell0", "table-1", 0, 0, "table:table-1/row:0/cell:0");
        var cand2 = fullTableCellCandidate("b-cell1", "table-2", 1, 1, "table:table-2/row:1/cell:1");
        var pol = budgetPolicy(64, 8);
        var batch = fullBatch(doc, List.of(cand1, cand2));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(ev.candidateValue()).isEqualTo("v");
        assertThat(ev.occurrences()).hasSize(2);
        assertThat(ev.occurrences().get(0).locationLevel()).isEqualTo("TABLE_CELL");
        assertThat(ev.occurrences().get(0).previewElementRef()).isEqualTo("table:table-1/row:0/cell:0");
        assertThat(ev.occurrences().get(1).locationLevel()).isEqualTo("TABLE_CELL");
        assertThat(ev.occurrences().get(1).previewElementRef()).isEqualTo("table:table-2/row:1/cell:1");
    }

    @Test void blockCellIdentityTwoOccurrencesAndAnchors() {
        var block1 = new WordParserSpikeDocument.DocumentBlock("b-para",
                WordParserSpikeDocument.BlockType.PARAGRAPH, "v", "v", List.of("s"),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", null, null, List.of(),
                WordParserSpikeDocument.ConfidenceLevel.HIGH,
                WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        var tableCells = List.of(new WordParserSpikeDocument.TableCellSpan(0, "v", 0, 1));
        var block2 = new WordParserSpikeDocument.DocumentBlock("b-cell",
                WordParserSpikeDocument.BlockType.TABLE_ROW, "v", "v", List.of("s"),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", "table-1", 0, tableCells,
                WordParserSpikeDocument.ConfidenceLevel.HIGH,
                WordParserSpikeDocument.PreviewAnchorLevel.TABLE_CELL);
        var doc = docForBlocks(new String[]{}, List.of(block1, block2));
        var cand1 = new EvidenceCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b-para", "t", true, true, true, List.of(), "BODY", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        var cand2 = fullTableCellCandidate("b-cell", "table-1", 0, 0, "table:table-1/row:0/cell:0");
        var pol = budgetPolicy(64, 8);
        var batch = fullBatch(doc, List.of(cand1, cand2));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(ev.candidateValue()).isEqualTo("v");
        assertThat(ev.occurrences()).hasSize(2);
        // BLOCK identity occurrence
        assertThat(ev.occurrences().get(0).locationLevel()).isEqualTo("BLOCK_LEVEL");
        assertThat(ev.occurrences().get(0).previewElementRef()).isNull();
        // TABLE_CELL identity occurrence
        assertThat(ev.occurrences().get(1).locationLevel()).isEqualTo("TABLE_CELL");
        assertThat(ev.occurrences().get(1).previewElementRef()).isEqualTo("table:table-1/row:0/cell:0");
    }

    @Test void forgedRowIndexMismatch() {
        var block = forgedBaseBlock("table-real", 0, 0);
        var doc = forgedBaseDoc(List.of(block));
        var forged = fullTableCellCandidate("b0", "table-real", 99, 0, "table:table-real/row:99/cell:0");
        var batch = forgedBatch(List.of(forged));
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void forgedCellIndexMismatch() {
        var block = forgedBaseBlock("table-real", 0, 0);
        var doc = forgedBaseDoc(List.of(block));
        var forged = fullTableCellCandidate("b0", "table-real", 0, 99, "table:table-real/row:0/cell:99");
        var batch = forgedBatch(List.of(forged));
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void forgedPreviewElementRefMismatch() {
        var block = forgedBaseBlock("table-real", 0, 0);
        var doc = forgedBaseDoc(List.of(block));
        var forged = fullTableCellCandidate("b0", "table-real", 0, 0, "table:table-real/row:0/cell:1");
        var batch = forgedBatch(List.of(forged));
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void realBlockLowConfidenceDowngrades() {
        var block = new WordParserSpikeDocument.DocumentBlock("b1", WordParserSpikeDocument.BlockType.PARAGRAPH, "t", "t", List.of(), WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL, WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED, "t.docx", null, null, List.of(), WordParserSpikeDocument.ConfidenceLevel.LOW, WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        var doc = new WordParserSpikeDocument(new WordParserSpikeDocument.Metadata("t", "t.docx"), List.of(block), List.of(), List.of(), new WordParserSpikeDocument.ParseQualityReport("DOCX", "t", "zh-CN", 0, 1, 0, 0, 0, 0, false, WordParserSpikeDocument.ParseStatus.GOOD, "HIGH", 0, 0, 0, List.of()), new WordParserSpikeDocument.ScopeCoverageReport(true, List.of("TOC","HEADER_FOOTER","DELETED","VOIDED"), List.of(), List.of()));
        var cand = new EvidenceCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v", "b1", "t", true, true, true, List.of(), "BODY", null, null, null, null, "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        var pol = budgetPolicy(64, 8);
        var batch = fullBatch(doc, List.of(cand));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
    }

    @Test void forgedTableCellRefTableIdMismatch() {
        var block = new WordParserSpikeDocument.DocumentBlock("b0", WordParserSpikeDocument.BlockType.TABLE_ROW, "c0 | c1", "c0 c1",
                List.of(), WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", "table-real", 0, List.of(new WordParserSpikeDocument.TableCellSpan(0, "c0", 0, 2)),
                WordParserSpikeDocument.ConfidenceLevel.HIGH, WordParserSpikeDocument.PreviewAnchorLevel.TABLE_CELL);
        var doc = new WordParserSpikeDocument(new WordParserSpikeDocument.Metadata("t", "t.docx"), List.of(block), List.of(), List.of(),
                new WordParserSpikeDocument.ParseQualityReport("DOCX", "t", "zh-CN", 0, 1, 0, 0, 0, 0, false,
                        WordParserSpikeDocument.ParseStatus.GOOD, "HIGH", 0, 0, 0, List.of()),
                new WordParserSpikeDocument.ScopeCoverageReport(true, List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of(), List.of()));
        var forged = new EvidenceCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v", "b0", "t", true, true, true,
                List.of(), "BODY", "table-fake", 0, 0, "table:table-fake/row:0/cell:0",
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "TABLE_CELL", List.of());
        var batch = new ConsistencyCandidateBatch(List.of(forged),
                List.of(new BlockScanLedger("b0", "BODY", "NORMAL", LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED")),
                List.of(), true, List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    // ══════════ Real collector chain: CandidateCollector → SetCollector ══════════

    @Test void realChainGoodAndBadGrammarProducesAmbiguous() {
        var block = bodyBlock("b1");
        var doc = new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"), List.of(block), List.of(), List.of(),
                reportGood(1), scopeFull());
        var probe = (ConsistencyCandidateCollector.ProbeDescriptor) (code, role, blks) -> {
            if (blks.size() == 1 && "b1".equals(blks.get(0).blockId())) {
                return List.of(
                        fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "good", "b1", true, true, true),
                        fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "bad", "b1", true, false, true));
            }
            return List.of();
        };
        var cc = new ConsistencyCandidateCollector(probe);
        var batch = cc.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, budgetPolicy(64, 8));
        assertThat(batch.rawCandidates()).hasSize(2);
        assertThat(batch.rejectedCandidates()).hasSize(1);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, budgetPolicy(64, 8));
        // grammar mismatch → SYS_EVIDENCE_AMBIGUOUS (priority 10 > priority 11)
        assertThat(ev.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_AMBIGUOUS");
    }

    @Test void realChainBadGrammarWithRoleConflictPrioritizesRole() {
        var block = bodyBlock("b1");
        var doc = new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"), List.of(block), List.of(), List.of(),
                reportGood(1), scopeFull());
        var probe = (ConsistencyCandidateCollector.ProbeDescriptor) (code, role, blks) -> {
            if (blks.size() == 1 && "b1".equals(blks.get(0).blockId())) {
                return List.of(
                        fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "bad", "b1", false, false, true));
            }
            return List.of();
        };
        var cc = new ConsistencyCandidateCollector(probe);
        var batch = cc.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, budgetPolicy(64, 8));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, budgetPolicy(64, 8));
        // role conflict (priority 9) > grammar (priority 10)
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_ROLE_CONFLICT");
    }

    @Test void realChainGrammarWithSourceFailureSourceWins() {
        // Source failure (priority 6) > grammar (priority 10)
        var block = bodyBlock("b1");
        var doc = new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"), List.of(block), List.of(), List.of(),
                reportGood(1), scopeFull());
        var probe = (ConsistencyCandidateCollector.ProbeDescriptor) (code, role, blks) -> {
            if (blks.size() == 1 && "b1".equals(blks.get(0).blockId())) {
                return List.of(new EvidenceCandidate(
                        ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                        "b1", "t", true, false, true, List.of(), "BODY",
                        null, null, null, null,
                        "NORMAL", "OTHER_ORIGIN", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of()));
            }
            return List.of();
        };
        var cc = new ConsistencyCandidateCollector(probe);
        var batch = cc.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, budgetPolicy(64, 8));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, budgetPolicy(64, 8));
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.evidenceSummary()).isEqualTo("sourceOrigin mismatch");
    }

    @Test void realChainGrammarWithBlockConfidenceBlockConfidenceWins() {
        // Block confidence LOW (priority 8) > grammar (priority 10)
        var block = new WordParserSpikeDocument.DocumentBlock("b1",
                WordParserSpikeDocument.BlockType.PARAGRAPH, "t", "t", List.of(),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", null, null, List.of(),
                WordParserSpikeDocument.ConfidenceLevel.LOW,
                WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        var doc = new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"), List.of(block), List.of(), List.of(),
                reportGood(1), scopeFull());
        var probe = (ConsistencyCandidateCollector.ProbeDescriptor) (code, role, blks) -> {
            if (blks.size() == 1 && "b1".equals(blks.get(0).blockId())) {
                return List.of(fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v", "b1", true, false, true));
            }
            return List.of();
        };
        var cc = new ConsistencyCandidateCollector(probe);
        var batch = cc.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, budgetPolicy(64, 8));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, budgetPolicy(64, 8));
        assertThat(ev.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_PARSE_LOW_CONFIDENCE");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.PARSE_LOW_CONFIDENCE);
        assertThat(ev.evidenceSummary()).isEqualTo("real block confidence < HIGH");
    }

    @Test void realChainGrammarWithAttributionMismatchGrammarWins() {
        var block = bodyBlock("b1");
        var doc = new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"), List.of(block), List.of(), List.of(),
                reportGood(1), scopeFull());
        var probe = (ConsistencyCandidateCollector.ProbeDescriptor) (code, role, blks) -> {
            if (blks.size() == 1 && "b1".equals(blks.get(0).blockId())) {
                return List.of(fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v", "b1", true, false, false));
            }
            return List.of();
        };
        var cc = new ConsistencyCandidateCollector(probe);
        var batch = cc.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, budgetPolicy(64, 8));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, budgetPolicy(64, 8));
        assertThat(ev.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_AMBIGUOUS");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
        assertThat(ev.evidenceSummary()).isEqualTo("missing valueFormat");
    }

    @Test void realChainSourceFailurePlusGrammarBothOrders() {
        var block = bodyBlock("b1");
        var doc = new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"), List.of(block), List.of(), List.of(),
                reportGood(1), scopeFull());
        var badSource = new EvidenceCandidate(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b1", "t", true, true, true, List.of(), "BODY",
                null, null, null, null,
                "NORMAL", "OTHER_ORIGIN", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        var badGrammar = fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "bad", "b1", true, false, true);
        // Order A: source failure first, grammar failure second
        var probeA = (ConsistencyCandidateCollector.ProbeDescriptor) (code, role, blks) -> {
            if (blks.size() == 1 && "b1".equals(blks.get(0).blockId())) {
                return List.of(badSource, badGrammar);
            }
            return List.of();
        };
        var ccA = new ConsistencyCandidateCollector(probeA);
        var batchA = ccA.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, budgetPolicy(64, 8));
        assertThat(batchA.rawCandidates()).hasSize(2);
        assertThat(batchA.rejectedCandidates()).hasSize(1);
        var evA = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batchA, doc, budgetPolicy(64, 8));
        assertThat(evA.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(evA.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(evA.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(evA.evidenceSummary()).isEqualTo("sourceOrigin mismatch");
        // Order B: grammar failure first, source failure second
        var probeB = (ConsistencyCandidateCollector.ProbeDescriptor) (code, role, blks) -> {
            if (blks.size() == 1 && "b1".equals(blks.get(0).blockId())) {
                return List.of(badGrammar, badSource);
            }
            return List.of();
        };
        var ccB = new ConsistencyCandidateCollector(probeB);
        var batchB = ccB.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, budgetPolicy(64, 8));
        assertThat(batchB.rawCandidates()).hasSize(2);
        assertThat(batchB.rejectedCandidates()).hasSize(1);
        var evB = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batchB, doc, budgetPolicy(64, 8));
        assertThat(evB.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(evB.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(evB.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(evB.evidenceSummary()).isEqualTo("sourceOrigin mismatch");
        // Both orders produce exactly the same result
        assertThat(evA.status()).isEqualTo(evB.status());
        assertThat(evA.diagnosticCode()).isEqualTo(evB.diagnosticCode());
        assertThat(evA.notConcludedReason()).isEqualTo(evB.notConcludedReason());
        assertThat(evA.evidenceSummary()).isEqualTo(evB.evidenceSummary());
    }

    @Test void realChainBlockConfidencePlusGrammarBothOrders() {
        var block = new WordParserSpikeDocument.DocumentBlock("b1",
                WordParserSpikeDocument.BlockType.PARAGRAPH, "t", "t", List.of(),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", null, null, List.of(),
                WordParserSpikeDocument.ConfidenceLevel.LOW,
                WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        var doc = new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"), List.of(block), List.of(), List.of(),
                reportGood(1), scopeFull());
        var highConf = fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v", "b1", true, true, true);
        var badGrammar = fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "bad", "b1", true, false, true);
        // Order A: highConf first, badGrammar second
        var probeA = (ConsistencyCandidateCollector.ProbeDescriptor) (code, role, blks) -> {
            if (blks.size() == 1 && "b1".equals(blks.get(0).blockId())) {
                return List.of(highConf, badGrammar);
            }
            return List.of();
        };
        var ccA = new ConsistencyCandidateCollector(probeA);
        var batchA = ccA.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, budgetPolicy(64, 8));
        assertThat(batchA.rawCandidates()).hasSize(2);
        assertThat(batchA.rejectedCandidates()).hasSize(1);
        var evA = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batchA, doc, budgetPolicy(64, 8));
        assertThat(evA.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(evA.diagnosticCode()).isEqualTo("SYS_PARSE_LOW_CONFIDENCE");
        assertThat(evA.notConcludedReason()).isEqualTo(NotConcludedReasonCode.PARSE_LOW_CONFIDENCE);
        assertThat(evA.evidenceSummary()).isEqualTo("real block confidence < HIGH");
        // Order B: badGrammar first, highConf second
        var probeB = (ConsistencyCandidateCollector.ProbeDescriptor) (code, role, blks) -> {
            if (blks.size() == 1 && "b1".equals(blks.get(0).blockId())) {
                return List.of(badGrammar, highConf);
            }
            return List.of();
        };
        var ccB = new ConsistencyCandidateCollector(probeB);
        var batchB = ccB.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, budgetPolicy(64, 8));
        assertThat(batchB.rawCandidates()).hasSize(2);
        assertThat(batchB.rejectedCandidates()).hasSize(1);
        var evB = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batchB, doc, budgetPolicy(64, 8));
        assertThat(evB.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(evB.diagnosticCode()).isEqualTo("SYS_PARSE_LOW_CONFIDENCE");
        assertThat(evB.notConcludedReason()).isEqualTo(NotConcludedReasonCode.PARSE_LOW_CONFIDENCE);
        assertThat(evB.evidenceSummary()).isEqualTo("real block confidence < HIGH");
        // Both orders produce exactly the same result
        assertThat(evA.status()).isEqualTo(evB.status());
        assertThat(evA.diagnosticCode()).isEqualTo(evB.diagnosticCode());
        assertThat(evA.notConcludedReason()).isEqualTo(evB.notConcludedReason());
        assertThat(evA.evidenceSummary()).isEqualTo(evB.evidenceSummary());
    }

    @Test void realChainGrammarPlusAttributionMismatchBothOrders() {
        var block = bodyBlock("b1");
        var doc = new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"), List.of(block), List.of(), List.of(),
                reportGood(1), scopeFull());
        var badGrammar = fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v", "b1", true, false, true);
        var badAttribution = fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v", "b1", true, true, false);
        // Order A: grammar first, attribution second
        var probeA = (ConsistencyCandidateCollector.ProbeDescriptor) (code, role, blks) -> {
            if (blks.size() == 1 && "b1".equals(blks.get(0).blockId())) {
                return List.of(badGrammar, badAttribution);
            }
            return List.of();
        };
        var ccA = new ConsistencyCandidateCollector(probeA);
        var batchA = ccA.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, budgetPolicy(64, 8));
        assertThat(batchA.rawCandidates()).hasSize(2);
        assertThat(batchA.rejectedCandidates()).hasSize(1);
        var evA = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batchA, doc, budgetPolicy(64, 8));
        assertThat(evA.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(evA.diagnosticCode()).isEqualTo("SYS_EVIDENCE_AMBIGUOUS");
        assertThat(evA.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
        assertThat(evA.evidenceSummary()).isEqualTo("missing valueFormat");
        // Order B: attribution first, grammar second
        var probeB = (ConsistencyCandidateCollector.ProbeDescriptor) (code, role, blks) -> {
            if (blks.size() == 1 && "b1".equals(blks.get(0).blockId())) {
                return List.of(badAttribution, badGrammar);
            }
            return List.of();
        };
        var ccB = new ConsistencyCandidateCollector(probeB);
        var batchB = ccB.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", doc, budgetPolicy(64, 8));
        assertThat(batchB.rawCandidates()).hasSize(2);
        assertThat(batchB.rejectedCandidates()).hasSize(1);
        var evB = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batchB, doc, budgetPolicy(64, 8));
        assertThat(evB.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(evB.diagnosticCode()).isEqualTo("SYS_EVIDENCE_AMBIGUOUS");
        assertThat(evB.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
        assertThat(evB.evidenceSummary()).isEqualTo("missing valueFormat");
        // Both orders produce exactly the same result
        assertThat(evA.status()).isEqualTo(evB.status());
        assertThat(evA.diagnosticCode()).isEqualTo(evB.diagnosticCode());
        assertThat(evA.notConcludedReason()).isEqualTo(evB.notConcludedReason());
        assertThat(evA.evidenceSummary()).isEqualTo(evB.evidenceSummary());
    }

    @Test void policyRejectsWrongCardinalityMode() {
        var bad = new ConsistencyPolicySnapshot("SINGLE_VALUE", 1, 8, 64,
                "consistency-scope-v20260715.1", List.of("BODY"), List.of(), List.of(), List.of(),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1", List.of("reviewPointCode", "blockId"),
                List.of("reviewPointCode", "blockId", "previewElementRef"));
        var doc = docBuilder(ParseStatus.GOOD).build();
        assertThatThrownBy(() -> collect(doc, List.of(), bad)).isInstanceOf(IllegalStateException.class);
    }

    @Test void policyRejectsMissingAnchorVersion() {
        var bad = new ConsistencyPolicySnapshot("CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1", List.of("BODY"), List.of(), List.of(), List.of(),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "", List.of("reviewPointCode", "blockId"),
                List.of("reviewPointCode", "blockId", "previewElementRef"));
        var doc = docBuilder(ParseStatus.GOOD).build();
        assertThatThrownBy(() -> collect(doc, List.of(), bad)).isInstanceOf(IllegalStateException.class);
    }

    @Test void policyRejectsWrongAnchorVersion() {
        var bad = new ConsistencyPolicySnapshot("CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1", List.of("BODY"), List.of(), List.of(), List.of(),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "invalid-anchor-version",
                List.of("reviewPointCode", "blockId"),
                List.of("reviewPointCode", "blockId", "previewElementRef"));
        var doc = docBuilder(ParseStatus.GOOD).build();
        assertThatThrownBy(() -> collect(doc, List.of(candidateWithBlock("b1", "v")), bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("anchorVersion");
    }

    @Test void policyRejectsWrongBlockIdentityContent() {
        var bad = new ConsistencyPolicySnapshot("CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1", List.of("BODY"), List.of(), List.of(), List.of(),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1",
                List.of("blockId", "reviewPointCode"),  // wrong order
                List.of("reviewPointCode", "blockId", "previewElementRef"));
        var doc = docBuilder(ParseStatus.GOOD).build();
        assertThatThrownBy(() -> collect(doc, List.of(candidateWithBlock("b1", "v")), bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blockIdentity");
    }

    @Test void policyRejectsWrongTableCellIdentityContent() {
        var bad = new ConsistencyPolicySnapshot("CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1", List.of("BODY"), List.of(), List.of(), List.of(),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1",
                List.of("reviewPointCode", "blockId"),
                List.of("blockId", "reviewPointCode", "previewElementRef"));  // wrong order
        var doc = docBuilder(ParseStatus.GOOD).build();
        assertThatThrownBy(() -> collect(doc, List.of(candidateWithBlock("b1", "v")), bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tableCellIdentity");
    }

    @Test void policyRejectsWrongBlockIdentitySize() {
        var bad = new ConsistencyPolicySnapshot("CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1", List.of("BODY"), List.of(), List.of(), List.of(),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1",
                List.of("reviewPointCode"),  // missing blockId
                List.of("reviewPointCode", "blockId", "previewElementRef"));
        var doc = docBuilder(ParseStatus.GOOD).build();
        assertThatThrownBy(() -> collect(doc, List.of(candidateWithBlock("b1", "v")), bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blockIdentity");
    }

    @Test void forgedRowIndexFullAssertions() {
        var block = forgedBaseBlock("table-real", 0, 0);
        var doc = forgedBaseDoc(List.of(block));
        var forged = fullTableCellCandidate("b0", "table-real", 99, 0, "table:table-real/row:99/cell:0");
        var batch = forgedBatch(List.of(forged));
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void forgedCellIndexFullAssertions() {
        var block = forgedBaseBlock("table-real", 0, 0);
        var doc = forgedBaseDoc(List.of(block));
        var forged = fullTableCellCandidate("b0", "table-real", 0, 99, "table:table-real/row:0/cell:99");
        var batch = forgedBatch(List.of(forged));
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void forgedPreviewElementRefFullAssertions() {
        var block = forgedBaseBlock("table-real", 0, 0);
        var doc = forgedBaseDoc(List.of(block));
        var forged = fullTableCellCandidate("b0", "table-real", 0, 0, "table:table-real/row:0/cell:1");
        var batch = forgedBatch(List.of(forged));
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void forgedTableIdFullAssertions() {
        var block = forgedBaseBlock("table-real", 0, 0);
        var doc = forgedBaseDoc(List.of(block));
        var forged = fullTableCellCandidate("b0", "table-fake", 0, 0, "table:table-fake/row:0/cell:0");
        var batch = new ConsistencyCandidateBatch(List.of(forged),
                List.of(new BlockScanLedger("b0", "BODY", "NORMAL",
                        LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED")),
                List.of(), true, List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void carrierContextTypeMismatchFails() {
        var block = new WordParserSpikeDocument.DocumentBlock("b1",
                WordParserSpikeDocument.BlockType.PARAGRAPH, "t", "t", List.of(),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", null, null, List.of(),
                WordParserSpikeDocument.ConfidenceLevel.HIGH, WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        var doc = new WordParserSpikeDocument(new WordParserSpikeDocument.Metadata("t","t.docx"),
                List.of(block), List.of(), List.of(),
                reportGood(1), scopeFull());
        // Candidate claims TOC context type, but real block is NORMAL
        var cand = new EvidenceCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b1", "t", true, true, true, List.of(), "BODY", null, null, null, null,
                "TOC", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        var batch = new ConsistencyCandidateBatch(List.of(cand),
                List.of(new BlockScanLedger("b1", "BODY", "NORMAL",
                        LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED")),
                List.of(), true, List.of("TOC","HEADER_FOOTER","DELETED","VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void carrierBlockLevelPretendsTableCellFails() {
        // BLOCK_LEVEL carrier upgrade forgery: real block is BLOCK_LEVEL but candidate claims TABLE_CELL
        var block = new WordParserSpikeDocument.DocumentBlock("b1",
                WordParserSpikeDocument.BlockType.PARAGRAPH, "t", "t", List.of(),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", null, null, List.of(),
                WordParserSpikeDocument.ConfidenceLevel.HIGH, WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        var doc = new WordParserSpikeDocument(new WordParserSpikeDocument.Metadata("t","t.docx"),
                List.of(block), List.of(), List.of(),
                reportGood(1), scopeFull());
        var cand = new EvidenceCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b1", "t", true, true, true, List.of(), "BODY", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "TABLE_CELL", List.of());
        var batch = new ConsistencyCandidateBatch(List.of(cand),
                List.of(new BlockScanLedger("b1", "BODY", "NORMAL",
                        LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED")),
                List.of(), true, List.of("TOC","HEADER_FOOTER","DELETED","VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void readinessUnverifiedScopeReportCausesBundleInvalid() {
        var doc = docBuilder(ParseStatus.GOOD).scopeVerified(false).build();
        var batch = new ConsistencyCandidateBatch(List.of(candidateWithBlock("b1", "v")),
                List.of(new BlockScanLedger("b1", "BODY", "NORMAL",
                        LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED")),
                List.of(), true, List.of(), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void readinessUnresolvedSignalsCausesBundleInvalid() {
        var doc = docBuilder(ParseStatus.GOOD).scopeVerified(true)
                .unresolvedSignals(List.of("SCOPE_SCAN_TOC_FAILED")).build();
        var batch = new ConsistencyCandidateBatch(List.of(candidateWithBlock("b1", "v")),
                List.of(new BlockScanLedger("b1", "BODY", "NORMAL",
                        LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED")),
                List.of(), true, List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void readinessLedgerWithUncertainCausesBundleInvalid() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var batch = new ConsistencyCandidateBatch(List.of(candidateWithBlock("b1", "v")),
                List.of(new BlockScanLedger("b1", "BODY", "NORMAL",
                        LedgerStatus.UNCERTAIN, "PROBE_FAILED")),
                List.of(), true, List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void readinessMissingRequiredStrongContextCausesBundleInvalid() {
        var doc = new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"),
                List.of(bodyBlock("b1")), List.of(), List.of(),
                new WordParserSpikeDocument.ParseQualityReport("DOCX","t","zh-CN",0,1,0,0,0,0,false,
                        WordParserSpikeDocument.ParseStatus.GOOD,"HIGH",0,0,0,List.of()),
                new WordParserSpikeDocument.ScopeCoverageReport(true,
                        List.of("TOC", "HEADER_FOOTER"), List.of(), List.of()));
        var batch = new ConsistencyCandidateBatch(List.of(candidateWithBlock("b1", "v")),
                List.of(new BlockScanLedger("b1", "BODY", "NORMAL",
                        LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED")),
                List.of(), true, List.of("TOC", "HEADER_FOOTER"), List.of());
        var pol = new ConsistencyPolicySnapshot("CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1", List.of("BODY"),
                List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of(), List.of(),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1",
                List.of("reviewPointCode", "blockId"),
                List.of("reviewPointCode", "blockId", "previewElementRef"));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        // DELETED and VOIDED missing from scope report → bundle-invalid
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
    }

    @Test void unicodeNbspGroupedWithWhitespace() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var evidence = collect(doc, List.of(candidateWithBlock("b1", "Hello World")), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.candidateValue()).isEqualTo("helloworld");
    }

    @Test void unicodeNarrowNoBreakSpaceGroupedWithWhitespace() {
        var doc = docBuilder(ParseStatus.GOOD).addBlock("b2").build();
        var pol = new ConsistencyPolicySnapshot("CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1", List.of("BODY"), List.of(), List.of(), List.of(),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1",
                List.of("reviewPointCode", "blockId"),
                List.of("reviewPointCode", "blockId", "previewElementRef"));
        var evidence = collectForPoint(doc, List.of(
                candidateWithBlock("b1", "Hello World"),
                candidateWithBlock("b2", "Hello World")), pol,
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B");
        // Both normalize to same "helloworld" → same canonical group
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.candidateValue()).isEqualTo("helloworld");
        assertThat(evidence.occurrences()).hasSize(2);
    }

    @Test void unicodeIdeographicSpaceGroupedWithWhitespace() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var evidence = collect(doc, List.of(candidateWithBlock("b1", "合同　条款")), POLICY);
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.candidateValue()).isEqualTo("合同条款");
    }

    @Test void unicodeNextLineGroupedWithWhitespace() {
        var doc = docBuilder(ParseStatus.GOOD).addBlock("b2").build();
        var pol = new ConsistencyPolicySnapshot("CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1", List.of("BODY"), List.of(), List.of(), List.of(),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1",
                List.of("reviewPointCode", "blockId"),
                List.of("reviewPointCode", "blockId", "previewElementRef"));
        // U+0085 NEXT LINE should be removed like whitespace
        var nell = "Hello" + ((char) 0x0085) + "World";
        var evidence = collectForPoint(doc, List.of(
                candidateWithBlock("b1", nell),
                candidateWithBlock("b2", "Hello World")), pol,
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B");
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.candidateValue()).isEqualTo("helloworld");
        assertThat(evidence.occurrences()).hasSize(2);
    }

    @Test void unicodeFigureSpaceGroupedWithWhitespace() {
        var doc = docBuilder(ParseStatus.GOOD).addBlock("b2").build();
        var pol = new ConsistencyPolicySnapshot("CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1", List.of("BODY"), List.of(), List.of(), List.of(),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1",
                List.of("reviewPointCode", "blockId"),
                List.of("reviewPointCode", "blockId", "previewElementRef"));
        // U+2007 FIGURE SPACE should be removed like whitespace
        var figSpace = "Hello" + ((char) 0x2007) + "World";
        var evidence = collectForPoint(doc, List.of(
                candidateWithBlock("b1", figSpace),
                candidateWithBlock("b2", "Hello World")), pol,
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B");
        assertThat(evidence.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(evidence.candidateValue()).isEqualTo("helloworld");
        assertThat(evidence.occurrences()).hasSize(2);
    }

    @Test void carrierRegionTypeMismatchFails() {
        var block = new WordParserSpikeDocument.DocumentBlock("b1",
                WordParserSpikeDocument.BlockType.PARAGRAPH, "t", "t", List.of(),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", null, null, List.of(),
                WordParserSpikeDocument.ConfidenceLevel.HIGH, WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        var doc = new WordParserSpikeDocument(new WordParserSpikeDocument.Metadata("t","t.docx"),
                List.of(block), List.of(), List.of(),
                reportGood(1), scopeFull());
        // Candidate claims APPENDIX region type, but real block is BODY
        var cand = new EvidenceCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b1", "t", true, true, true, List.of(), "APPENDIX", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        var batch = new ConsistencyCandidateBatch(List.of(cand),
                List.of(new BlockScanLedger("b1", "BODY", "NORMAL",
                        LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED")),
                List.of(), true, List.of("TOC","HEADER_FOOTER","DELETED","VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void carrierPreviewAnchorLevelMismatchFails() {
        // Real block is TABLE_CELL but candidate claims BLOCK_LEVEL (downgrade forgery)
        var tableCells = List.of(new WordParserSpikeDocument.TableCellSpan(0, "t", 0, 1));
        var block = new WordParserSpikeDocument.DocumentBlock("b1",
                WordParserSpikeDocument.BlockType.TABLE_ROW, "t", "t", List.of(),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", "table-1", 0, tableCells,
                WordParserSpikeDocument.ConfidenceLevel.HIGH, WordParserSpikeDocument.PreviewAnchorLevel.TABLE_CELL);
        var doc = new WordParserSpikeDocument(new WordParserSpikeDocument.Metadata("t","t.docx"),
                List.of(block), List.of(), List.of(),
                reportGood(1), scopeFull());
        var cand = new EvidenceCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b1", "t", true, true, true, List.of(), "BODY", "table-1", 0, 0, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        var batch = new ConsistencyCandidateBatch(List.of(cand),
                List.of(new BlockScanLedger("b1", "BODY", "NORMAL",
                        LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED")),
                List.of(), true, List.of("TOC","HEADER_FOOTER","DELETED","VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void goodAndBadGrammarPreventsBusinessConclusion() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var good = fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "good", "b1", true, true, true);
        var bad = fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "bad", "b1", true, false, true);
        var batch = fullBatch(doc, List.of(good, bad));
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_AMBIGUOUS");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
        // No reliable slot
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
    }

    @Test void grammarFailureWithRoleConflictPrioritizesRoleConflict() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        // candidate with both roleLabel=false AND valueFormat=false
        // §3.4 priority: roleLabel (9) > valueFormat (10)
        var badRoleAndGrammar = fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "bad",
                "b1", false, false, true);
        var batch = fullBatch(doc, List.of(badRoleAndGrammar));
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_ROLE_CONFLICT");
    }

    @Test void readinessLedgerMissingEntryCausesBundleInvalid() {
        var block = bodyBlock("b1");
        var doc = docForBlocks(new String[]{}, List.of(block));
        // empty ledger → missing entry
        var batch = new ConsistencyCandidateBatch(List.of(),
                List.of(), List.of(), true,
                List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void readinessLedgerDuplicateEntryCausesBundleInvalid() {
        var block = bodyBlock("b1");
        var doc = docForBlocks(new String[]{}, List.of(block));
        var batch = new ConsistencyCandidateBatch(List.of(),
                List.of(
                        new BlockScanLedger("b1", "BODY", "NORMAL", LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED"),
                        new BlockScanLedger("b1", "BODY", "NORMAL", LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED")),
                List.of(), true,
                List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void readinessLedgerExtraEntryCausesBundleInvalid() {
        var block = bodyBlock("b1");
        var doc = docForBlocks(new String[]{}, List.of(block));
        // Two blocks in doc but three ledger entries
        var batch = new ConsistencyCandidateBatch(List.of(),
                List.of(
                        new BlockScanLedger("b1", "BODY", "NORMAL", LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH"),
                        new BlockScanLedger("b2", "BODY", "NORMAL", LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH")),
                List.of(), true,
                List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void readinessLedgerReorderedEntriesCausesBundleInvalid() {
        var blocks = List.of(bodyBlock("b1"), bodyBlock("b2"));
        var doc = new WordParserSpikeDocument(new WordParserSpikeDocument.Metadata("t", "t.docx"),
                blocks, List.of(), List.of(),
                new WordParserSpikeDocument.ParseQualityReport("DOCX", "t", "zh-CN", 0, 2, 0, 0, 0, 0, false,
                        WordParserSpikeDocument.ParseStatus.GOOD, "HIGH", 0, 0, 0, List.of()),
                new WordParserSpikeDocument.ScopeCoverageReport(true,
                        List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of(), List.of()));
        // Ledger has b2 then b1 instead of b1 then b2
        var batch = new ConsistencyCandidateBatch(List.of(),
                List.of(
                        new BlockScanLedger("b2", "BODY", "NORMAL", LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH"),
                        new BlockScanLedger("b1", "BODY", "NORMAL", LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH")),
                List.of(), true,
                List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.evidenceSummary()).contains("ledger coverage: blockId mismatch at 0");
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void readinessLedgerWrongBlockIdCausesBundleInvalid() {
        var block = bodyBlock("b1");
        var doc = docForBlocks(new String[]{}, List.of(block));
        var batch = new ConsistencyCandidateBatch(List.of(),
                List.of(new BlockScanLedger("wrong-id", "BODY", "NORMAL",
                        LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH")),
                List.of(), true,
                List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void readinessLedgerWithUncertainFails() {
        var block = bodyBlock("b1");
        var doc = docForBlocks(new String[]{}, List.of(block));
        var batch = new ConsistencyCandidateBatch(List.of(),
                List.of(new BlockScanLedger("b1", "BODY", "NORMAL",
                        LedgerStatus.UNCERTAIN, "PROBE_FAILED")),
                List.of(), true,
                List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void readinessMissingRequiredSemanticContextCausesBundleInvalid() {
        var block = bodyBlock("b1");
        var doc = new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"),
                List.of(block), List.of(), List.of(),
                new WordParserSpikeDocument.ParseQualityReport("DOCX","t","zh-CN",0,1,0,0,0,0,false,
                        WordParserSpikeDocument.ParseStatus.GOOD,"HIGH",0,0,0,List.of()),
                new WordParserSpikeDocument.ScopeCoverageReport(true,
                        List.of("TOC","HEADER_FOOTER","DELETED","VOIDED"), List.of(), List.of()));
        var batch = new ConsistencyCandidateBatch(List.of(),
                List.of(new BlockScanLedger("b1", "BODY", "NORMAL",
                        LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH")),
                List.of(), true,
                List.of("TOC","HEADER_FOOTER","DELETED","VOIDED"),
                List.of()); // empty handledSemanticContextTypes, but policy requires CONTRACT_TITLE_NAME_MENTION
        var pol = new ConsistencyPolicySnapshot("CONSISTENCY_SET", 1, 8, 64,
                "consistency-scope-v20260715.1", List.of("BODY"),
                List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"),
                List.of("SOURCE_CONFIDENCE", "PARSE_CONFIDENCE", "VALUE_GRAMMAR",
                        "ROLE_LABEL", "REGION_CONTEXT", "ANCHOR_IDENTITY"),
                List.of("CONTRACT_TITLE_NAME_MENTION"),
                "consistency-canonicalization-v20260715.1", "TEXT", "NONE",
                "mvp-occurrence-identity-v1",
                List.of("reviewPointCode", "blockId"),
                List.of("reviewPointCode", "blockId", "previewElementRef"));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_A", batch, doc, pol);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.SYSTEM_FAILURE);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_EVIDENCE_BUNDLE_INVALID");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.INTERNAL_RULE_ERROR);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.PARTIAL);
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    @Test void attributionReviewPointCodeMismatch() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var cand = new EvidenceCandidate(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_B", "v",
                "b1", "t", true, true, true,
                List.of(), "BODY", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        var batch = fullBatch(doc, List.of(cand));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, POLICY);
        assertRoleConflict(ev);
    }

    @Test void attributionReviewPointCodeMismatchSwappedOrder() {
        var doc = docBuilder(ParseStatus.GOOD).addBlock("b2").build();
        var good = fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v", "b1", true, true, true);
        var bad = new EvidenceCandidate(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY, "PARTY_B", "v",
                "b2", "t", true, true, true,
                List.of(), "BODY", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        // Bad candidate first
        var batch1 = fullBatch(doc, List.of(bad, good));
        var ev1 = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch1, doc, POLICY);
        assertRoleConflict(ev1);
        // Bad candidate last
        var batch2 = fullBatch(doc, List.of(good, bad));
        var ev2 = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch2, doc, POLICY);
        assertRoleConflict(ev2);
        assertThat(ev1.status()).isEqualTo(ev2.status());
        assertThat(ev1.diagnosticCode()).isEqualTo(ev2.diagnosticCode());
        assertThat(ev1.notConcludedReason()).isEqualTo(ev2.notConcludedReason());
        assertThat(ev1.evidenceSummary()).isEqualTo(ev2.evidenceSummary());
    }

    @Test void attributionCandidateRoleMismatch() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var cand = new EvidenceCandidate(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_A", "v",
                "b1", "t", true, true, true,
                List.of(), "BODY", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        var batch = fullBatch(doc, List.of(cand));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, POLICY);
        assertRoleConflict(ev);
    }

    @Test void attributionCandidateRoleMismatchSwappedOrder() {
        var doc = docBuilder(ParseStatus.GOOD).addBlock("b2").build();
        var good = fullCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v", "b1", true, true, true);
        var bad = new EvidenceCandidate(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_A", "v",
                "b2", "t", true, true, true,
                List.of(), "BODY", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        var batch1 = fullBatch(doc, List.of(bad, good));
        var ev1 = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch1, doc, POLICY);
        assertRoleConflict(ev1);
        var batch2 = fullBatch(doc, List.of(good, bad));
        var ev2 = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch2, doc, POLICY);
        assertRoleConflict(ev2);
        assertThat(ev1.status()).isEqualTo(ev2.status());
        assertThat(ev1.diagnosticCode()).isEqualTo(ev2.diagnosticCode());
        assertThat(ev1.notConcludedReason()).isEqualTo(ev2.notConcludedReason());
        assertThat(ev1.evidenceSummary()).isEqualTo(ev2.evidenceSummary());
    }

    @Test void attributionLedgerMismatch() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var cand = new EvidenceCandidate(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b1", "t", true, true, true,
                List.of(), "BODY", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        // Ledger does not have CANDIDATE_EMITTED for b1
        var batch = new ConsistencyCandidateBatch(List.of(cand),
                List.of(new BlockScanLedger("b1", "BODY", "NORMAL",
                        LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH")),
                List.of(), true,
                List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, pol);
        assertRoleConflict(ev);
    }

    @Test void attributionLedgerMismatchSwappedOrder() {
        var doc = docBuilder(ParseStatus.GOOD).addBlock("b2").build();
        var good = new EvidenceCandidate(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b1", "t", true, true, true,
                List.of(), "BODY", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        var bad = new EvidenceCandidate(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b2", "t", true, true, true,
                List.of(), "BODY", null, null, null, null,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "BLOCK_LEVEL", List.of());
        // Ledger has CANDIDATE_EMITTED for b1 but not for b2
        var batch1 = new ConsistencyCandidateBatch(List.of(bad, good),
                List.of(
                        new BlockScanLedger("b1", "BODY", "NORMAL", LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED"),
                        new BlockScanLedger("b2", "BODY", "NORMAL", LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH")),
                List.of(), true,
                List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var pol = budgetPolicy(64, 8);
        var ev1 = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch1, doc, pol);
        assertRoleConflict(ev1);
        // Reverse order: bad candidate last
        var batch2 = new ConsistencyCandidateBatch(List.of(good, bad),
                List.of(
                        new BlockScanLedger("b1", "BODY", "NORMAL", LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED"),
                        new BlockScanLedger("b2", "BODY", "NORMAL", LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH")),
                List.of(), true,
                List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
        var ev2 = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch2, doc, pol);
        assertRoleConflict(ev2);
        assertThat(ev1.status()).isEqualTo(ev2.status());
        assertThat(ev1.diagnosticCode()).isEqualTo(ev2.diagnosticCode());
        assertThat(ev1.notConcludedReason()).isEqualTo(ev2.notConcludedReason());
        assertThat(ev1.evidenceSummary()).isEqualTo(ev2.evidenceSummary());
    }

    @Test void blockIdentitySameBlockMentionsFoldToOne() {
        var doc = docBuilder(ParseStatus.GOOD).build();
        var cands = List.of(
                candidateWithBlock("b1", "test-value"),
                candidateWithBlock("b1", "test-value"));
        var batch = fullBatch(doc, cands);
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, POLICY);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(ev.occurrences()).hasSize(1);
        var occ = ev.occurrences().get(0);
        assertThat(occ.candidateValue()).isEqualTo("test-value");
        assertThat(occ.blockId()).isEqualTo("b1");
        assertThat(occ.locationLevel()).isEqualTo("BLOCK_LEVEL");
        assertThat(occ.previewElementRef()).isNull();
    }

    @Test void tableCellIdentitySameRowDifferentCellRefsRemainDistinct() {
        var tableCells = List.of(
                new WordParserSpikeDocument.TableCellSpan(0, "a", 0, 1),
                new WordParserSpikeDocument.TableCellSpan(1, "b", 2, 3));
        var block = new WordParserSpikeDocument.DocumentBlock("b-row",
                WordParserSpikeDocument.BlockType.TABLE_ROW, "a b", "ab", List.of("s"),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", "table-1", 0, tableCells,
                WordParserSpikeDocument.ConfidenceLevel.HIGH,
                WordParserSpikeDocument.PreviewAnchorLevel.TABLE_CELL);
        var doc = new WordParserSpikeDocument(new WordParserSpikeDocument.Metadata("t","t.docx"),
                List.of(block), List.of(), List.of(),
                new WordParserSpikeDocument.ParseQualityReport("DOCX","t","zh-CN",0,1,0,0,0,0,false,
                        WordParserSpikeDocument.ParseStatus.GOOD,"HIGH",0,0,0,List.of()),
                new WordParserSpikeDocument.ScopeCoverageReport(true,
                        List.of("TOC","HEADER_FOOTER","DELETED","VOIDED"), List.of(), List.of()));
        var cand0 = new EvidenceCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b-row", "t", true, true, true, List.of(), "BODY",
                "table-1", 0, 0, "table:table-1/row:0/cell:0",
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "TABLE_CELL", List.of());
        var cand1 = new EvidenceCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b-row", "t", true, true, true, List.of(), "BODY",
                "table-1", 0, 1, "table:table-1/row:0/cell:1",
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "TABLE_CELL", List.of());
        var batch = fullBatch(doc, List.of(cand0, cand1));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, POLICY);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(ev.candidateValue()).isEqualTo("v");
        assertThat(ev.occurrences()).hasSize(2);
        assertThat(ev.occurrences()).anySatisfy(o -> {
            assertThat(o.candidateValue()).isEqualTo("v");
            assertThat(o.blockId()).isEqualTo("b-row");
            assertThat(o.locationLevel()).isEqualTo("TABLE_CELL");
            assertThat(o.previewElementRef()).isEqualTo("table:table-1/row:0/cell:0");
        });
        assertThat(ev.occurrences()).anySatisfy(o -> {
            assertThat(o.candidateValue()).isEqualTo("v");
            assertThat(o.blockId()).isEqualTo("b-row");
            assertThat(o.locationLevel()).isEqualTo("TABLE_CELL");
            assertThat(o.previewElementRef()).isEqualTo("table:table-1/row:0/cell:1");
        });
    }

    @Test void tableCellIdentityDuplicateSameCellFoldsToOne() {
        var tableCells = List.of(new WordParserSpikeDocument.TableCellSpan(0, "v", 0, 1));
        var block = new WordParserSpikeDocument.DocumentBlock("b-cell",
                WordParserSpikeDocument.BlockType.TABLE_ROW, "v", "v", List.of("s"),
                WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", "table-1", 0, tableCells,
                WordParserSpikeDocument.ConfidenceLevel.HIGH,
                WordParserSpikeDocument.PreviewAnchorLevel.TABLE_CELL);
        var doc = new WordParserSpikeDocument(new WordParserSpikeDocument.Metadata("t","t.docx"),
                List.of(block), List.of(), List.of(),
                new WordParserSpikeDocument.ParseQualityReport("DOCX","t","zh-CN",0,1,0,0,0,0,false,
                        WordParserSpikeDocument.ParseStatus.GOOD,"HIGH",0,0,0,List.of()),
                new WordParserSpikeDocument.ScopeCoverageReport(true,
                        List.of("TOC","HEADER_FOOTER","DELETED","VOIDED"), List.of(), List.of()));
        var cand = new EvidenceCandidate(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                "b-cell", "t", true, true, true, List.of(), "BODY",
                "table-1", 0, 0, "table:table-1/row:0/cell:0",
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "TABLE_CELL", List.of());
        var batch = fullBatch(doc, List.of(cand, cand));
        var ev = ConsistencySetCollector.collect(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", batch, doc, POLICY);
        assertThat(ev.status()).isEqualTo(EvidenceStatus.CONFIRMED);
        assertThat(ev.occurrences()).hasSize(1);
        var occ = ev.occurrences().get(0);
        assertThat(occ.candidateValue()).isEqualTo("v");
        assertThat(occ.blockId()).isEqualTo("b-cell");
        assertThat(occ.locationLevel()).isEqualTo("TABLE_CELL");
        assertThat(occ.previewElementRef()).isEqualTo("table:table-1/row:0/cell:0");
    }

    private static void assertRoleConflict(PointEvidence ev) {
        assertThat(ev.status()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(ev.slotCoverages()).hasSize(1);
        assertThat(ev.slotCoverages()).allMatch(s -> s.coverageStatus() == EvidenceSlotCoverageStatus.AMBIGUOUS);
        assertThat(ev.diagnosticCode()).isEqualTo("SYS_ROLE_CONFLICT");
        assertThat(ev.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
        assertThat(ev.evidenceSummary()).isEqualTo("ROLE_CONFLICT");
        assertThat(ev.slotCoverages()).allMatch(s -> !s.reliableAnchor());
        assertThat(ev.occurrences()).isEmpty();
    }

    private static EvidenceCandidate fullTableCellCandidate(
            String blockId, String tableId, int rowIndex, int cellIndex, String ref) {
        return new EvidenceCandidate(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY, "PARTY_B", "v",
                blockId, "t", true, true, true,
                List.of(), "BODY", tableId, rowIndex, cellIndex, ref,
                "NORMAL", "NATIVE_WORD", "STRUCTURED", "HIGH", "TABLE_CELL", List.of());
    }

    private WordParserSpikeDocument docForBlocks(String[] extraIds, List<WordParserSpikeDocument.DocumentBlock> extraBlocks) {
        var blocks = new ArrayList<WordParserSpikeDocument.DocumentBlock>();
        blocks.add(bodyBlock("b1"));
        for (var id : extraIds) blocks.add(bodyBlock(id));
        blocks.addAll(extraBlocks);
        return new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"), blocks, List.of(), List.of(),
                new WordParserSpikeDocument.ParseQualityReport("DOCX", "t", "zh-CN", 0, blocks.size(), 0, 0, 0, 0, false,
                        WordParserSpikeDocument.ParseStatus.GOOD, "HIGH", 0, 0, 0, List.of()),
                new WordParserSpikeDocument.ScopeCoverageReport(true,
                        List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of(), List.of()));
    }

    private static WordParserSpikeDocument.DocumentBlock bodyBlock(String id) {
        return new WordParserSpikeDocument.DocumentBlock(id,
                WordParserSpikeDocument.BlockType.PARAGRAPH, "t " + id, "t " + id,
                List.of("s"), WordParserSpikeDocument.RegionType.BODY,
                WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", null, null, List.of(),
                WordParserSpikeDocument.ConfidenceLevel.HIGH,
                WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
    }

    private static WordParserSpikeDocument.DocumentBlock forgedBaseBlock(String tableId, int rowIndex, int cellIndex) {
        return new WordParserSpikeDocument.DocumentBlock("b0",
                WordParserSpikeDocument.BlockType.TABLE_ROW, "c0 | c1", "c0 c1",
                List.of(), WordParserSpikeDocument.RegionType.BODY,
                WordParserSpikeDocument.ContextType.NORMAL,
                WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                "t.docx", tableId, rowIndex,
                List.of(new WordParserSpikeDocument.TableCellSpan(cellIndex, "c" + cellIndex, 0, 2)),
                WordParserSpikeDocument.ConfidenceLevel.HIGH,
                WordParserSpikeDocument.PreviewAnchorLevel.TABLE_CELL);
    }

    private static WordParserSpikeDocument forgedBaseDoc(List<WordParserSpikeDocument.DocumentBlock> blocks) {
        return new WordParserSpikeDocument(
                new WordParserSpikeDocument.Metadata("t", "t.docx"), blocks, List.of(), List.of(),
                new WordParserSpikeDocument.ParseQualityReport("DOCX", "t", "zh-CN", 0, blocks.size(), 0, 0, 0, 0, false,
                        WordParserSpikeDocument.ParseStatus.GOOD, "HIGH", 0, 0, 0, List.of()),
                new WordParserSpikeDocument.ScopeCoverageReport(true,
                        List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of(), List.of()));
    }

    private static ConsistencyCandidateBatch forgedBatch(List<EvidenceCandidate> cands) {
        return new ConsistencyCandidateBatch(List.copyOf(cands),
                List.of(new BlockScanLedger("b0", "BODY", "NORMAL",
                        LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED")),
                List.of(), true, List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of());
    }

    private static ConsistencyPolicySnapshot budgetPolicy(int occ, int max) {
        return new ConsistencyPolicySnapshot("CONSISTENCY_SET", 1, max, occ,
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
    }

    private WordParserSpikeDocument docForBlocks(int n) {
        var blocks = new java.util.ArrayList<WordParserSpikeDocument.DocumentBlock>();
        for (int i = 0; i < n; i++) {
            blocks.add(new WordParserSpikeDocument.DocumentBlock("b" + i, WordParserSpikeDocument.BlockType.PARAGRAPH, "t" + i, "t" + i,
                    List.of(), WordParserSpikeDocument.RegionType.BODY, WordParserSpikeDocument.ContextType.NORMAL,
                    WordParserSpikeDocument.SourceOrigin.NATIVE_WORD, WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "t.docx", null, null, List.of(), WordParserSpikeDocument.ConfidenceLevel.HIGH, WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL));
        }
        return new WordParserSpikeDocument(new WordParserSpikeDocument.Metadata("t", "t.docx"), blocks, List.of(), List.of(),
                new WordParserSpikeDocument.ParseQualityReport("DOCX", "t", "zh-CN", 0, n, 0, 0, 0, 0, false,
                        WordParserSpikeDocument.ParseStatus.GOOD, "HIGH", 0, 0, 0, List.of()),
                new WordParserSpikeDocument.ScopeCoverageReport(true,
                        List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"), List.of(), List.of()));
    }

    private WordParserSpikeDocument parseDoc(ParseStatus status) {
        return docBuilder(status).build();
    }

    private DocBuilder docBuilder(ParseStatus ps) { return new DocBuilder(ps); }

    private static class DocBuilder {
        private final ParseStatus parseStatus;
        private int lr, lb, lt;
        private boolean scopeVerified = true;
        private List<String> unresolved = List.of();
        private final List<String> extra = new ArrayList<>();

        DocBuilder(ParseStatus ps) { this.parseStatus = ps; }
        DocBuilder lowConfidenceRegionCount(int v) { lr = v; return this; }
        DocBuilder lowConfidenceBlockCount(int v) { lb = v; return this; }
        DocBuilder lowConfidenceTableCount(int v) { lt = v; return this; }
        DocBuilder scopeVerified(boolean v) { scopeVerified = v; return this; }
        DocBuilder unresolvedSignals(List<String> v) { unresolved = v; return this; }
        DocBuilder addBlock(String id) { extra.add(id); return this; }

        WordParserSpikeDocument build() {
            var blocks = new ArrayList<WordParserSpikeDocument.DocumentBlock>();
            blocks.add(bodyBlock("b1"));
            for (var id : extra) blocks.add(bodyBlock(id));
            var handled = new ArrayList<String>();
            if (scopeVerified && unresolved.isEmpty())
                handled.addAll(List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"));
            return new WordParserSpikeDocument(
                    new WordParserSpikeDocument.Metadata("t", "t.docx"), blocks, List.of(), List.of(),
                    new WordParserSpikeDocument.ParseQualityReport(
                            "DOCX", "t", "zh-CN", 0, blocks.size(), 0, 0, 0, 0, false,
                            parseStatus, "HIGH", lr, lb, lt, List.of()),
                    new WordParserSpikeDocument.ScopeCoverageReport(scopeVerified, handled, List.of(), unresolved));
        }

        private WordParserSpikeDocument.DocumentBlock bodyBlock(String id) {
            return new WordParserSpikeDocument.DocumentBlock(id,
                    WordParserSpikeDocument.BlockType.PARAGRAPH, "t " + id, "t " + id,
                    List.of("s"), WordParserSpikeDocument.RegionType.BODY,
                    WordParserSpikeDocument.ContextType.NORMAL,
                    WordParserSpikeDocument.SourceOrigin.NATIVE_WORD,
                    WordParserSpikeDocument.SourceExtractionMode.STRUCTURED,
                    "t.docx", null, null, List.of(),
                    WordParserSpikeDocument.ConfidenceLevel.HIGH,
                    WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL);
        }
    }
}
