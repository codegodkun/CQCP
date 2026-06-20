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

    private static final List<Pattern> PARTY_A_BLOCK_PATTERNS = patternVariants(
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
            "合同固定总价",
            "合同暂定总价",
            "签约合同价",
            "含税总价",
            "材料/设备含税总价");
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

    private final DocxWordParserSpike parser;
    private final MinimalCandidateResolver candidateResolver;

    ParserBackedReviewInputPreparer(DocxWordParserSpike parser) {
        this(parser, new MinimalCandidateResolver());
    }

    ParserBackedReviewInputPreparer(DocxWordParserSpike parser, MinimalCandidateResolver candidateResolver) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.candidateResolver = Objects.requireNonNull(candidateResolver, "candidateResolver");
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

    ReviewEngineInput build(TaskExecutionRequest request, EvidenceBuildPlan plan) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(plan, "plan");

        var blocks = plan.evidenceIndex().parsedDocument().document().blocks();
        var evidences = new EnumMap<ReviewPointCode, PointEvidence>(ReviewPointCode.class);
        evidences.put(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, resolvePartyA(plan));
        evidences.put(ReviewPointCode.PARTY_B_NAME_CONSISTENCY, resolvePartyB(plan));
        evidences.put(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, resolveNumericEvidence(
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
                roleOf(ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY),
                blocks,
                TOTAL_AMOUNT_LABEL_HINTS,
                TOTAL_AMOUNT_PATTERNS));
        evidences.put(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, resolveNumericEvidence(
                ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY,
                roleOf(ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY),
                blocks,
                TAX_AMOUNT_LABEL_HINTS,
                TAX_AMOUNT_PATTERNS));
        evidences.put(ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, resolveRatioEvidence(
                ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY,
                request,
                plan,
                PREPAYMENT_LABEL_HINTS,
                PREPAYMENT_PATTERNS,
                List.of()));
        evidences.put(ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, resolveRatioEvidence(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                request,
                plan,
                PROGRESS_PAYMENT_LABEL_HINTS,
                PROGRESS_PAYMENT_PATTERNS,
                PROGRESS_PAYMENT_FALLBACK_PATTERNS));
        evidences.put(ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, resolveRatioEvidence(
                ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY,
                request,
                plan,
                COMPLETION_PAYMENT_LABEL_HINTS,
                COMPLETION_PAYMENT_PATTERNS,
                COMPLETION_PAYMENT_FALLBACK_PATTERNS));
        evidences.put(ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, resolveRatioEvidence(
                ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
                request,
                plan,
                SETTLEMENT_PAYMENT_LABEL_HINTS,
                SETTLEMENT_PAYMENT_PATTERNS,
                SETTLEMENT_PAYMENT_FALLBACK_PATTERNS));
        evidences.put(ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, resolveRatioEvidence(
                ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY,
                request,
                plan,
                WARRANTY_PAYMENT_LABEL_HINTS,
                WARRANTY_PAYMENT_PATTERNS,
                List.of()));

        return new ReviewEngineInput(
                request.task().taskId(),
                request.execution().executionId(),
                plan.evidenceIndex().parsedDocument().documentReference().sampleId(),
                StructuredFieldSet.fromMap(request.task().structuredFieldsSnapshot()),
                evidences);
    }

    private PointEvidence resolvePartyA(EvidenceBuildPlan plan) {
        return resolveTextEvidence(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                "PARTY_A",
                textVariants("发包方", "甲方（全称）", "甲方"),
                PARTY_A_BLOCK_PATTERNS,
                plan);
    }

    private PointEvidence resolvePartyB(EvidenceBuildPlan plan) {
        return resolveTextEvidence(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY,
                "PARTY_B",
                textVariants("承包方", "乙方（全称）", "乙方"),
                PARTY_B_BLOCK_PATTERNS,
                plan);
    }

    private PointEvidence resolveTextEvidence(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            List<String> labelHints,
            List<Pattern> blockPatterns,
            EvidenceBuildPlan plan) {
        var candidates = new ArrayList<EvidenceCandidate>();
        for (WordParserSpikeDocument.DocumentBlock block : plan.evidenceIndex().parsedDocument().document().blocks()) {
            if (labelHints.stream().noneMatch(block.text()::contains)) {
                continue;
            }
            for (String line : splitLines(block.text())) {
                String matchedValue = null;
                for (Pattern blockPattern : blockPatterns) {
                    var matcher = blockPattern.matcher(line);
                    if (matcher.find()) {
                        matchedValue = matcher.group(1);
                        break;
                    }
                }
                if (matchedValue == null) {
                    continue;
                }
                var value = cleanPartyValue(matchedValue);
                if (!value.isBlank()) {
                    candidates.add(new EvidenceCandidate(
                            reviewPointCode,
                            candidateRole,
                            value,
                            block.blockId(),
                            block.text(),
                            true,
                            true,
                            true));
                }
            }
        }
        return resolveFromCandidates(reviewPointCode, candidateRole, candidates);
    }

    private PointEvidence resolveNumericEvidence(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            List<String> labelHints,
            List<Pattern> patterns) {
        var candidates = new ArrayList<>(collectPatternCandidates(
                reviewPointCode,
                candidateRole,
                blocks,
                labelHints,
                patterns,
                true));
        if (candidates.isEmpty()) {
            candidates.addAll(collectStructuredAmountTupleCandidates(
                    reviewPointCode,
                    candidateRole,
                    blocks,
                    labelHints));
        }
        return resolveFromCandidates(
                reviewPointCode,
                candidateRole,
                candidates);
    }

    private PointEvidence resolveRatioEvidence(
            ReviewPointCode reviewPointCode,
            TaskExecutionRequest request,
            EvidenceBuildPlan plan,
            List<String> labelHints,
            List<Pattern> directPatterns,
            List<Pattern> fallbackPatterns) {
        var paymentMethod = request.task().structuredFieldsSnapshot().get("paymentMethod");
        var blocks = reviewPointCode == ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY
                ? plan.evidenceIndex().parsedDocument().document().blocks()
                : paymentClauseBlocks(request, plan);
        var semanticCandidates = collectSemanticRatioCandidates(
                reviewPointCode,
                roleOf(reviewPointCode),
                blocks,
                paymentMethod);
        if (!semanticCandidates.isEmpty() && reviewPointCode != ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY) {
            return resolveFromCandidates(reviewPointCode, roleOf(reviewPointCode), semanticCandidates);
        }

        var candidates = new ArrayList<EvidenceCandidate>();
        candidates.addAll(semanticCandidates);
        candidates.addAll(collectPatternCandidates(
                reviewPointCode,
                roleOf(reviewPointCode),
                blocks,
                labelHints,
                directPatterns,
                false));
        candidates.addAll(collectWholeTextCandidates(
                reviewPointCode,
                roleOf(reviewPointCode),
                blocks,
                labelHints,
                fallbackPatterns));
        if (candidates.isEmpty()) {
            candidates.addAll(collectRoleBlockPercentFallbackCandidates(
                    reviewPointCode,
                    roleOf(reviewPointCode),
                    blocks,
                    labelHints));
        }
        if (candidates.isEmpty() && reviewPointCode != ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY) {
            candidates.addAll(collectWeakPaymentClausePercentFallbackCandidates(
                    reviewPointCode,
                    roleOf(reviewPointCode),
                    blocks));
        }
        return resolveFromCandidates(reviewPointCode, roleOf(reviewPointCode), candidates);
    }

    private List<EvidenceCandidate> collectSemanticRatioCandidates(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            String paymentMethod) {
        Set<EvidenceCandidate> result = new LinkedHashSet<>();
        for (WordParserSpikeDocument.DocumentBlock block : blocks) {
            var text = block.text();
            if (!isRatioRoleBlock(reviewPointCode, text, paymentMethod)) {
                continue;
            }

            var matcher = PERCENT_VALUE_PATTERN.matcher(text);
            while (matcher.find()) {
                var candidateValue = stripTrailingZeros(matcher.group(1));
                if (candidateValue == null || candidateValue.isBlank()) {
                    continue;
                }
                if (!isExpectedRatioValue(reviewPointCode, candidateValue, text)) {
                    continue;
                }
                result.add(new EvidenceCandidate(
                        reviewPointCode,
                        candidateRole,
                        candidateValue,
                        block.blockId(),
                        block.text(),
                        true,
                        true,
                        true));
            }
        }
        return List.copyOf(result);
    }

    private boolean isRatioRoleBlock(ReviewPointCode reviewPointCode, String text, String paymentMethod) {
        return switch (reviewPointCode) {
            case PREPAYMENT_RATIO_CONSISTENCY -> containsAny(text, "预付款", "无预付款");
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> isProgressRatioBlock(text, paymentMethod);
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> text.startsWith("竣工款")
                    || text.startsWith("安装完工款")
                    || containsAny(text, "已完工程量", "完工并验收合格");
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> text.startsWith("结算款")
                    || containsAny(text, "支付至结算金额", "支付至结算总价");
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> text.startsWith("质保金")
                    || containsAny(text, "质量保函", "质保函");
            default -> false;
        };
    }

    private boolean isProgressRatioBlock(String text, String paymentMethod) {
        if (containsAny(text, "到货总价", "到货验收款")) {
            return true;
        }
        if (text.startsWith("进度款")) {
            return true;
        }
        if (text.contains("形象进度产值")) {
            return !"MONTHLY".equals(paymentMethod) || !text.startsWith("节点");
        }
        return false;
    }

    private boolean isExpectedRatioValue(ReviewPointCode reviewPointCode, String candidateValue, String text) {
        return switch (reviewPointCode) {
            case PREPAYMENT_RATIO_CONSISTENCY -> !"100".equals(candidateValue) || containsAny(text, "预付款");
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> !"100".equals(candidateValue);
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> !"100".equals(candidateValue);
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> !"100".equals(candidateValue)
                    || containsAny(text, "结算金额相等");
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> true;
            default -> true;
        };
    }

    private List<EvidenceCandidate> collectPatternCandidates(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            List<String> labelHints,
            List<Pattern> patterns,
            boolean requireRoleLabelSignal) {
        Set<EvidenceCandidate> result = new LinkedHashSet<>();
        for (WordParserSpikeDocument.DocumentBlock block : blocks) {
            var roleLabelSignal = labelHints.stream().anyMatch(block.text()::contains);
            if (requireRoleLabelSignal && !roleLabelSignal) {
                continue;
            }
            for (Pattern pattern : patterns) {
                var matcher = pattern.matcher(block.text());
                while (matcher.find()) {
                    var candidateValue = matcher.groupCount() == 0 ? "0" : stripTrailingZeros(matcher.group(1));
                    if (candidateValue == null || candidateValue.isBlank()) {
                        continue;
                    }
                    result.add(new EvidenceCandidate(
                            reviewPointCode,
                            candidateRole,
                            candidateValue,
                            block.blockId(),
                            block.text(),
                            roleLabelSignal,
                            true,
                            roleLabelSignal));
                }
            }
        }
        return List.copyOf(result);
    }

    private List<EvidenceCandidate> collectWholeTextCandidates(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            List<String> labelHints,
            List<Pattern> patterns) {
        if (patterns.isEmpty()) {
            return List.of();
        }

        Set<EvidenceCandidate> result = new LinkedHashSet<>();
        var searchableText = joinSearchableText(blocks);
        for (Pattern pattern : patterns) {
            var matcher = pattern.matcher(searchableText);
            while (matcher.find()) {
                var candidateValue = matcher.groupCount() == 0 ? "0" : stripTrailingZeros(matcher.group(1));
                if (candidateValue == null || candidateValue.isBlank()) {
                    continue;
                }
                var block = findBlock(blocks, labelHints, candidateValue);
                if (block.isEmpty()) {
                    continue;
                }
                var matchedBlock = block.orElseThrow();
                result.add(new EvidenceCandidate(
                        reviewPointCode,
                        candidateRole,
                        candidateValue,
                        matchedBlock.blockId(),
                        matchedBlock.text(),
                        true,
                        true,
                        true));
            }
        }
        return List.copyOf(result);
    }

    private List<EvidenceCandidate> collectStructuredAmountTupleCandidates(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            List<String> labelHints) {
        var labelMatched = blocks.stream()
                .filter(block -> labelHints.stream().anyMatch(block.text()::contains))
                .toList();

        var selectedBlock = selectAmountTupleBlock(labelMatched)
                .or(() -> selectAmountTupleBlock(blocks));
        if (selectedBlock.isEmpty()) {
            return List.of();
        }

        var amountTuple = extractAmountTuple(selectedBlock.orElseThrow()).orElseThrow();
        var selectedValue = switch (reviewPointCode) {
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY -> amountTuple.getFirst();
            case TAX_AMOUNT_FORMULA_CONSISTENCY -> amountTuple.getLast();
            default -> null;
        };
        if (selectedValue == null || selectedValue.isBlank()) {
            return List.of();
        }

        return List.of(new EvidenceCandidate(
                reviewPointCode,
                candidateRole,
                selectedValue,
                selectedBlock.orElseThrow().blockId(),
                selectedBlock.orElseThrow().text(),
                true,
                true,
                true));
    }

    private List<EvidenceCandidate> collectRoleBlockPercentFallbackCandidates(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            List<String> labelHints) {
        for (WordParserSpikeDocument.DocumentBlock block : blocks) {
            if (labelHints.stream().noneMatch(block.text()::contains)) {
                continue;
            }
            var matcher = PERCENT_VALUE_PATTERN.matcher(block.text());
            var percents = new ArrayList<String>();
            while (matcher.find()) {
                percents.add(stripTrailingZeros(matcher.group(1)));
            }
            if (percents.isEmpty()) {
                continue;
            }

            var selectedValue = selectFallbackPercent(reviewPointCode, percents, block.text()).orElse(null);
            if (selectedValue == null || selectedValue.isBlank()) {
                continue;
            }

            return List.of(new EvidenceCandidate(
                    reviewPointCode,
                    candidateRole,
                    selectedValue,
                    block.blockId(),
                    block.text(),
                    true,
                    true,
                    false));
        }
        return List.of();
    }

    private List<EvidenceCandidate> collectWeakPaymentClausePercentFallbackCandidates(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            List<WordParserSpikeDocument.DocumentBlock> blocks) {
        for (WordParserSpikeDocument.DocumentBlock block : blocks) {
            var matcher = PERCENT_VALUE_PATTERN.matcher(block.text());
            var percents = new ArrayList<String>();
            while (matcher.find()) {
                percents.add(stripTrailingZeros(matcher.group(1)));
            }
            if (percents.isEmpty()) {
                continue;
            }

            var selectedValue = selectFallbackPercent(reviewPointCode, percents, block.text()).orElse(null);
            if (selectedValue == null || selectedValue.isBlank()) {
                continue;
            }

            return List.of(new EvidenceCandidate(
                    reviewPointCode,
                    candidateRole,
                    selectedValue,
                    block.blockId(),
                    block.text(),
                    false,
                    true,
                    false));
        }
        return List.of();
    }

    private Optional<String> selectFallbackPercent(
            ReviewPointCode reviewPointCode,
            List<String> percents,
            String text) {
        var expectedPercents = percents.stream()
                .filter(percent -> isExpectedRatioValue(reviewPointCode, percent, text))
                .toList();
        if (expectedPercents.isEmpty()) {
            return Optional.empty();
        }

        return switch (reviewPointCode) {
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> Optional.of(expectedPercents.getFirst());
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> Optional.of(expectedPercents.getLast());
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> Optional.of(expectedPercents.getLast());
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> Optional.of(expectedPercents.getFirst());
            default -> Optional.empty();
        };
    }

    private Optional<WordParserSpikeDocument.DocumentBlock> selectAmountTupleBlock(
            List<WordParserSpikeDocument.DocumentBlock> blocks) {
        WordParserSpikeDocument.DocumentBlock bestBlock = null;
        double bestFirstAmount = -1;
        for (WordParserSpikeDocument.DocumentBlock block : blocks) {
            var tuple = extractAmountTuple(block);
            if (tuple.isEmpty()) {
                continue;
            }
            var firstAmount = Double.parseDouble(tuple.orElseThrow().getFirst());
            if (firstAmount > bestFirstAmount) {
                bestFirstAmount = firstAmount;
                bestBlock = block;
            }
        }
        return Optional.ofNullable(bestBlock);
    }

    private Optional<List<String>> extractAmountTuple(WordParserSpikeDocument.DocumentBlock block) {
        var matcher = NUMERIC_VALUE_PATTERN.matcher(block.text());
        var amounts = new ArrayList<String>();
        var sawDecimalOrGroupedAmount = false;
        while (matcher.find()) {
            var rawAmount = matcher.group(1);
            amounts.add(stripTrailingZeros(rawAmount));
            if (rawAmount.contains(".") || rawAmount.contains(",")) {
                sawDecimalOrGroupedAmount = true;
            }
        }
        return amounts.size() >= 3 && sawDecimalOrGroupedAmount ? Optional.of(amounts) : Optional.empty();
    }

    private PointEvidence resolveFromCandidates(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            List<EvidenceCandidate> candidates) {
        var resolution = candidateResolver.resolve(reviewPointCode, candidateRole, candidates);
        if (resolution.confidenceLevel() == EvidenceConfidenceLevel.HIGH) {
            var selected = resolution.selectedCandidate().orElseThrow();
            return confirmedEvidence(reviewPointCode, selected, resolution.confidenceLevel());
        }
        return unresolvedEvidence(reviewPointCode, candidateRole, candidates, resolution);
    }

    private PointEvidence confirmedEvidence(
            ReviewPointCode reviewPointCode,
            EvidenceCandidate candidate,
            EvidenceConfidenceLevel confidenceLevel) {
        return new PointEvidence(
                reviewPointCode,
                candidate.candidateRole(),
                candidate.candidateValue(),
                EvidenceStatus.CONFIRMED,
                SOURCE_ORIGIN,
                SOURCE_EXTRACTION_MODE,
                CONTEXT_TYPE,
                candidate.blockId(),
                confidenceLevel.name(),
                summarizeEvidence(reviewPointCode, candidate.blockText(), candidate.candidateValue()),
                null,
                null,
                List.of(new EvidenceSlotCoverage(
                        slotKeyOf(reviewPointCode),
                        true,
                        true,
                        candidate.blockId() == null || candidate.blockId().isBlank()
                                ? EvidenceSlotCoverageStatus.PARTIAL
                                : EvidenceSlotCoverageStatus.SATISFIED,
                        candidate.blockId() == null || candidate.blockId().isBlank()
                                ? "SYS_EVIDENCE_BUNDLE_INVALID"
                                : null,
                        candidate.blockId() != null && !candidate.blockId().isBlank())));
    }

    private PointEvidence unresolvedEvidence(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            List<EvidenceCandidate> candidates,
            CandidateResolutionResult resolution) {
        var status = resolution.confidenceLevel() == EvidenceConfidenceLevel.UNKNOWN
                ? EvidenceStatus.MISSING
                : EvidenceStatus.AMBIGUOUS;
        var blockId = resolution.selectedCandidate().map(EvidenceCandidate::blockId).orElse(null);
        return new PointEvidence(
                reviewPointCode,
                candidateRole,
                resolution.selectedCandidate().map(EvidenceCandidate::candidateValue).orElse(null),
                status,
                SOURCE_ORIGIN,
                SOURCE_EXTRACTION_MODE,
                CONTEXT_TYPE,
                blockId,
                resolution.confidenceLevel().name(),
                buildUnresolvedSummary(resolution.confidenceLevel(), resolution.selectedCandidate(), candidates),
                resolution.diagnosticCode(),
                resolution.notConcludedReason(),
                List.of(unresolvedSlotCoverage(reviewPointCode, resolution, blockId)));
    }

    private EvidenceSlotCoverage unresolvedSlotCoverage(
            ReviewPointCode reviewPointCode,
            CandidateResolutionResult resolution,
            String blockId) {
        var coverageStatus = switch (resolution.confidenceLevel()) {
            case UNKNOWN -> EvidenceSlotCoverageStatus.MISSING;
            case CONFLICTED -> EvidenceSlotCoverageStatus.AMBIGUOUS;
            case MEDIUM, LOW -> EvidenceSlotCoverageStatus.LOW_CONFIDENCE;
            case HIGH -> EvidenceSlotCoverageStatus.SATISFIED;
        };
        return new EvidenceSlotCoverage(
                slotKeyOf(reviewPointCode),
                true,
                true,
                coverageStatus,
                resolution.diagnosticCode(),
                blockId != null && !blockId.isBlank());
    }

    private String slotKeyOf(ReviewPointCode reviewPointCode) {
        return roleOf(reviewPointCode).toLowerCase(Locale.ROOT);
    }

    private String buildUnresolvedSummary(
            EvidenceConfidenceLevel confidenceLevel,
            Optional<EvidenceCandidate> selectedCandidate,
            List<EvidenceCandidate> candidates) {
        return switch (confidenceLevel) {
            case UNKNOWN -> "未从 parser 输出中定位到最小证据。";
            case LOW -> "候选已提取，但缺少足够归属信号；候选值="
                    + selectedCandidate.map(EvidenceCandidate::candidateValue).orElse("N/A");
            case MEDIUM -> "候选命中了部分归属信号，但仍不足以进入确定性裁判；候选值="
                    + selectedCandidate.map(EvidenceCandidate::candidateValue).orElse("N/A");
            case CONFLICTED -> "同一证据块内存在多个候选竞争同一角色："
                    + candidates.stream()
                            .map(candidate -> candidate.blockId() + "=" + candidate.candidateValue())
                            .distinct()
                            .reduce((left, right) -> left + ", " + right)
                            .orElse("N/A");
            case HIGH -> throw new IllegalStateException("HIGH should not use unresolved summary");
        };
    }

    private List<WordParserSpikeDocument.DocumentBlock> paymentClauseBlocks(
            TaskExecutionRequest request,
            EvidenceBuildPlan plan) {
        if (request == null) {
            return plan.evidenceIndex().parsedDocument().document().blocks();
        }
        var blocks = plan.evidenceIndex().parsedDocument().document().blocks();
        var paymentMethod = request.task().structuredFieldsSnapshot().get("paymentMethod");
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return blocks;
        }
        return switch (paymentMethod) {
            case "MONTHLY" -> sliceBlocks(
                    blocks,
                    block -> containsAnyVariant(block.text(), "月度付款", "按月度付款", "按月形象进度付款"),
                    block -> containsAnyVariant(block.text(), "履约保函的说明", "工程保修及质保金支付"));
            case "MILESTONE" -> sliceBlocks(
                    blocks,
                    block -> containsAnyVariant(block.text(), "节点付款", "按节点付款"),
                    block -> containsAnyVariant(block.text(), "履约保函的说明", "工程保修及质保金支付"));
            default -> blocks;
        };
    }

    private List<WordParserSpikeDocument.DocumentBlock> sliceBlocks(
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            java.util.function.Predicate<WordParserSpikeDocument.DocumentBlock> startPredicate,
            java.util.function.Predicate<WordParserSpikeDocument.DocumentBlock> endPredicate) {
        int startIndex = -1;
        for (int index = 0; index < blocks.size(); index++) {
            if (startPredicate.test(blocks.get(index))) {
                startIndex = index;
                break;
            }
        }
        if (startIndex < 0 || startIndex >= blocks.size()) {
            return blocks;
        }

        int endIndex = blocks.size();
        for (int index = startIndex; index < blocks.size(); index++) {
            if (endPredicate.test(blocks.get(index))) {
                endIndex = index;
                break;
            }
        }
        return blocks.subList(startIndex, endIndex);
    }

    private Optional<WordParserSpikeDocument.DocumentBlock> findBlock(
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            List<String> labelHints,
            String candidateValue) {
        var normalizedValue = normalizeSearchText(candidateValue);
        for (WordParserSpikeDocument.DocumentBlock block : blocks) {
            var normalizedBlock = normalizeSearchText(block.normalizedText());
            if (labelHints.stream().anyMatch(block.text()::contains) && normalizedBlock.contains(normalizedValue)) {
                return Optional.of(block);
            }
        }
        for (WordParserSpikeDocument.DocumentBlock block : blocks) {
            if (normalizeSearchText(block.normalizedText()).contains(normalizedValue)) {
                return Optional.of(block);
            }
        }
        return Optional.empty();
    }

    private String joinSearchableText(List<WordParserSpikeDocument.DocumentBlock> blocks) {
        return blocks.stream()
                .map(WordParserSpikeDocument.DocumentBlock::normalizedText)
                .map(ParserBackedReviewInputPreparer::normalizeSearchText)
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private String summarizeEvidence(ReviewPointCode reviewPointCode, String blockText, String candidateValue) {
        var normalized = blockText.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120);
        }
        return "审核标签=" + displayLabelOf(reviewPointCode) + "；命中值=" + candidateValue + "；证据片段：" + normalized;
    }

    private String roleOf(ReviewPointCode reviewPointCode) {
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

    private String displayLabelOf(ReviewPointCode reviewPointCode) {
        return switch (reviewPointCode) {
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

    private String cleanExtractedValue(String value) {
        var normalized = value.replace('|', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.endsWith("。")) {
            return normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private String cleanPartyValue(String value) {
        var cleaned = cleanExtractedValue(value);
        for (String marker : List.of("地址", "法定代表人", "授权代表", "电话", "开户银行", "账号", "乙方", "甲方", "（公章）", "(公章)", "日期")) {
            var index = cleaned.indexOf(marker);
            if (index > 0) {
                cleaned = cleaned.substring(0, index).trim();
            }
        }
        return cleaned;
    }

    private List<String> splitLines(String text) {
        return text == null
                ? List.of()
                : text.lines()
                        .map(line -> line.replace('\u00A0', ' ').trim())
                        .filter(line -> !line.isBlank())
                        .toList();
    }

    private String stripTrailingZeros(String rawNumber) {
        var normalized = rawNumber.replace(",", "").trim();
        if (!normalized.contains(".")) {
            return normalized;
        }
        normalized = normalized.replaceAll("0+$", "").replaceAll("\\.$", "");
        return normalized.isBlank() ? rawNumber : normalized;
    }

    private static String normalizeSearchText(String text) {
        return text == null
                ? ""
                : text.replace('\u00A0', ' ')
                        .replaceAll("\\s+", "")
                        .replace(",", "")
                        .replace("。", "")
                        .replace("（", "")
                        .replace("）", "")
                        .replace(":", "")
                        .replace("：", "")
                        .toLowerCase(Locale.ROOT);
    }

    private static boolean containsAnyVariant(String text, String... values) {
        return textVariants(values).stream().anyMatch(text::contains);
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> textVariants(String... values) {
        var result = new ArrayList<String>();
        for (String value : values) {
            result.add(value);
            result.add(toMojibake(value));
        }
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
        for (List<Pattern> group : groups) {
            result.addAll(group);
        }
        return List.copyOf(result);
    }

    private static String toMojibake(String value) {
        return new String(value.getBytes(StandardCharsets.UTF_8), GBK);
    }
}

record ParsedContractDocument(
        TaskExecutionDocumentReference documentReference,
        WordParserSpikeDocument document) {
}

record ContractEvidenceIndex(
        ParsedContractDocument parsedDocument,
        String searchableText) {
}

record EvidenceBuildPlan(
        ContractEvidenceIndex evidenceIndex) {
}
