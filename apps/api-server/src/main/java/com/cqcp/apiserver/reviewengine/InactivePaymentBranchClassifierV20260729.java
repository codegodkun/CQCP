package com.cqcp.apiserver.reviewengine;

import com.cqcp.apiserver.wordparser.WordParserSpikeDocument;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Document-local, version-owned classifier for inactive payment branches.
 *
 * <p>It consumes only parser-issued block identity/order/text/section/region.
 * No task metadata, sample identity, fixture or human result is accepted.
 */
final class InactivePaymentBranchClassifierV20260729 {

    static final String CLASSIFIER_ID = "INACTIVE_PAYMENT_BRANCH";

    private static final String PRODUCT_NODE = "□按节点付款";
    private static final String PRODUCT_MONTHLY = "√月度付款";
    private static final Set<String> PRODUCT_CONFLICTS =
            Set.of("√按节点付款", "√节点付款", "□月度付款");
    private static final String ENGINEERING_SELECTOR = "进度款:A模式□B模式";
    private static final Set<String> ENGINEERING_NODES =
            Set.of("B模式:按节点付款:", "B模式:按节点付款");

    private InactivePaymentBranchClassifierV20260729() {
    }

    static Resolution classify(List<WordParserSpikeDocument.DocumentBlock> blocks) {
        var product = scanProduct(blocks);
        var engineering = scanEngineering(blocks);

        if (product.state() == FamilyState.UNCERTAIN
                || engineering.state() == FamilyState.UNCERTAIN
                || (product.state() == FamilyState.VALID
                    && engineering.state() == FamilyState.VALID)) {
            return Resolution.uncertain("PAYMENT_BRANCH_ENVELOPE_UNCERTAIN");
        }
        if (product.state() == FamilyState.VALID) {
            return Resolution.certain(blockIds(blocks, product.startInclusive(), product.endExclusive()));
        }
        if (engineering.state() == FamilyState.VALID) {
            return Resolution.certain(blockIds(
                    blocks, engineering.startInclusive(), engineering.endExclusive()));
        }
        return Resolution.certain(Set.of());
    }

    private static FamilyScan scanProduct(List<WordParserSpikeDocument.DocumentBlock> blocks) {
        var nodeExact = new ArrayList<Integer>();
        var monthlyExact = new ArrayList<Integer>();
        var relevant = new ArrayList<Integer>();
        var conflicts = new ArrayList<Integer>();
        for (int i = 0; i < blocks.size(); i++) {
            String text = normalizeMarker(blocks.get(i).text());
            if (text.contains(PRODUCT_NODE) || text.contains(PRODUCT_MONTHLY)
                    || PRODUCT_CONFLICTS.stream().anyMatch(text::contains)) {
                relevant.add(i);
            }
            if (PRODUCT_NODE.equals(text)) nodeExact.add(i);
            if (PRODUCT_MONTHLY.equals(text)) monthlyExact.add(i);
            if (PRODUCT_CONFLICTS.contains(text)) conflicts.add(i);
        }
        if (relevant.isEmpty()) return FamilyScan.absent();
        if (nodeExact.size() != 1 || monthlyExact.size() != 1 || !conflicts.isEmpty()) {
            return FamilyScan.uncertain();
        }
        int start = nodeExact.getFirst();
        int end = monthlyExact.getFirst();
        if (start >= end || !sameBodySection(blocks.get(start), blocks.get(end))) {
            return FamilyScan.uncertain();
        }
        for (int index : relevant) {
            if (!sameBodySection(blocks.get(start), blocks.get(index))) {
                return FamilyScan.uncertain();
            }
        }
        return FamilyScan.valid(start, end);
    }

    private static FamilyScan scanEngineering(List<WordParserSpikeDocument.DocumentBlock> blocks) {
        var selectors = new ArrayList<Integer>();
        var nodes = new ArrayList<Integer>();
        boolean anyRelevant = false;
        boolean conflict = false;
        for (int i = 0; i < blocks.size(); i++) {
            String text = normalizeMarker(blocks.get(i).text());
            if (text.contains("进度款:") && (text.contains("A模式") || text.contains("B模式"))) {
                anyRelevant = true;
                if (ENGINEERING_SELECTOR.equals(text)) selectors.add(i);
                if (text.contains("□A模式") || text.contains("√B模式")) conflict = true;
            }
            if (text.contains("B模式:按节点付款")) {
                anyRelevant = true;
                if (ENGINEERING_NODES.contains(text)) nodes.add(i);
            }
        }
        if (!anyRelevant) return FamilyScan.absent();
        if (conflict || selectors.size() != 1 || nodes.size() != 1) {
            return FamilyScan.uncertain();
        }
        int selector = selectors.getFirst();
        int start = nodes.getFirst();
        if (selector >= start || !sameBodySection(blocks.get(selector), blocks.get(start))) {
            return FamilyScan.uncertain();
        }
        int end = -1;
        for (int i = start + 1; i < blocks.size(); i++) {
            var block = blocks.get(i);
            if (!sameBodySection(blocks.get(start), block)) continue;
            String text = normalizeMarker(block.text());
            if (text.startsWith("竣工款:") || text.startsWith("安装完工款:")) {
                end = i;
                break;
            }
        }
        if (end < 0) return FamilyScan.uncertain();
        return FamilyScan.valid(start, end);
    }

    private static boolean sameBodySection(
            WordParserSpikeDocument.DocumentBlock left,
            WordParserSpikeDocument.DocumentBlock right) {
        return left.regionType() == WordParserSpikeDocument.RegionType.BODY
                && right.regionType() == WordParserSpikeDocument.RegionType.BODY
                && left.sectionPath() != null
                && !left.sectionPath().isEmpty()
                && left.sectionPath().equals(right.sectionPath());
    }

    private static Set<String> blockIds(
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            int startInclusive,
            int endExclusive) {
        var result = new HashSet<String>();
        for (int i = startInclusive; i < endExclusive; i++) {
            result.add(blocks.get(i).blockId());
        }
        return Set.copyOf(result);
    }

    static String normalizeMarker(String raw) {
        if (raw == null) return "";
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace('：', ':');
        var result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp))
                .forEach(result::appendCodePoint);
        return result.toString().strip();
    }

    enum FamilyState { ABSENT, VALID, UNCERTAIN }

    private record FamilyScan(FamilyState state, int startInclusive, int endExclusive) {
        static FamilyScan absent() {
            return new FamilyScan(FamilyState.ABSENT, -1, -1);
        }

        static FamilyScan valid(int startInclusive, int endExclusive) {
            return new FamilyScan(FamilyState.VALID, startInclusive, endExclusive);
        }

        static FamilyScan uncertain() {
            return new FamilyScan(FamilyState.UNCERTAIN, -1, -1);
        }
    }

    record Resolution(boolean certain, Set<String> excludedBlockIds, String reason) {
        Resolution {
            excludedBlockIds = Set.copyOf(excludedBlockIds);
        }

        static Resolution certain(Set<String> excludedBlockIds) {
            return new Resolution(true, excludedBlockIds, "PAYMENT_BRANCH_CLASSIFIED");
        }

        static Resolution uncertain(String reason) {
            return new Resolution(false, Set.of(), reason);
        }

        boolean excludes(WordParserSpikeDocument.DocumentBlock block) {
            if (!certain) {
                throw new IllegalStateException(reason);
            }
            return excludedBlockIds.contains(block.blockId());
        }
    }
}
