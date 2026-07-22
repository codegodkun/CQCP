package com.cqcp.apiserver.reviewengine;

import com.cqcp.apiserver.wordparser.WordParserSpikeDocument;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ConsistencyCandidateCollector {

    @FunctionalInterface
    interface ProbeDescriptor {
        List<EvidenceCandidate> probe(
                ReviewPointCode reviewPointCode,
                String candidateRole,
                List<WordParserSpikeDocument.DocumentBlock> blocks);
    }

    /** Immutable semantic classifier list for observability. */
    record SemanticClassifier(String id, java.util.function.Predicate<WordParserSpikeDocument.DocumentBlock> predicate) {
    }

    private static final String SCOPE_VERSION = "consistency-scope-v20260715.1";

    private final ProbeDescriptor descriptor;
    private final List<SemanticClassifier> partyASemanticClassifiers;

    ConsistencyCandidateCollector(
            ProbeDescriptor descriptor,
            List<SemanticClassifier> partyASemanticClassifiers) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.partyASemanticClassifiers = List.copyOf(partyASemanticClassifiers);
    }

    ConsistencyCandidateCollector(ProbeDescriptor descriptor) {
        this(descriptor, defaultPartyASemanticClassifiers());
    }

    private static List<SemanticClassifier> defaultPartyASemanticClassifiers() {
        return List.of(
                new SemanticClassifier("CONTRACT_TITLE_NAME_MENTION",
                        ConsistencyCandidateCollector::isContractTitleNameMention),
                new SemanticClassifier("AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION",
                        ConsistencyCandidateCollector::isAgreementPreambleContractNameMention));
    }

    ConsistencyCandidateBatch collect(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            WordParserSpikeDocument document,
            ConsistencyPolicySnapshot policy) {
        Objects.requireNonNull(reviewPointCode, "reviewPointCode");
        Objects.requireNonNull(candidateRole, "candidateRole");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(policy, "policy");

        if (!SCOPE_VERSION.equals(policy.scopeVersion())) {
            throw new IllegalStateException("Unsupported scope version: " + policy.scopeVersion());
        }

        var allBlocks = document.blocks();
        var scopeReport = document.scopeCoverageReport();

        var ledger = new ArrayList<BlockScanLedger>();
        var rawCandidates = new ArrayList<EvidenceCandidate>();
        var rejectedCandidates = new ArrayList<RejectedCandidate>();
        var handledSemanticContexts = new LinkedHashSet<String>();

        // handledSemanticContexts are marked inside isSemanticallyExcluded after each classifier executes

        // Build set of excluded blockIds from fully-voided/deleted ExcludedSourceRegions (BODY only)
        var excludedBlockIds = new java.util.HashSet<String>();
        for (var er : scopeReport.excludedSourceRegions()) {
            if ("BODY".equals(er.sourcePart()) && er.blockId() != null
                    && ("DELETED".equals(er.contextType()) || "VOIDED".equals(er.contextType()))) {
                // Only fully excluded if the region's reason indicates full-block exclusion
                // (The parser records DELETED/VOIDED for fully excluded blocks; mixed content goes to unresolved)
                excludedBlockIds.add(er.blockId());
            }
        }

        boolean hasUnresolved = !scopeReport.unresolvedSignals().isEmpty();

        for (WordParserSpikeDocument.DocumentBlock block : allBlocks) {
            var blockId = block.blockId();
            var regionName = block.regionType().name();
            var contextName = block.contextType().name();

            // 1. Region type inclusion
            if (!policy.includedRegionTypes().contains(regionName)) {
                ledger.add(new BlockScanLedger(blockId, regionName, contextName,
                        LedgerStatus.EXCLUDED, "REGION_EXCLUDED"));
                continue;
            }

            // 2. Strong context exclusion by context type enum
            if (policy.strongExcludedContextTypes().contains(contextName)) {
                ledger.add(new BlockScanLedger(blockId, regionName, contextName,
                        LedgerStatus.EXCLUDED, "STRONG_CONTEXT_EXCLUDED"));
                continue;
            }

            // 3. Strong exclusion from parser scope report (fully-voided/deleted body blocks)
            if (excludedBlockIds.contains(blockId)) {
                ledger.add(new BlockScanLedger(blockId, regionName, contextName,
                        LedgerStatus.EXCLUDED, "STRONG_CONTEXT_EXCLUDED"));
                continue;
            }

            // 4. Semantic exclusion (PARTY_A only)
            if (reviewPointCode == ReviewPointCode.PARTY_A_NAME_CONSISTENCY) {
                var excludedResult = checkSemanticExclusion(block, policy, handledSemanticContexts);
                if (excludedResult == SemanticResult.EXCLUDED) {
                    ledger.add(new BlockScanLedger(blockId, regionName, contextName,
                            LedgerStatus.EXCLUDED, "SEMANTIC_EXCLUDED"));
                    continue;
                } else if (excludedResult == SemanticResult.FAILED) {
                    ledger.add(new BlockScanLedger(blockId, regionName, contextName,
                            LedgerStatus.UNCERTAIN, "SEMANTIC_CLASSIFIER_FAILED"));
                    continue;
                }
            }

            // 5. Per-block probe via descriptor
            if (hasUnresolved) {
                // Cannot trust probe results when parser scope has unresolved signals
                ledger.add(new BlockScanLedger(blockId, regionName, contextName,
                        LedgerStatus.UNCERTAIN, "PARSER_SCOPE_UNRESOLVED"));
                continue;
            }

            try {
                var blockCandidates = descriptor.probe(
                        reviewPointCode, candidateRole, List.of(block));

                if (blockCandidates.isEmpty()) {
                    ledger.add(new BlockScanLedger(blockId, regionName, contextName,
                            LedgerStatus.SCANNED_NO_MATCH, "NO_MATCH"));
                } else {
                    boolean anyGrammarAccepted = false;
                    for (var candidate : blockCandidates) {
                        // ALL probe candidates enter rawCandidates (never silently dropped)
                        rawCandidates.add(candidate);
                        // valueFormatSignal=false also recorded in rejectedCandidates for audit
                        if (!candidate.valueFormatSignal()) {
                            rejectedCandidates.add(new RejectedCandidate(
                                    blockId, candidate.candidateValue(), "VALUE_GRAMMAR_REJECTED"));
                        } else {
                            anyGrammarAccepted = true;
                        }
                    }
                    if (anyGrammarAccepted) {
                        ledger.add(new BlockScanLedger(blockId, regionName, contextName,
                                LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_ACCEPTED"));
                    } else {
                        ledger.add(new BlockScanLedger(blockId, regionName, contextName,
                                LedgerStatus.CANDIDATE_EMITTED, "CANDIDATE_EMITTED_GRAMMAR_REJECTED"));
                    }
                }
            } catch (Exception e) {
                ledger.add(new BlockScanLedger(blockId, regionName, contextName,
                        LedgerStatus.UNCERTAIN, "PROBE_FAILED"));
            }
        }

        boolean fullPolicyScopeScanned = isFullPolicyScopeScanned(
                ledger, scopeReport, policy, handledSemanticContexts, allBlocks);

        // handled strong contexts come from parser's scope coverage report (already correct)
        // collector does NOT backfill based on block inspection

        return new ConsistencyCandidateBatch(
                List.copyOf(rawCandidates),
                List.copyOf(ledger),
                List.copyOf(rejectedCandidates),
                fullPolicyScopeScanned,
                List.copyOf(scopeReport.handledStrongContextTypes()),
                List.copyOf(handledSemanticContexts));
    }

    enum SemanticResult { NOT_EXCLUDED, EXCLUDED, FAILED }

    private SemanticResult checkSemanticExclusion(
            WordParserSpikeDocument.DocumentBlock block,
            ConsistencyPolicySnapshot policy,
            Set<String> handledSemanticContexts) {
        var exclusions = policy.strongExcludedSemanticContexts();
        if (exclusions.isEmpty()) {
            return SemanticResult.NOT_EXCLUDED;
        }
        boolean excluded = false;
        for (var sc : partyASemanticClassifiers) {
            if (!exclusions.contains(sc.id())) continue;
            try {
                boolean match = sc.predicate().test(block);
                if (match) excluded = true;
                // Mark as handled after execution, regardless of match result
                handledSemanticContexts.add(sc.id());
            } catch (Exception e) {
                // Predicate exception: classifier NOT counted as handled
                // If this is the first classifier and it fails, or the second also fails,
                // return FAILED — block state uncertain
                // Don't mark handledSemanticContexts for failed classifier
                return SemanticResult.FAILED;
            }
        }
        return excluded ? SemanticResult.EXCLUDED : SemanticResult.NOT_EXCLUDED;
    }

    /**
     * Package-private pure ledger coverage helper.
     * Returns null if coverage is valid, or a description of the first problem found.
     */
    static String validateLedgerCoverage(
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            List<BlockScanLedger> ledger) {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(ledger, "ledger");
        if (ledger.size() != blocks.size()) {
            return "ledger size " + ledger.size() + " != blocks size " + blocks.size();
        }
        var blockIds = blocks.stream().map(WordParserSpikeDocument.DocumentBlock::blockId).toList();
        var ledgerBlockIds = ledger.stream().map(BlockScanLedger::blockId).toList();
        for (int i = 0; i < blockIds.size(); i++) {
            if (!blockIds.get(i).equals(ledgerBlockIds.get(i))) {
                return "blockId mismatch at " + i + ": block=" + blockIds.get(i)
                        + " ledger=" + ledgerBlockIds.get(i);
            }
        }
        var distinctBlockIds = ledgerBlockIds.stream().distinct().count();
        if (distinctBlockIds != ledgerBlockIds.size()) {
            return "duplicate blockIds in ledger";
        }
        for (var entry : ledger) {
            if (entry.reason() == null || entry.reason().isBlank()) {
                return "null/blank reason for block " + entry.blockId();
            }
        }
        return null;
    }

    private boolean isFullPolicyScopeScanned(
            List<BlockScanLedger> ledger,
            WordParserSpikeDocument.ScopeCoverageReport scopeReport,
            ConsistencyPolicySnapshot policy,
            Set<String> handledSemanticContexts,
            List<WordParserSpikeDocument.DocumentBlock> allBlocks) {
        var coverageIssue = validateLedgerCoverage(allBlocks, ledger);
        if (coverageIssue != null) {
            return false;
        }
        if (ledger.stream().anyMatch(l -> l.status() == LedgerStatus.UNCERTAIN)) {
            return false;
        }
        if (!scopeReport.verified()) {
            return false;
        }
        if (!scopeReport.unresolvedSignals().isEmpty()) {
            return false;
        }
        for (String excludedContext : policy.strongExcludedContextTypes()) {
            if (!scopeReport.handledStrongContextTypes().contains(excludedContext)
                    && ledger.stream().noneMatch(l -> excludedContext.equals(l.context()))) {
                return false;
            }
        }
        for (String semanticContext : policy.strongExcludedSemanticContexts()) {
            if (!handledSemanticContexts.contains(semanticContext)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isContractTitleNameMention(WordParserSpikeDocument.DocumentBlock block) {
        var text = block.text();
        if (text == null || text.isBlank()) return false;
        if (hasPartyARoleAssignment(text)) return false;
        var stripped = text.replaceAll("\\s+", "");
        if (stripped.isEmpty()) return false;
        if (!stripped.contains("合同")) return false;
        if (!stripped.endsWith("合同")) return false;
        if (stripped.contains("。") || stripped.contains("；") || stripped.contains("：")) return false;
        return stripped.length() >= 2 && stripped.length() <= 160;
    }

    private static boolean isAgreementPreambleContractNameMention(WordParserSpikeDocument.DocumentBlock block) {
        var text = block.text();
        if (text == null || text.isBlank()) return false;
        if (hasPartyARoleAssignment(text)) return false;
        return text.contains("甲乙双方") && text.contains("签署")
                && text.contains("《") && text.contains("》") && text.contains("合同");
    }

    private static boolean hasPartyARoleAssignment(String text) {
        for (var pattern : ParserBackedReviewInputPreparer.PARTY_A_BLOCK_PATTERNS) {
            if (pattern.matcher(text).find()) return true;
        }
        return false;
    }

    enum LedgerStatus { EXCLUDED, SCANNED_NO_MATCH, CANDIDATE_EMITTED, UNCERTAIN }

    record BlockScanLedger(String blockId, String region, String context, LedgerStatus status, String reason) {}

    record RejectedCandidate(String blockId, String candidateValue, String reason) {}

    record ConsistencyCandidateBatch(
            List<EvidenceCandidate> rawCandidates,
            List<BlockScanLedger> blockScanLedger,
            List<RejectedCandidate> rejectedCandidates,
            boolean fullPolicyScopeScanned,
            List<String> handledStrongContextTypes,
            List<String> handledSemanticContextTypes) {
        ConsistencyCandidateBatch {
            rawCandidates = List.copyOf(rawCandidates);
            blockScanLedger = List.copyOf(blockScanLedger);
            rejectedCandidates = List.copyOf(rejectedCandidates);
            handledStrongContextTypes = List.copyOf(handledStrongContextTypes);
            handledSemanticContextTypes = List.copyOf(handledSemanticContextTypes);
        }
    }
}
