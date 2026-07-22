package com.cqcp.apiserver.reviewengine;

import com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollector.BlockScanLedger;
import com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollector.ConsistencyCandidateBatch;
import com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollector.LedgerStatus;
import com.cqcp.apiserver.wordparser.WordParserSpikeDocument;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class ConsistencySetCollector {

    private static final Pattern TABLE_CELL_REF = Pattern.compile("^table:[^/]+/row:[0-9]+/cell:[0-9]+$");
    private static final String CANONICALIZATION_VERSION = "consistency-canonicalization-v20260715.1";
    private static final String FROZEN_ANCHOR_VERSION = "mvp-occurrence-identity-v1";
    private static final List<String> FROZEN_BLOCK_IDENTITY = List.of("reviewPointCode", "blockId");
    private static final List<String> FROZEN_TABLE_CELL_IDENTITY = List.of("reviewPointCode", "blockId", "previewElementRef");

    private ConsistencySetCollector() {}

    static PointEvidence collect(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            ConsistencyCandidateBatch candidateBatch,
            WordParserSpikeDocument document,
            ConsistencyPolicySnapshot policy) {
        Objects.requireNonNull(reviewPointCode, "reviewPointCode");
        Objects.requireNonNull(candidateRole, "candidateRole");
        Objects.requireNonNull(candidateBatch, "candidateBatch");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(policy, "policy");

        // Step 0: Policy validation — cardinality, anchor version, identity fields
        if (!"CONSISTENCY_SET".equals(policy.cardinalityMode())) {
            throw new IllegalStateException("cardinalityMode must be CONSISTENCY_SET, got: " + policy.cardinalityMode());
        }
        if (!CANONICALIZATION_VERSION.equals(policy.canonicalizationVersion())) {
            throw new IllegalStateException("Unsupported canonicalization version: " + policy.canonicalizationVersion());
        }
        if (!FROZEN_ANCHOR_VERSION.equals(policy.anchorVersion())) {
            throw new IllegalStateException("anchorVersion must be " + FROZEN_ANCHOR_VERSION
                    + ", got: " + policy.anchorVersion());
        }
        validateIdentityFields("blockIdentity", policy.blockIdentity(), FROZEN_BLOCK_IDENTITY);
        validateIdentityFields("tableCellIdentity", policy.tableCellIdentity(), FROZEN_TABLE_CELL_IDENTITY);

        var rawCandidates = candidateBatch.rawCandidates();
        var parseReport = document.parseQualityReport();
        var scopeReport = document.scopeCoverageReport();

        // Step 2: Parser fatal
        if (parseReport.parseStatus() == WordParserSpikeDocument.ParseStatus.FAILED) {
            return sysfail(reviewPointCode, candidateRole, EvidenceSlotCoverageStatus.PARTIAL,
                    "SYS_INDEX_INCOMPLETE", NotConcludedReasonCode.EVIDENCE_NOT_FOUND, "INDEX_MISSING",
                    "parse failed");
        }
        // Step 3: Parser confidence
        if (parseReport.parseStatus() == WordParserSpikeDocument.ParseStatus.PARTIAL
                || parseReport.parseStatus() == WordParserSpikeDocument.ParseStatus.LOW_CONFIDENCE) {
            return ambigLow(reviewPointCode, candidateRole, "parse low confidence");
        }
        // Step 4: Low-confidence counts
        if (parseReport.lowConfidenceRegionCount() > 0
                || parseReport.lowConfidenceBlockCount() > 0
                || parseReport.lowConfidenceTableCount() > 0) {
            return ambigLow(reviewPointCode, candidateRole, "low confidence count > 0");
        }
        // Step 5: Scope observability — revalidate from actual carriers, not just batch boolean
        if (!candidateBatch.fullPolicyScopeScanned()) {
            return sysfail(reviewPointCode, candidateRole, EvidenceSlotCoverageStatus.PARTIAL,
                    "SYS_EVIDENCE_BUNDLE_INVALID", NotConcludedReasonCode.INTERNAL_RULE_ERROR, null,
                    "scope not fully scanned");
        }
        // Re-verify critical conditions from source carriers (not trusting batch boolean alone)
        if (!scopeReport.verified()) {
            return sysfail(reviewPointCode, candidateRole, EvidenceSlotCoverageStatus.PARTIAL,
                    "SYS_EVIDENCE_BUNDLE_INVALID", NotConcludedReasonCode.INTERNAL_RULE_ERROR, null,
                    "scope report not verified");
        }
        if (!scopeReport.unresolvedSignals().isEmpty()) {
            return sysfail(reviewPointCode, candidateRole, EvidenceSlotCoverageStatus.PARTIAL,
                    "SYS_EVIDENCE_BUNDLE_INVALID", NotConcludedReasonCode.INTERNAL_RULE_ERROR, null,
                    "scope has unresolved signals");
        }
        // Verify ledger has exactly one entry per document block, no UNCERTAIN
        var ledger = candidateBatch.blockScanLedger();
        var ledgerCoverageIssue = ConsistencyCandidateCollector.validateLedgerCoverage(document.blocks(), ledger);
        if (ledgerCoverageIssue != null) {
            return sysfail(reviewPointCode, candidateRole, EvidenceSlotCoverageStatus.PARTIAL,
                    "SYS_EVIDENCE_BUNDLE_INVALID", NotConcludedReasonCode.INTERNAL_RULE_ERROR, null,
                    "ledger coverage: " + ledgerCoverageIssue);
        }
        if (ledger.stream().anyMatch(l -> l.status() == LedgerStatus.UNCERTAIN)) {
            return sysfail(reviewPointCode, candidateRole, EvidenceSlotCoverageStatus.PARTIAL,
                    "SYS_EVIDENCE_BUNDLE_INVALID", NotConcludedReasonCode.INTERNAL_RULE_ERROR, null,
                    "ledger has UNCERTAIN entries");
        }
        // Verify required strong contexts are in scope report
        var requiredStrong = policy.strongExcludedContextTypes();
        for (String ctx : requiredStrong) {
            if (!scopeReport.handledStrongContextTypes().contains(ctx)) {
                return sysfail(reviewPointCode, candidateRole, EvidenceSlotCoverageStatus.PARTIAL,
                        "SYS_EVIDENCE_BUNDLE_INVALID", NotConcludedReasonCode.INTERNAL_RULE_ERROR, null,
                        "required strong context not handled: " + ctx);
            }
        }
        // Verify required semantic contexts are in handled list
        var requiredSemantic = policy.strongExcludedSemanticContexts();
        for (String ctx : requiredSemantic) {
            if (!candidateBatch.handledSemanticContextTypes().contains(ctx)) {
                return sysfail(reviewPointCode, candidateRole, EvidenceSlotCoverageStatus.PARTIAL,
                        "SYS_EVIDENCE_BUNDLE_INVALID", NotConcludedReasonCode.INTERNAL_RULE_ERROR, null,
                        "required semantic context not handled: " + ctx);
            }
        }

        // Step 6: Source lineage + carrier consistency — ALL candidates match real blocks
        for (var candidate : rawCandidates) {
            var blockOpt = findBlock(document, candidate.blockId());
            if (blockOpt.isEmpty()) return lineageFail(reviewPointCode, candidateRole, "block not found");
            var b = blockOpt.orElseThrow();

            // Carrier must match real block
            if (!b.regionType().name().equals(candidate.regionType()))
                return lineageFail(reviewPointCode, candidateRole, "regionType mismatch with real block");
            if (!b.contextType().name().equals(candidate.contextType()))
                return lineageFail(reviewPointCode, candidateRole, "contextType mismatch with real block");
            if (!b.previewAnchorLevel().name().equals(candidate.previewAnchorLevel()))
                return lineageFail(reviewPointCode, candidateRole, "previewAnchorLevel mismatch with real block");

            if (!b.sourceOrigin().name().equals(candidate.sourceOrigin()))
                return lineageFail(reviewPointCode, candidateRole, "sourceOrigin mismatch");
            if (!"NATIVE_WORD".equals(candidate.sourceOrigin()))
                return lineageFail(reviewPointCode, candidateRole, "source not NATIVE_WORD");
            if (!policy.includedRegionTypes().contains(b.regionType().name()))
                return lineageFail(reviewPointCode, candidateRole, "region not in includedRegionTypes");
            if (policy.strongExcludedContextTypes().contains(b.contextType().name()))
                return lineageFail(reviewPointCode, candidateRole, "context in strongExcludedContextTypes");
        }

        // Step 7: Extraction mode — ALL candidates
        for (var candidate : rawCandidates) {
            var block = findBlock(document, candidate.blockId());
            if (block.isPresent()) {
                var b = block.orElseThrow();
                if (!b.sourceExtractionMode().name().equals(candidate.sourceExtractionMode()))
                    return lineageFail(reviewPointCode, candidateRole, "extractionMode mismatch");
                if (!"STRUCTURED".equals(candidate.sourceExtractionMode()))
                    return lineageFail(reviewPointCode, candidateRole, "mode not STRUCTURED");
            }
        }

        // Step 8: Block confidence — ALL candidates vs real block
        for (var candidate : rawCandidates) {
            if (!"HIGH".equals(candidate.blockConfidence()))
                return ambigLow(reviewPointCode, candidateRole, "candidate confidence < HIGH");
            var blockOpt = findBlock(document, candidate.blockId());
            if (blockOpt.isPresent()) {
                var realBlock = blockOpt.orElseThrow();
                if (!"HIGH".equals(realBlock.blockConfidence().name()))
                    return ambigLow(reviewPointCode, candidateRole, "real block confidence < HIGH");
            }
        }

        // Step 9: Role label — ALL must be true (§3.4 priority 9)
        if (rawCandidates.stream().anyMatch(c -> !c.roleLabelSignal()))
            return ambig(reviewPointCode, candidateRole, "SYS_ROLE_CONFLICT",
                    NotConcludedReasonCode.EVIDENCE_AMBIGUOUS, "ROLE_CONFLICT", "missing roleLabel");

        // Step 10: ValueFormat — ALL must be true (§3.4 priority 10, BEFORE code/ledger/attribution)
        if (rawCandidates.stream().anyMatch(c -> !c.valueFormatSignal()))
            return ambig(reviewPointCode, candidateRole, "SYS_EVIDENCE_AMBIGUOUS",
                    NotConcludedReasonCode.EVIDENCE_AMBIGUOUS, null, "missing valueFormat");

        // Step 11: Code/role/ledger attribution + blockAttribution (§3.4 priority 11)
        // Both code/role/ledger and blockAttribution mismatch → AMBIGUOUS/ROLE_CONFLICT
        // code/role/ledger first, then blockAttribution
        for (var candidate : rawCandidates) {
            if (candidate.reviewPointCode() != reviewPointCode)
                return roleConflict(reviewPointCode, candidateRole, "reviewPointCode mismatch");
            if (!candidateRole.equals(candidate.candidateRole()))
                return roleConflict(reviewPointCode, candidateRole, "candidateRole mismatch");
            var inLedger = ledger.stream()
                    .anyMatch(l -> l.blockId().equals(candidate.blockId()) && l.status() == LedgerStatus.CANDIDATE_EMITTED);
            if (!inLedger)
                return roleConflict(reviewPointCode, candidateRole, "not in ledger CANDIDATE_EMITTED");
        }

        if (rawCandidates.stream().anyMatch(c -> !c.blockAttributionSignal()))
            return roleConflict(reviewPointCode, candidateRole, "missing blockAttribution");

        // Step 13: Canonicalization
        List<CanonicalEntry> canonicalEntries = new ArrayList<>();
        for (var candidate : rawCandidates) {
            var cv = canonicalize(candidate.candidateValue(), policy);
            if (cv == null)
                return ambig(reviewPointCode, candidateRole, "SYS_EVIDENCE_AMBIGUOUS",
                        NotConcludedReasonCode.EVIDENCE_AMBIGUOUS, null, "canonicalization failed");
            canonicalEntries.add(new CanonicalEntry(candidate, cv));
        }

        // Step 14: Anchor validation (§3.4 priority 12 = bundle-invalid)
        for (var entry : canonicalEntries) {
            var issue = validateAnchor(entry.candidate(), document);
            if (issue != null)
                return lineageFail(reviewPointCode, candidateRole, issue);
        }

        // Step 17: Identity fold
        var identityMap = new LinkedHashMap<String, List<CanonicalEntry>>();
        for (var entry : canonicalEntries) {
            var identity = buildIdentity(entry.candidate(), reviewPointCode, policy);
            identityMap.computeIfAbsent(identity, k -> new ArrayList<>()).add(entry);
        }

        var folded = new LinkedHashMap<String, CanonicalEntry>();
        boolean identityConflict = false;
        for (var entry : identityMap.entrySet()) {
            var entries = entry.getValue();
            var distinct = entries.stream().map(CanonicalEntry::canonicalValue).distinct().toList();
            if (distinct.size() > 1) { identityConflict = true; break; }
            folded.put(entry.getKey(), entries.getFirst());
        }
        if (identityConflict)
            return roleConflict(reviewPointCode, candidateRole, "same identity different canonical value");

        // Step 18: Occurrence budget (§3.4 priority 14)
        if (folded.size() > policy.occurrenceBudget())
            return budgetExceeded(reviewPointCode, candidateRole, "occurrence " + folded.size() + "/" + policy.occurrenceBudget());
        // Step 19: Distinct value budget (§3.4 priority 15)
        var distinctValues = folded.values().stream().map(CanonicalEntry::canonicalValue).distinct().toList();
        if (distinctValues.size() > policy.maxCandidates())
            return budgetExceeded(reviewPointCode, candidateRole, "distinct " + distinctValues.size() + "/" + policy.maxCandidates());
        // Step 20: Empty after fold (§3.4 priority 16)
        if (folded.isEmpty())
            return missing(reviewPointCode, candidateRole);

        // Step 21: READY (§3.4 priority 17)
        var sorted = buildOccurrences(folded, document);
        return readyEvidence(reviewPointCode, candidateRole, sorted, distinctValues, document);
    }

    // ────────── Canonicalization ──────────

    // Unicode White_Space: covers U+0085 NEXT LINE, U+00A0 NBSP, U+2007 FIGURE SPACE,
    // U+202F narrow no-break space, U+3000 ideographic space, and all Java whitespace
    private static final Pattern UNICODE_WHITESPACE = Pattern.compile(
            "[\\u0085\\u00A0\\u2007\\u202F\\u3000\\p{javaWhitespace}]+");

    private static String canonicalize(String value, ConsistencyPolicySnapshot policy) {
        if (value == null || value.isBlank()) return null;
        var vt = policy.valueType();
        var unit = policy.unit();
        if ("TEXT".equals(vt) && "NONE".equals(unit))
            return UNICODE_WHITESPACE.matcher(value.strip()).replaceAll("").toLowerCase(Locale.ROOT);
        if ("DECIMAL".equals(vt) && "CNY".equals(unit)) return canonAmount(value);
        if ("DECIMAL".equals(vt) && "PERCENT".equals(unit)) return canonPercent(value);
        return null;
    }

    private static String canonAmount(String v) {
        try { return new BigDecimal(v.replace(",", "").trim()).stripTrailingZeros().toPlainString(); }
        catch (NumberFormatException e) { return null; }
    }

    private static String canonPercent(String v) {
        try {
            var c = v.trim();
            if (c.endsWith("%")) c = c.substring(0, c.length() - 1).trim();
            return new BigDecimal(c).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) { return null; }
    }

    // ────────── Policy identity validation ──────────

    /**
     * Validates identity field list against the frozen expected values:
     * exact content, order, no nulls, no duplicates, no unknown fields.
     * Throws IllegalStateException on any violation.
     */
    private static void validateIdentityFields(String fieldName, List<String> actual, List<String> frozen) {
        if (actual == null) {
            throw new IllegalStateException(fieldName + " must not be null");
        }
        if (actual.size() != frozen.size()) {
            throw new IllegalStateException(fieldName + " size mismatch: expected " + frozen.size()
                    + ", got " + actual.size() + ": " + actual);
        }
        for (int i = 0; i < frozen.size(); i++) {
            if (!frozen.get(i).equals(actual.get(i))) {
                throw new IllegalStateException(fieldName + " mismatch at index " + i
                        + ": expected '" + frozen.get(i) + "', got '" + actual.get(i) + "'"
                        + " — full list: " + actual);
            }
        }
    }

    // ────────── Identity ──────────

    /**
     * Builds occurrence identity using policy-configured identity fields.
     * Dynamically constructs identity from policy.blockIdentity() or policy.tableCellIdentity()
     * field list. Each field token is resolved: reviewPointCode, blockId, previewElementRef.
     * Unknown token → IllegalStateException.
     */
    private static String buildIdentity(EvidenceCandidate c, ReviewPointCode code, ConsistencyPolicySnapshot policy) {
        var fields = "TABLE_CELL".equals(c.previewAnchorLevel())
                ? policy.tableCellIdentity()
                : policy.blockIdentity();
        var parts = new ArrayList<String>();
        for (var field : fields) {
            parts.add(resolveField(field, c, code));
        }
        return String.join("|", parts);
    }

    private static String resolveField(String field, EvidenceCandidate c, ReviewPointCode code) {
        return switch (field) {
            case "reviewPointCode" -> code.name();
            case "blockId" -> c.blockId() != null ? c.blockId() : "";
            case "previewElementRef" -> c.previewElementRef() != null ? c.previewElementRef() : "";
            default -> throw new IllegalStateException("Unknown identity field: " + field);
        };
    }

    // ────────── Anchor validation ──────────

    private static String validateAnchor(EvidenceCandidate c, WordParserSpikeDocument doc) {
        if (c.blockId() == null || c.blockId().isBlank()) return "候选缺少 blockId。";
        var block = findBlock(doc, c.blockId());
        if (block.isEmpty()) return "候选 blockId 不存在。";
        var b = block.orElseThrow();
        if (!b.blockId().equals(c.blockId())) return "blockId 不一致。";
        if (!"TABLE_CELL".equals(c.previewAnchorLevel())) return null;

        var ref = c.previewElementRef();
        if (ref == null || !TABLE_CELL_REF.matcher(ref).matches()) return "TABLE_CELL ref 无效。";
        var parts = ref.split("/");
        if (parts.length < 3) return "ref 不完整。";
        var refTable = parts[0];
        var refTableId = refTable.contains(":") ? refTable.substring(refTable.indexOf(':') + 1) : "";
        if (!refTableId.equals(c.tableId())) return "tableId 不一致。";
        if (!refTableId.equals(b.tableId())) return "tableId 与文档 block 不一致。";

        var refRowPart = parts[1];
        int refRowIdx = -1;
        if (refRowPart.startsWith("row:")) {
            try { refRowIdx = Integer.parseInt(refRowPart.substring(4)); } catch (NumberFormatException e) { return "ref row 解析失败。"; }
        }
        if (c.rowIndex() == null || !c.rowIndex().equals(refRowIdx)) return "rowIndex 与 ref 不一致。";
        if (b.rowIndex() == null || !b.rowIndex().equals(refRowIdx)) return "rowIndex 与文档 block 不一致。";

        var refCellPart = parts[2];
        final int refCellIdx;
        if (refCellPart.startsWith("cell:")) {
            try { refCellIdx = Integer.parseInt(refCellPart.substring(5)); } catch (NumberFormatException e) { return "ref cell 解析失败。"; }
        } else { refCellIdx = -1; }
        if (c.cellIndex() == null || !c.cellIndex().equals(refCellIdx)) return "cellIndex 与 ref 不一致。";
        boolean cellExists = b.tableCells().stream().anyMatch(tc -> tc.cellIndex() == refCellIdx);
        if (!cellExists) return "ref cellIndex 在 tableCells 中不存在。";
        return null;
    }

    // ────────── Occurrences ──────────

    private static List<PointEvidenceOccurrence> buildOccurrences(
            LinkedHashMap<String, CanonicalEntry> folded, WordParserSpikeDocument doc) {
        var result = new ArrayList<PointEvidenceOccurrence>();
        for (var entry : folded.entrySet()) {
            var ce = entry.getValue();
            var c = ce.candidate();
            var loc = "TABLE_CELL".equals(c.previewAnchorLevel()) ? "TABLE_CELL"
                    : (c.blockId() != null && !c.blockId().isBlank() ? "BLOCK_LEVEL" : null);
            result.add(new PointEvidenceOccurrence(ce.canonicalValue(), c.blockId(), c.blockText(),
                    c.sectionPath(), c.regionType(), EvidenceConfidenceLevel.HIGH.name(), loc, c.previewElementRef()));
        }
        result.sort((a, b) -> {
            var cmp = Integer.compare(indexOf(doc, a.blockId()), indexOf(doc, b.blockId()));
            if (cmp != 0) return cmp;
            return Integer.compare(cellIdx(a.previewElementRef()), cellIdx(b.previewElementRef()));
        });
        return List.copyOf(result);
    }

    private static int indexOf(WordParserSpikeDocument doc, String blockId) {
        var blks = doc.blocks();
        for (int i = 0; i < blks.size(); i++) if (blks.get(i).blockId().equals(blockId)) return i;
        return Integer.MAX_VALUE;
    }
    private static int cellIdx(String ref) {
        if (ref == null || !TABLE_CELL_REF.matcher(ref).matches()) return -1;
        var p = ref.split("/cell:"); return p.length < 2 ? -1 : Integer.parseInt(p[1]);
    }

    // ────────── READY ──────────

    private static PointEvidence readyEvidence(
            ReviewPointCode code, String role, List<PointEvidenceOccurrence> occs,
            List<String> distinctValues, WordParserSpikeDocument doc) {
        String cv = distinctValues.size() == 1 ? distinctValues.getFirst() : null;
        var first = occs.isEmpty() ? null : occs.getFirst();
        var summary = "一致性扫描 READY: " + (distinctValues.size() == 1 ? "值=" + distinctValues.getFirst() : distinctValues.size() + " 个不同值");
        return new PointEvidence(code, role, cv, EvidenceStatus.CONFIRMED,
                "NATIVE_WORD", "STRUCTURED", null,
                first != null ? first.blockId() : null, EvidenceConfidenceLevel.HIGH.name(), summary,
                null, null,
                List.of(new EvidenceSlotCoverage(keyOf(code), true, true, EvidenceSlotCoverageStatus.SATISFIED, null, true)),
                first != null ? first.sectionPath() : List.of(),
                first != null ? first.regionType() : null,
                first != null ? first.locationLevel() : null,
                first != null ? first.previewElementRef() : null,
                occs);
    }

    // ────────── Error helpers ──────────

    private static PointEvidence sysfail(ReviewPointCode c, String r, EvidenceSlotCoverageStatus css,
            String diag, NotConcludedReasonCode rc, String detail, String msg) {
        return new PointEvidence(c, r, null, EvidenceStatus.SYSTEM_FAILURE,
                "NATIVE_WORD", "STRUCTURED", null, null, EvidenceConfidenceLevel.HIGH.name(), msg,
                diag, rc, List.of(new EvidenceSlotCoverage(keyOf(c), true, true, css, diag, false)),
                List.of(), null, null, null, List.of());
    }
    /**
     * §3.4 priority 11: code/role/ledger attribution mismatch → AMBIGUOUS/ROLE_CONFLICT.
     */
    private static PointEvidence roleConflict(ReviewPointCode c, String r, String detail) {
        return new PointEvidence(c, r, null, EvidenceStatus.AMBIGUOUS,
                "NATIVE_WORD", "STRUCTURED", null, null, EvidenceConfidenceLevel.CONFLICTED.name(),
                "ROLE_CONFLICT",
                "SYS_ROLE_CONFLICT", NotConcludedReasonCode.EVIDENCE_AMBIGUOUS,
                List.of(new EvidenceSlotCoverage(keyOf(c), true, true, EvidenceSlotCoverageStatus.AMBIGUOUS, "SYS_ROLE_CONFLICT", false)),
                List.of(), null, null, null);
    }

    private static PointEvidence lineageFail(ReviewPointCode c, String r, String msg) {
        return sysfail(c, r, EvidenceSlotCoverageStatus.PARTIAL, "SYS_EVIDENCE_BUNDLE_INVALID",
                NotConcludedReasonCode.INTERNAL_RULE_ERROR, null, msg);
    }
    private static PointEvidence budgetExceeded(ReviewPointCode c, String r, String msg) {
        return sysfail(c, r, EvidenceSlotCoverageStatus.BUDGET_TRUNCATED, "SYS_EVIDENCE_BUDGET_EXCEEDED",
                NotConcludedReasonCode.MODEL_BUDGET_EXCEEDED, "BUDGET_TRUNCATED", msg);
    }
    private static PointEvidence ambigLow(ReviewPointCode c, String r, String msg) {
        return new PointEvidence(c, r, null, EvidenceStatus.AMBIGUOUS,
                "NATIVE_WORD", "STRUCTURED", null, null, EvidenceConfidenceLevel.LOW.name(), msg,
                "SYS_PARSE_LOW_CONFIDENCE", NotConcludedReasonCode.PARSE_LOW_CONFIDENCE,
                List.of(new EvidenceSlotCoverage(keyOf(c), true, true, EvidenceSlotCoverageStatus.LOW_CONFIDENCE, "SYS_PARSE_LOW_CONFIDENCE", false)),
                List.of(), null, null, null, List.of());
    }
    private static PointEvidence ambig(ReviewPointCode c, String r, String diag,
            NotConcludedReasonCode rc, String detail, String msg) {
        return new PointEvidence(c, r, null, EvidenceStatus.AMBIGUOUS,
                "NATIVE_WORD", "STRUCTURED", null, null, EvidenceConfidenceLevel.CONFLICTED.name(), msg,
                diag, rc, List.of(new EvidenceSlotCoverage(keyOf(c), true, true, EvidenceSlotCoverageStatus.AMBIGUOUS, diag, false)),
                List.of(), null, null, null, List.of());
    }
    private static PointEvidence missing(ReviewPointCode c, String r) {
        return new PointEvidence(c, r, null, EvidenceStatus.MISSING,
                "NATIVE_WORD", "STRUCTURED", null, null, EvidenceConfidenceLevel.UNKNOWN.name(),
                "完整扫描后零纳入 occurrence。", "SYS_INDEX_INCOMPLETE", NotConcludedReasonCode.EVIDENCE_NOT_FOUND,
                List.of(new EvidenceSlotCoverage(keyOf(c), true, true, EvidenceSlotCoverageStatus.MISSING, "SYS_INDEX_INCOMPLETE", false)),
                List.of(), null, null, null, List.of());
    }

    private static String keyOf(ReviewPointCode c) {
        return switch (c) {
            case PARTY_A_NAME_CONSISTENCY -> "party_a";
            case PARTY_B_NAME_CONSISTENCY -> "party_b";
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY -> "contract_total_amount";
            case TAX_AMOUNT_FORMULA_CONSISTENCY -> "tax_amount";
            case PREPAYMENT_RATIO_CONSISTENCY -> "prepayment_ratio";
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> "progress_payment_ratio";
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> "completion_payment_ratio";
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> "settlement_payment_ratio";
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> "warranty_retention_ratio";
        };
    }

    private static java.util.Optional<WordParserSpikeDocument.DocumentBlock> findBlock(WordParserSpikeDocument doc, String blockId) {
        return doc.blocks().stream().filter(b -> b.blockId().equals(blockId)).findFirst();
    }

    private record CanonicalEntry(EvidenceCandidate candidate, String canonicalValue) {}
}
