package com.cqcp.apiserver.reviewengine;

import com.cqcp.apiserver.wordparser.DocxWordParserSpike;
import com.cqcp.apiserver.wordparser.WordParserSpikeDocument;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class ParserBackedReviewInputPreparer {

    private static final String SOURCE_ORIGIN = "NATIVE_WORD";
    private static final String SOURCE_EXTRACTION_MODE = "STRUCTURED";
    private static final String CONTEXT_TYPE = "NORMAL";

    private static final Charset GBK = Charset.forName("GBK");

    static final List<Pattern> PARTY_A_BLOCK_PATTERNS = patternVariants(
            "^(?:发包方\\s*[（(]?甲方[）)]?|甲方（全称）|甲方)\\s*[:：]\\s*(.+)$");
    private static final List<Pattern> PARTY_B_BLOCK_PATTERNS = patternVariants(
            "^(?:承包方\\s*[（(]?乙方[）)]?|乙方（全称）|乙方)\\s*[:：]\\s*(.+)$");

    private static final List<String> TOTAL_AMOUNT_LABEL_HINTS = textVariants("合同固定总价", "合同暂定总价", "含税总价", "签约合同价", "材料/设备含税总价");
    private static final List<String> TAX_AMOUNT_LABEL_HINTS = textVariants("增值税税款", "税金");
    private static final List<String> PREPAYMENT_LABEL_HINTS = textVariants("预付款");
    private static final List<String> PROGRESS_PAYMENT_LABEL_HINTS = textVariants("进度款", "到货验收款");
    private static final List<String> COMPLETION_PAYMENT_LABEL_HINTS = textVariants("竣工款", "安装完工款");
    private static final List<String> SETTLEMENT_PAYMENT_LABEL_HINTS = textVariants("结算款");
    private static final List<String> WARRANTY_PAYMENT_LABEL_HINTS = textVariants("质保", "保函", "质保金", "质量保函");

    private static final List<Pattern> TOTAL_AMOUNT_PATTERNS = amountAfterLabelPatterns(
            "合同固定总价", "合同暂定总价", "签约合同价", "含税总价", "材料/设备含税总价");
    private static final List<Pattern> TAX_AMOUNT_PATTERNS = amountAfterLabelPatterns("增值税税款", "税金");
    private static final List<Pattern> PREPAYMENT_PATTERNS = patternsOf(
            patternVariants("无预付款"),
            patternVariants("预付款[^\\d]{0,60}(\\d{1,3}(?:\\.\\d+)?)\\s*%"));
    private static final List<Pattern> PROGRESS_PAYMENT_PATTERNS = patternVariants(
            "^(?:进度款|到货验收款)[：:].{0,240}?(\\d{1,3}(?:\\.\\d+)?)\\s*%");
    private static final List<Pattern> PROGRESS_PAYMENT_FALLBACK_PATTERNS = patternVariants(
            "形象进度产值的(\\d{1,3}(?:\\.\\d+)?)\\s*%");
    private static final List<Pattern> COMPLETION_PAYMENT_PATTERNS = patternsOf(
            patternVariants("^竣工款[：:].{0,240}?已完工程量的(\\d{1,3}(?:\\.\\d+)?)\\s*%"),
            patternVariants("^安装完工款[：:].{0,240}?(\\d{1,3}(?:\\.\\d+)?)\\s*%"));
    private static final List<Pattern> COMPLETION_PAYMENT_FALLBACK_PATTERNS = patternVariants(
            "已完工程量的(\\d{1,3}(?:\\.\\d+)?)\\s*%");
    private static final List<Pattern> SETTLEMENT_PAYMENT_PATTERNS = patternVariants(
            "^结算款[：:].{0,240}?结算金额的(\\d{1,3}(?:\\.\\d+)?)\\s*%");
    private static final List<Pattern> SETTLEMENT_PAYMENT_FALLBACK_PATTERNS = patternVariants(
            "结算金额的(\\d{1,3}(?:\\.\\d+)?)\\s*%");
    private static final List<Pattern> WARRANTY_PAYMENT_PATTERNS = patternsOf(
            patternVariants("^(?:质保(?:金)?|质量保函)[^\\d]{0,120}(\\d{1,3}(?:\\.\\d+)?)\\s*%"),
            patternVariants("(\\d{1,3}(?:\\.\\d+)?)\\s*%[^\\d]{0,40}(?:的《质量保函》|质量保函)"));
    private static final Pattern NUMERIC_VALUE_PATTERN = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern PERCENT_VALUE_PATTERN = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?)\\s*%");

    enum ProbeExecutionMode { LEGACY, CONSISTENCY_FULL_SCAN }

    @FunctionalInterface
    interface ProbeObserver {
        void observe(ReviewPointCode code, ProbeExecutionMode mode, String stage);
        static ProbeObserver NO_OP = (c, m, s) -> {};
    }

    private final DocxWordParserSpike parser;
    private final MinimalCandidateResolver candidateResolver;
    private final ProbeObserver probeObserver;

    ParserBackedReviewInputPreparer(DocxWordParserSpike parser) {
        this(parser, new MinimalCandidateResolver(), ProbeObserver.NO_OP);
    }

    ParserBackedReviewInputPreparer(DocxWordParserSpike parser, MinimalCandidateResolver candidateResolver) {
        this(parser, candidateResolver, ProbeObserver.NO_OP);
    }

    ParserBackedReviewInputPreparer(DocxWordParserSpike parser, MinimalCandidateResolver candidateResolver, ProbeObserver probeObserver) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.candidateResolver = Objects.requireNonNull(candidateResolver, "candidateResolver");
        this.probeObserver = Objects.requireNonNull(probeObserver, "probeObserver");
    }

    ParsedContractDocument parse(TaskExecutionDocumentReference documentReference) {
        Objects.requireNonNull(documentReference, "documentReference");
        try {
            return new ParsedContractDocument(documentReference, parser.parse(documentReference.docxPath()));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse docx: " + documentReference.docxPath(), exception);
        }
    }

    ContractEvidenceIndex index(ParsedContractDocument parsedDocument) {
        Objects.requireNonNull(parsedDocument, "parsedDocument");
        var searchableText = parsedDocument.document().blocks().stream()
                .map(WordParserSpikeDocument.DocumentBlock::normalizedText)
                .map(ParserBackedReviewInputPreparer::normalizeSearchText)
                .reduce("", (left, right) -> left + "\n" + right);
        return new ContractEvidenceIndex(parsedDocument, searchableText);
    }

    EvidenceBuildPlan plan(ContractEvidenceIndex evidenceIndex) {
        Objects.requireNonNull(evidenceIndex, "evidenceIndex");
        return new EvidenceBuildPlan(evidenceIndex);
    }

    // ────────── Legacy build ──────────
    ReviewEngineInput build(TaskExecutionRequest request, EvidenceBuildPlan plan) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(plan, "plan");

        var allBlocks = plan.evidenceIndex().parsedDocument().document().blocks();
        var paymentMethod = request.task().structuredFieldsSnapshot().get("paymentMethod");
        var evidences = new EnumMap<ReviewPointCode, PointEvidence>(ReviewPointCode.class);

        for (ReviewPointCode code : ReviewPointCode.values()) {
            var role = roleOf(code);
            // Only non-PREPAYMENT ratio use paymentClauseBlocks; text/amount always use all blocks
            List<WordParserSpikeDocument.DocumentBlock> blocks;
            boolean isRatio = code == ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY
                    || code == ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY
                    || code == ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY
                    || code == ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY;
            if (isRatio) {
                blocks = paymentClauseBlocks(request, plan);
            } else {
                blocks = allBlocks;
            }

            var candidates = probeAllForPoint(code, role, blocks, ProbeExecutionMode.LEGACY, paymentMethod);
            var resolution = candidateResolver.resolve(code, role, candidates);
            var evidence = resolution.confidenceLevel() == EvidenceConfidenceLevel.HIGH
                    ? confirmedEvidence(code, resolution.selectedCandidate().orElseThrow())
                    : unresolvedEvidence(code, role, candidates, resolution);
            evidences.put(code, evidence);
        }

        return new ReviewEngineInput(
                request.task().taskId(),
                request.execution().executionId(),
                plan.evidenceIndex().parsedDocument().documentReference().sampleId(),
                StructuredFieldSet.fromMap(request.task().structuredFieldsSnapshot()),
                evidences);
    }

    // ────────── C1 build ──────────
    ReviewEngineInput build(
            TaskExecutionRequest request,
            EvidenceBuildPlan plan,
            RuntimeRuleSetSnapshot runtimeRuleSetSnapshot) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(runtimeRuleSetSnapshot, "runtimeRuleSetSnapshot");

        if (!"v20260715.1".equals(runtimeRuleSetSnapshot.version()))
            throw new IllegalStateException("Unsupported rule set version: " + runtimeRuleSetSnapshot.version());
        if (runtimeRuleSetSnapshot.policyMap().size() != 9)
            throw new IllegalStateException("Snapshot must cover all 9 review points");
        for (ReviewPointCode code : ReviewPointCode.values()) {
            var pol = runtimeRuleSetSnapshot.policyMap().get(code);
            if (pol == null) throw new IllegalStateException("Missing policy for: " + code);
            if (!"CONSISTENCY_SET".equals(pol.cardinalityMode()))
                throw new IllegalStateException("Non-CONSISTENCY_SET policy for: " + code);
        }

        var document = plan.evidenceIndex().parsedDocument().document();
        var evidences = new EnumMap<ReviewPointCode, PointEvidence>(ReviewPointCode.class);

        var collector = new ConsistencyCandidateCollector(
                (code, role, blks) -> probeAllForPoint(code, role, blks, ProbeExecutionMode.CONSISTENCY_FULL_SCAN, null),
                defaultSemanticClassifiers());

        for (ReviewPointCode code : ReviewPointCode.values()) {
            var policy = runtimeRuleSetSnapshot.policyMap().get(code);
            var role = roleOf(code);
            var batch = collector.collect(code, role, document, policy);
            var evidence = ConsistencySetCollector.collect(code, role, batch, document, policy);
            evidences.put(code, evidence);
        }

        return new ReviewEngineInput(
                request.task().taskId(),
                request.execution().executionId(),
                plan.evidenceIndex().parsedDocument().documentReference().sampleId(),
                StructuredFieldSet.fromMap(request.task().structuredFieldsSnapshot()),
                evidences,
                runtimeRuleSetSnapshot);
    }

    private static List<ConsistencyCandidateCollector.SemanticClassifier> defaultSemanticClassifiers() {
        return List.of(
                new ConsistencyCandidateCollector.SemanticClassifier("CONTRACT_TITLE_NAME_MENTION",
                        block -> isContractTitleNameMention(block.text())),
                new ConsistencyCandidateCollector.SemanticClassifier("AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION",
                        block -> isAgreementPreambleContractNameMention(block.text())));
    }

    // ────────── Shared probe dispatcher ──────────
    List<EvidenceCandidate> probeAllForPoint(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            ProbeExecutionMode mode,
            String paymentMethod) {
        Objects.requireNonNull(reviewPointCode, "reviewPointCode");
        Objects.requireNonNull(candidateRole, "candidateRole");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(mode, "mode");

        return switch (reviewPointCode) {
            case PARTY_A_NAME_CONSISTENCY, PARTY_B_NAME_CONSISTENCY -> {
                var isPartyA = reviewPointCode == ReviewPointCode.PARTY_A_NAME_CONSISTENCY;
                var labelHints = isPartyA
                        ? textVariants("发包方", "甲方（全称）", "甲方")
                        : textVariants("承包方", "乙方（全称）", "乙方");
                var blockPatterns = isPartyA ? PARTY_A_BLOCK_PATTERNS : PARTY_B_BLOCK_PATTERNS;
                var candidates = new ArrayList<EvidenceCandidate>();
                probeObserver.observe(reviewPointCode, mode, "PATTERN");
                for (var block : blocks) {
                    boolean rls = labelHints.stream().anyMatch(block.text()::contains);
                    if (!rls) continue;
                    for (String line : splitLines(block.text())) {
                        String matched = null;
                        for (Pattern p : blockPatterns) {
                            var m = p.matcher(line);
                            if (m.find()) { matched = m.group(1); break; }
                        }
                        if (matched == null) continue;
                        var value = cleanPartyValue(matched);
                        if (!value.isBlank()) {
                            boolean vfs = isPartyNameValueValid(value);
                            boolean bas = block.blockId() != null && !block.blockId().isBlank();
                            candidates.add(candidateForBlock(reviewPointCode, candidateRole, value, block, rls, vfs, bas));
                        }
                    }
                }
                yield candidates;
            }
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY, TAX_AMOUNT_FORMULA_CONSISTENCY -> {
                var isTotal = reviewPointCode == ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY;
                probeObserver.observe(reviewPointCode, mode, "PATTERN");
                var labelHints = isTotal ? TOTAL_AMOUNT_LABEL_HINTS : TAX_AMOUNT_LABEL_HINTS;
                var patterns = isTotal ? TOTAL_AMOUNT_PATTERNS : TAX_AMOUNT_PATTERNS;
                var candidates = new ArrayList<>(collectPatternCandidates(
                        reviewPointCode, candidateRole, blocks, labelHints, patterns, true));
                if (candidates.isEmpty()) {
                    candidates.addAll(collectStructuredAmountTupleCandidates(
                            reviewPointCode, candidateRole, blocks, labelHints));
                }
                yield candidates;
            }
            case PREPAYMENT_RATIO_CONSISTENCY -> {
                var candidates = new LinkedHashSet<EvidenceCandidate>();
                probeObserver.observe(reviewPointCode, mode, "SEMANTIC");
                candidates.addAll(collectSemanticRatioCandidates(reviewPointCode, candidateRole, blocks, paymentMethod));
                probeObserver.observe(reviewPointCode, mode, "DIRECT");
                candidates.addAll(collectPatternCandidates(reviewPointCode, candidateRole, blocks,
                        PREPAYMENT_LABEL_HINTS, PREPAYMENT_PATTERNS, false));
                if (mode == ProbeExecutionMode.LEGACY) {
                    probeObserver.observe(reviewPointCode, mode, "WHOLE_TEXT");
                    candidates.addAll(collectWholeTextCandidates(reviewPointCode, candidateRole, blocks,
                            PREPAYMENT_LABEL_HINTS, List.of()));
                }
                if (candidates.isEmpty()) {
                    probeObserver.observe(reviewPointCode, mode, "ROLE");
                    candidates.addAll(collectRoleBlockPercentFallbackCandidates(
                            reviewPointCode, candidateRole, blocks, PREPAYMENT_LABEL_HINTS));
                }
                // PREPAYMENT never runs weak fallback regardless of mode
                yield new ArrayList<>(candidates);
            }
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY, COMPLETION_PAYMENT_RATIO_CONSISTENCY,
                 SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, WARRANTY_RETENTION_RATIO_CONSISTENCY -> {
                var candidates = new LinkedHashSet<EvidenceCandidate>();
                probeObserver.observe(reviewPointCode, mode, "SEMANTIC");
                candidates.addAll(collectSemanticRatioCandidates(reviewPointCode, candidateRole, blocks, paymentMethod));
                probeObserver.observe(reviewPointCode, mode, "DIRECT");
                candidates.addAll(collectPatternCandidates(reviewPointCode, candidateRole, blocks,
                        labelHintsFor(reviewPointCode), directPatternsFor(reviewPointCode), false));
                probeObserver.observe(reviewPointCode, mode, "WHOLE_TEXT");
                candidates.addAll(collectWholeTextCandidates(reviewPointCode, candidateRole, blocks,
                        labelHintsFor(reviewPointCode), fallbackPatternsFor(reviewPointCode)));
                if (candidates.isEmpty()) {
                    probeObserver.observe(reviewPointCode, mode, "ROLE");
                    candidates.addAll(collectRoleBlockPercentFallbackCandidates(
                            reviewPointCode, candidateRole, blocks, labelHintsFor(reviewPointCode)));
                }
                if (candidates.isEmpty() && mode == ProbeExecutionMode.LEGACY) {
                    probeObserver.observe(reviewPointCode, mode, "WEAK");
                    candidates.addAll(collectWeakPaymentClausePercentFallbackCandidates(
                            reviewPointCode, candidateRole, blocks));
                }
                yield new ArrayList<>(candidates);
            }
        };
    }

    // ────────── Label/pattern per point ──────────
    private static List<String> labelHintsFor(ReviewPointCode code) {
        return switch (code) {
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> PROGRESS_PAYMENT_LABEL_HINTS;
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> COMPLETION_PAYMENT_LABEL_HINTS;
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> SETTLEMENT_PAYMENT_LABEL_HINTS;
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> WARRANTY_PAYMENT_LABEL_HINTS;
            default -> List.of();
        };
    }

    private static List<Pattern> directPatternsFor(ReviewPointCode code) {
        return switch (code) {
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> PROGRESS_PAYMENT_PATTERNS;
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> COMPLETION_PAYMENT_PATTERNS;
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> SETTLEMENT_PAYMENT_PATTERNS;
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> WARRANTY_PAYMENT_PATTERNS;
            default -> List.of();
        };
    }

    private static List<Pattern> fallbackPatternsFor(ReviewPointCode code) {
        return switch (code) {
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> PROGRESS_PAYMENT_FALLBACK_PATTERNS;
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> COMPLETION_PAYMENT_FALLBACK_PATTERNS;
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> SETTLEMENT_PAYMENT_FALLBACK_PATTERNS;
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> List.of();
            default -> List.of();
        };
    }

    private static boolean isContractTitleNameMention(String text) {
        if (text == null || text.isBlank()) return false;
        if (hasPartyARoleAssignment(text)) return false;
        var stripped = text.replaceAll("\\s+", "");
        if (stripped.isEmpty()) return false;
        if (!stripped.contains("合同")) return false;
        if (!stripped.endsWith("合同")) return false;
        if (stripped.contains("。") || stripped.contains("；") || stripped.contains("：")) return false;
        return stripped.length() >= 2 && stripped.length() <= 160;
    }

    private static boolean isAgreementPreambleContractNameMention(String text) {
        if (text == null || text.isBlank()) return false;
        if (hasPartyARoleAssignment(text)) return false;
        return text.contains("甲乙双方") && text.contains("签署")
                && text.contains("《") && text.contains("》") && text.contains("合同");
    }

    private static boolean hasPartyARoleAssignment(String text) {
        for (var p : PARTY_A_BLOCK_PATTERNS) {
            if (p.matcher(text).find()) return true;
        }
        return false;
    }

    // ────────── Legacy evidence construction (kept from original) ──────────

    private PointEvidence confirmedEvidence(ReviewPointCode code, EvidenceCandidate candidate) {
        return new PointEvidence(code, candidate.candidateRole(), candidate.candidateValue(),
                EvidenceStatus.CONFIRMED, SOURCE_ORIGIN, SOURCE_EXTRACTION_MODE, CONTEXT_TYPE,
                candidate.blockId(), EvidenceConfidenceLevel.HIGH.name(),
                summarizeEvidence(code, candidate.blockText(), candidate.candidateValue()),
                null, null,
                List.of(new EvidenceSlotCoverage(slotKeyOf(code), true, true,
                        candidate.blockId() == null || candidate.blockId().isBlank()
                                ? EvidenceSlotCoverageStatus.PARTIAL : EvidenceSlotCoverageStatus.SATISFIED,
                        candidate.blockId() == null || candidate.blockId().isBlank()
                                ? "SYS_EVIDENCE_BUNDLE_INVALID" : null,
                        candidate.blockId() != null && !candidate.blockId().isBlank())),
                candidate.sectionPath(), candidate.regionType(),
                candidate.blockId() == null || candidate.blockId().isBlank() ? null : "BLOCK_LEVEL",
                candidate.previewElementRef());
    }

    private PointEvidence unresolvedEvidence(
            ReviewPointCode code, String role, List<EvidenceCandidate> candidates,
            CandidateResolutionResult resolution) {
        var status = resolution.confidenceLevel() == EvidenceConfidenceLevel.UNKNOWN
                ? EvidenceStatus.MISSING : EvidenceStatus.AMBIGUOUS;
        var blockId = resolution.selectedCandidate().map(EvidenceCandidate::blockId).orElse(null);
        var selected = resolution.selectedCandidate().orElse(null);
        return new PointEvidence(code, role,
                resolution.selectedCandidate().map(EvidenceCandidate::candidateValue).orElse(null),
                status, SOURCE_ORIGIN, SOURCE_EXTRACTION_MODE, CONTEXT_TYPE,
                blockId, resolution.confidenceLevel().name(),
                buildUnresolvedSummary(resolution.confidenceLevel(), resolution.selectedCandidate(), candidates),
                resolution.diagnosticCode(), resolution.notConcludedReason(),
                List.of(unresolvedSlotCoverage(code, resolution, blockId)),
                selected == null ? List.of() : selected.sectionPath(),
                selected == null ? null : selected.regionType(),
                blockId == null || blockId.isBlank() ? null : "BLOCK_LEVEL",
                selected == null ? null : selected.previewElementRef());
    }

    private EvidenceSlotCoverage unresolvedSlotCoverage(
            ReviewPointCode code, CandidateResolutionResult resolution, String blockId) {
        var cs = switch (resolution.confidenceLevel()) {
            case UNKNOWN -> EvidenceSlotCoverageStatus.MISSING;
            case CONFLICTED -> EvidenceSlotCoverageStatus.AMBIGUOUS;
            case MEDIUM, LOW -> EvidenceSlotCoverageStatus.LOW_CONFIDENCE;
            case HIGH -> EvidenceSlotCoverageStatus.SATISFIED;
        };
        return new EvidenceSlotCoverage(slotKeyOf(code), true, true, cs,
                resolution.diagnosticCode(), blockId != null && !blockId.isBlank());
    }

    // ────────── Existing shared probe methods ──────────

    List<EvidenceCandidate> collectPatternCandidates(
            ReviewPointCode code, String role,
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            List<String> labelHints, List<Pattern> patterns, boolean requireRls) {
        Set<EvidenceCandidate> result = new LinkedHashSet<>();
        for (var block : blocks) {
            var rls = labelHints.stream().anyMatch(block.text()::contains);
            if (requireRls && !rls) continue;
            for (Pattern pattern : patterns) {
                var m = pattern.matcher(block.text());
                while (m.find()) {
                    var v = m.groupCount() == 0 ? "0" : stripTrailingZeros(m.group(1));
                    if (v == null || v.isBlank()) continue;
                    var vfs = switch (code) {
                        case CONTRACT_TOTAL_AMOUNT_CONSISTENCY, TAX_AMOUNT_FORMULA_CONSISTENCY -> isAmountValueValid(v);
                        case PREPAYMENT_RATIO_CONSISTENCY, PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                             COMPLETION_PAYMENT_RATIO_CONSISTENCY, SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
                             WARRANTY_RETENTION_RATIO_CONSISTENCY -> isRatioValueValid(v);
                        default -> false;
                    };
                    result.add(candidateForMatch(code, role, v, block, rls, vfs, rls, m.start(), m.end()));
                }
            }
        }
        return List.copyOf(result);
    }

    private List<EvidenceCandidate> collectWholeTextCandidates(
            ReviewPointCode code, String role,
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            List<String> labelHints, List<Pattern> patterns) {
        if (patterns.isEmpty()) return List.of();
        Set<EvidenceCandidate> result = new LinkedHashSet<>();
        var fullText = joinSearchableText(blocks);
        for (Pattern pattern : patterns) {
            var m = pattern.matcher(fullText);
            while (m.find()) {
                var v = m.groupCount() == 0 ? "0" : stripTrailingZeros(m.group(1));
                if (v == null || v.isBlank()) continue;
                var block = findBlock(blocks, labelHints, v);
                if (block.isEmpty()) continue;
                result.add(candidateForBlock(code, role, v, block.orElseThrow(), true, true, true));
            }
        }
        return List.copyOf(result);
    }

    private List<EvidenceCandidate> collectSemanticRatioCandidates(
            ReviewPointCode code, String role,
            List<WordParserSpikeDocument.DocumentBlock> blocks, String paymentMethod) {
        Set<EvidenceCandidate> result = new LinkedHashSet<>();
        for (var block : blocks) {
            var text = block.text();
            if (!isRatioRoleBlock(code, text, paymentMethod)) continue;
            var m = PERCENT_VALUE_PATTERN.matcher(text);
            while (m.find()) {
                var v = stripTrailingZeros(m.group(1));
                if (v == null || v.isBlank()) continue;
                if (!isExpectedRatioValue(code, v, text)) continue;
                result.add(candidateForMatch(code, role, v, block, true, true, true, m.start(), m.end()));
            }
        }
        return List.copyOf(result);
    }

    private List<EvidenceCandidate> collectStructuredAmountTupleCandidates(
            ReviewPointCode code, String role,
            List<WordParserSpikeDocument.DocumentBlock> blocks, List<String> labelHints) {
        var matched = blocks.stream().filter(b -> labelHints.stream().anyMatch(b.text()::contains)).toList();
        var selected = selectAmountTupleBlock(matched).or(() -> selectAmountTupleBlock(blocks));
        if (selected.isEmpty()) return List.of();
        var tuple = extractAmountTuple(selected.orElseThrow());
        if (tuple.isEmpty()) return List.of();
        var sv = switch (code) {
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY -> tuple.orElseThrow().getFirst();
            case TAX_AMOUNT_FORMULA_CONSISTENCY -> tuple.orElseThrow().get(tuple.orElseThrow().size() - 1);
            default -> null;
        };
        if (sv == null || sv.isBlank()) return List.of();
        return List.of(candidateForBlock(code, role, sv, selected.orElseThrow(), true, true, true));
    }

    private List<EvidenceCandidate> collectRoleBlockPercentFallbackCandidates(
            ReviewPointCode code, String role,
            List<WordParserSpikeDocument.DocumentBlock> blocks, List<String> labelHints) {
        for (var block : blocks) {
            if (labelHints.stream().noneMatch(block.text()::contains)) continue;
            var m = PERCENT_VALUE_PATTERN.matcher(block.text());
            var percents = new ArrayList<String>();
            while (m.find()) percents.add(stripTrailingZeros(m.group(1)));
            if (percents.isEmpty()) continue;
            var sv = selectFallbackPercent(code, percents, block.text()).orElse(null);
            if (sv == null || sv.isBlank()) continue;
            return List.of(candidateForBlock(code, role, sv, block, true, true, false));
        }
        return List.of();
    }

    private List<EvidenceCandidate> collectWeakPaymentClausePercentFallbackCandidates(
            ReviewPointCode code, String role,
            List<WordParserSpikeDocument.DocumentBlock> blocks) {
        for (var block : blocks) {
            var m = PERCENT_VALUE_PATTERN.matcher(block.text());
            var percents = new ArrayList<String>();
            while (m.find()) percents.add(stripTrailingZeros(m.group(1)));
            if (percents.isEmpty()) continue;
            var sv = selectFallbackPercent(code, percents, block.text()).orElse(null);
            if (sv == null || sv.isBlank()) continue;
            return List.of(candidateForBlock(code, role, sv, block, false, true, false));
        }
        return List.of();
    }

    // ────────── Legacy ratio helpers ──────────

    private boolean isRatioRoleBlock(ReviewPointCode code, String text, String paymentMethod) {
        return switch (code) {
            case PREPAYMENT_RATIO_CONSISTENCY -> containsAny(text, "预付款", "无预付款");
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> isProgressRatioBlock(text, paymentMethod);
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> text.startsWith("竣工款") || text.startsWith("安装完工款")
                    || containsAny(text, "已完工程量", "完工并验收合格");
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> text.startsWith("结算款")
                    || containsAny(text, "支付至结算金额", "支付至结算总价");
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> text.startsWith("质保金")
                    || containsAny(text, "质量保函", "质保函");
            default -> false;
        };
    }

    private boolean isProgressRatioBlock(String text, String paymentMethod) {
        if (containsAny(text, "到货总价", "到货验收款")) return true;
        if (text.startsWith("进度款")) return true;
        if (text.contains("形象进度产值")) return !"MONTHLY".equals(paymentMethod) || !text.startsWith("节点");
        return false;
    }

    private boolean isExpectedRatioValue(ReviewPointCode code, String v, String text) {
        return switch (code) {
            case PREPAYMENT_RATIO_CONSISTENCY -> !"100".equals(v) || containsAny(text, "预付款");
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> !"100".equals(v);
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> !"100".equals(v);
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> !"100".equals(v) || containsAny(text, "结算金额相等");
            default -> true;
        };
    }

    private Optional<String> selectFallbackPercent(
            ReviewPointCode code, List<String> percents, String text) {
        var expected = percents.stream().filter(p -> isExpectedRatioValue(code, p, text)).toList();
        if (expected.isEmpty()) return Optional.empty();
        return switch (code) {
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> Optional.of(expected.getFirst());
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> Optional.of(expected.getLast());
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> Optional.of(expected.getLast());
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> Optional.of(expected.getFirst());
            default -> Optional.empty();
        };
    }

    // ────────── Shared candidate builders ──────────

    private EvidenceCandidate candidateForBlock(
            ReviewPointCode code, String role, String value,
            WordParserSpikeDocument.DocumentBlock block,
            boolean rls, boolean vfs, boolean bas) {
        return candidateForMatch(code, role, value, block, rls, vfs, bas, -1, -1);
    }

    private EvidenceCandidate candidateForMatch(
            ReviewPointCode code, String role, String value,
            WordParserSpikeDocument.DocumentBlock block,
            boolean rls, boolean vfs, boolean bas, int start, int end) {
        Integer ci = null;
        if (start >= 0 && end >= start) {
            ci = block.tableCells().stream()
                    .filter(cell -> start >= cell.startOffset() && end <= cell.endOffset())
                    .map(WordParserSpikeDocument.TableCellSpan::cellIndex)
                    .findFirst().orElse(null);
        }
        return new EvidenceCandidate(code, role, value, block.blockId(), block.text(), rls, vfs, bas,
                block.sectionPath(), block.regionType().name(), block.tableId(), block.rowIndex(), ci,
                previewElementRef(block.tableId(), block.rowIndex(), ci),
                block.contextType().name(), block.sourceOrigin().name(),
                block.sourceExtractionMode().name(), block.blockConfidence().name(),
                block.previewAnchorLevel().name(), List.of());
    }

    private String previewElementRef(String tableId, Integer rowIndex, Integer cellIndex) {
        if (tableId == null || tableId.isBlank() || rowIndex == null) return null;
        var ref = "table:" + tableId + "/row:" + rowIndex;
        return cellIndex == null ? ref : ref + "/cell:" + cellIndex;
    }

    // ────────── Helpers ──────────

    private List<WordParserSpikeDocument.DocumentBlock> paymentClauseBlocks(
            TaskExecutionRequest request, EvidenceBuildPlan plan) {
        if (request == null) return plan.evidenceIndex().parsedDocument().document().blocks();
        var blocks = plan.evidenceIndex().parsedDocument().document().blocks();
        var pm = request.task().structuredFieldsSnapshot().get("paymentMethod");
        if (pm == null || pm.isBlank()) return blocks;
        return switch (pm) {
            case "MONTHLY" -> sliceBlocks(blocks,
                    b -> containsAnyVariant(b.text(), "月度付款", "按月度付款", "按月形象进度付款"),
                    b -> containsAnyVariant(b.text(), "履约保函的说明", "工程保修及质保金支付"));
            case "MILESTONE" -> sliceBlocks(blocks,
                    b -> containsAnyVariant(b.text(), "节点付款", "按节点付款"),
                    b -> containsAnyVariant(b.text(), "履约保函的说明", "工程保修及质保金支付"));
            default -> blocks;
        };
    }

    private List<WordParserSpikeDocument.DocumentBlock> sliceBlocks(
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            java.util.function.Predicate<WordParserSpikeDocument.DocumentBlock> start,
            java.util.function.Predicate<WordParserSpikeDocument.DocumentBlock> end) {
        int si = -1;
        for (int i = 0; i < blocks.size(); i++) { if (start.test(blocks.get(i))) { si = i; break; } }
        if (si < 0 || si >= blocks.size()) return blocks;
        int ei = blocks.size();
        for (int i = si; i < blocks.size(); i++) { if (end.test(blocks.get(i))) { ei = i; break; } }
        return blocks.subList(si, ei);
    }

    private Optional<WordParserSpikeDocument.DocumentBlock> findBlock(
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            List<String> labelHints, String value) {
        var nv = normalizeSearchText(value);
        for (var block : blocks) {
            if (labelHints.stream().anyMatch(block.text()::contains)
                    && normalizeSearchText(block.normalizedText()).contains(nv))
                return Optional.of(block);
        }
        for (var block : blocks) {
            if (normalizeSearchText(block.normalizedText()).contains(nv)) return Optional.of(block);
        }
        return Optional.empty();
    }

    private String joinSearchableText(List<WordParserSpikeDocument.DocumentBlock> blocks) {
        return blocks.stream()
                .map(WordParserSpikeDocument.DocumentBlock::normalizedText)
                .map(ParserBackedReviewInputPreparer::normalizeSearchText)
                .reduce("", (l, r) -> l + "\n" + r);
    }

    private String summarizeEvidence(ReviewPointCode code, String blockText, String candidateValue) {
        var n = blockText.replaceAll("\\s+", " ").trim();
        if (n.length() > 120) n = n.substring(0, 120);
        return "审核标签=" + displayLabelOf(code) + "；命中值=" + candidateValue + "；证据片段：" + n;
    }

    private Optional<WordParserSpikeDocument.DocumentBlock> selectAmountTupleBlock(
            List<WordParserSpikeDocument.DocumentBlock> blocks) {
        WordParserSpikeDocument.DocumentBlock best = null;
        double bestFirst = -1;
        for (var block : blocks) {
            var tuple = extractAmountTuple(block);
            if (tuple.isEmpty()) continue;
            var first = Double.parseDouble(tuple.orElseThrow().getFirst());
            if (first > bestFirst) { bestFirst = first; best = block; }
        }
        return Optional.ofNullable(best);
    }

    private Optional<List<String>> extractAmountTuple(WordParserSpikeDocument.DocumentBlock block) {
        var m = NUMERIC_VALUE_PATTERN.matcher(block.text());
        var amounts = new ArrayList<String>();
        boolean sawDecimal = false;
        while (m.find()) {
            var raw = m.group(1);
            amounts.add(stripTrailingZeros(raw));
            if (raw.contains(".") || raw.contains(",")) sawDecimal = true;
        }
        return amounts.size() >= 3 && sawDecimal ? Optional.of(amounts) : Optional.empty();
    }

    private PointEvidence resolveFromCandidates(
            ReviewPointCode code, String role, List<EvidenceCandidate> candidates) {
        var r = candidateResolver.resolve(code, role, candidates);
        if (r.confidenceLevel() == EvidenceConfidenceLevel.HIGH)
            return confirmedEvidence(code, r.selectedCandidate().orElseThrow());
        return unresolvedEvidence(code, role, candidates, r);
    }

    private String buildUnresolvedSummary(
            EvidenceConfidenceLevel cl, Optional<EvidenceCandidate> selected, List<EvidenceCandidate> candidates) {
        return switch (cl) {
            case UNKNOWN -> "未从 parser 输出中定位到最小证据。";
            case LOW -> "候选已提取，但缺少足够归属信号；候选值=" + selected.map(EvidenceCandidate::candidateValue).orElse("N/A");
            case MEDIUM -> "候选命中了部分归属信号，但仍不足以进入确定性裁判；候选值=" + selected.map(EvidenceCandidate::candidateValue).orElse("N/A");
            case CONFLICTED -> "同一证据块内存在多个候选竞争同一角色：" + candidates.stream()
                    .map(c -> c.blockId() + "=" + c.candidateValue()).distinct()
                    .reduce((l, r) -> l + ", " + r).orElse("N/A");
            case HIGH -> throw new IllegalStateException("HIGH should not use unresolved summary");
        };
    }

    private static String roleOf(ReviewPointCode code) {
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

    private String displayLabelOf(ReviewPointCode code) {
        return switch (code) {
            case PARTY_A_NAME_CONSISTENCY -> "甲方";
            case PARTY_B_NAME_CONSISTENCY -> "乙方";
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY -> "合同总金额";
            case TAX_AMOUNT_FORMULA_CONSISTENCY -> "税额";
            case PREPAYMENT_RATIO_CONSISTENCY -> "预付款";
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> "进度款";
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> "竣工款";
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> "结算款";
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> "质保";
        };
    }

    // ────────── Static helpers ──────────

    private String cleanPartyValue(String value) {
        var cleaned = value.replace('|', ' ').replaceAll("\\s+", " ").trim();
        if (cleaned.endsWith("。")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        for (String marker : List.of("地址", "法定代表人", "授权代表", "电话", "开户银行", "账号", "乙方", "甲方", "（公章）", "(公章)", "日期")) {
            var idx = cleaned.indexOf(marker);
            if (idx > 0) cleaned = cleaned.substring(0, idx).trim();
        }
        return cleaned;
    }

    private static boolean isPartyNameValueValid(String value) {
        if (value == null || value.isBlank()) return false;
        boolean hasHan = value.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
        if (hasHan) return true;
        long letterCount = value.codePoints().filter(Character::isLetter).count();
        long digitCount = value.codePoints().filter(Character::isDigit).count();
        return letterCount >= 1 && (letterCount + digitCount) >= 2;
    }

    private static boolean isAmountValueValid(String value) {
        try { var d = Double.parseDouble(value); return d > 0 && Double.isFinite(d); } catch (NumberFormatException e) { return false; }
    }

    private static boolean isRatioValueValid(String value) {
        try { var d = Double.parseDouble(value); return d >= 0 && d <= 100 && Double.isFinite(d); } catch (NumberFormatException e) { return false; }
    }

    private List<String> splitLines(String text) {
        return text == null ? List.of()
                : text.lines().map(l -> l.replace(' ', ' ').trim()).filter(l -> !l.isBlank()).toList();
    }

    private String stripTrailingZeros(String raw) {
        var n = raw.replace(",", "").trim();
        if (!n.contains(".")) return n;
        n = n.replaceAll("0+$", "").replaceAll("\\.$", "");
        return n.isBlank() ? raw : n;
    }

    private static String normalizeSearchText(String text) {
        return text == null ? "" : text.replace(' ', ' ').replaceAll("\\s+", "")
                .replace(",", "").replace("。", "").replace("（", "").replace("）", "")
                .replace(":", "").replace("：", "").toLowerCase(Locale.ROOT);
    }

    private static boolean containsAnyVariant(String text, String... values) {
        return textVariants(values).stream().anyMatch(text::contains);
    }

    private static boolean containsAny(String text, String... values) {
        for (String v : values) { if (text.contains(v)) return true; }
        return false;
    }

    private static List<String> textVariants(String... values) {
        var result = new ArrayList<String>();
        for (String v : values) { result.add(v); result.add(toMojibake(v)); }
        return result.stream().distinct().toList();
    }

    private static List<Pattern> patternVariants(String regex) {
        return List.of(Pattern.compile(regex));
    }

    private static List<Pattern> amountAfterLabelPatterns(String... labels) {
        var result = new ArrayList<Pattern>();
        for (String label : labels) {
            for (String variant : textVariants(label)) {
                result.add(Pattern.compile(Pattern.quote(variant) + "[^\\d]{0,40}(\\d[\\d,]*(?:\\.\\d+)?)\\s*元"));
            }
        }
        return List.copyOf(result);
    }

    @SafeVarargs
    private static List<Pattern> patternsOf(List<Pattern>... groups) {
        var result = new ArrayList<Pattern>();
        for (var g : groups) result.addAll(g);
        return List.copyOf(result);
    }

    private static String toMojibake(String value) {
        return new String(value.getBytes(StandardCharsets.UTF_8), GBK);
    }

    private static String slotKeyOf(ReviewPointCode code) {
        return roleOf(code).toLowerCase(Locale.ROOT);
    }
}

record ParsedContractDocument(TaskExecutionDocumentReference documentReference, WordParserSpikeDocument document) {}
record ContractEvidenceIndex(ParsedContractDocument parsedDocument, String searchableText) {}
record EvidenceBuildPlan(ContractEvidenceIndex evidenceIndex) {}
