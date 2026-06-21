package com.cqcp.apiserver.reviewengine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class EvidenceOverlapEvaluator {

    private static final Pattern ROW_REF = Pattern.compile("^table:[^/]+/row:(\\d+)$");
    private static final Pattern CELL_REF = Pattern.compile("^table:[^/]+/row:(\\d+)/cell:(\\d+)$");

    private EvidenceOverlapEvaluator() {
    }

    static Optional<String> canonicalKey(SourceAnchorSummary anchor) {
        if (anchor == null || anchor.blockId() == null || anchor.blockId().isBlank()) {
            return Optional.empty();
        }
        if (anchor.previewElementRef() == null || anchor.previewElementRef().isBlank()) {
            return Optional.of("BLOCK:" + anchor.blockId());
        }

        var cellMatcher = CELL_REF.matcher(anchor.previewElementRef());
        if (cellMatcher.matches()) {
            return Optional.of("TABLE_CELL:"
                    + anchor.blockId()
                    + ":"
                    + cellMatcher.group(1)
                    + ":"
                    + cellMatcher.group(2));
        }

        var rowMatcher = ROW_REF.matcher(anchor.previewElementRef());
        if (rowMatcher.matches()) {
            return Optional.of("TABLE_ROW:" + anchor.blockId() + ":" + rowMatcher.group(1));
        }

        return Optional.empty();
    }

    static EvidenceOverlapResult evaluatePositive(
            Set<String> expectedCanonicalAnchors,
            List<SourceAnchorSummary> actualAnchors) {
        var expected = new LinkedHashSet<>(expectedCanonicalAnchors);
        var actual = canonicalKeys(actualAnchors);
        var hasUnavailableAnchor = actualAnchors.stream()
                .anyMatch(anchor -> canonicalKey(anchor).isEmpty());
        var matched = intersection(expected, actual);
        var missing = difference(expected, actual);
        var unexpected = difference(actual, expected);

        var expectedRecall = ratio(matched.size(), expected.size());
        var actualPrecision = ratio(matched.size(), actual.size());
        var requiredHit = missing.isEmpty() ? 1 : 0;
        var failureReason = hasUnavailableAnchor
                ? AttributionFailureReason.SOURCE_ANCHOR_UNAVAILABLE
                : failureReason(expected, actual, missing, unexpected);

        return new EvidenceOverlapResult(
                expectedRecall,
                actualPrecision,
                requiredHit,
                Set.copyOf(missing),
                Set.copyOf(unexpected),
                failureReason);
    }

    static EvidenceOverlapResult evaluateNegative(
            EvidenceStatus evidenceStatus,
            Set<String> forbiddenCanonicalAnchors,
            List<SourceAnchorSummary> actualAnchors) {
        var actual = canonicalKeys(actualAnchors);
        var forbiddenHits = intersection(forbiddenCanonicalAnchors, actual);
        if (!forbiddenHits.isEmpty()) {
            return new EvidenceOverlapResult(
                    BigDecimal.ZERO.setScale(4),
                    BigDecimal.ZERO.setScale(4),
                    0,
                    Set.of(),
                    Set.copyOf(forbiddenHits),
                    AttributionFailureReason.CONFLICTING_CANDIDATE_ADMITTED);
        }
        return new EvidenceOverlapResult(
                BigDecimal.ZERO.setScale(4),
                BigDecimal.ZERO.setScale(4),
                0,
                Set.of(),
                Set.of(),
                evidenceStatus == EvidenceStatus.AMBIGUOUS
                        ? AttributionFailureReason.ATTRIBUTION_AMBIGUOUS
                        : AttributionFailureReason.SOURCE_ANCHOR_UNAVAILABLE);
    }

    private static Set<String> canonicalKeys(List<SourceAnchorSummary> anchors) {
        var result = new LinkedHashSet<String>();
        for (SourceAnchorSummary anchor : anchors) {
            canonicalKey(anchor).ifPresent(result::add);
        }
        return result;
    }

    private static AttributionFailureReason failureReason(
            Set<String> expected,
            Set<String> actual,
            Set<String> missing,
            Set<String> unexpected) {
        if (missing.isEmpty() && unexpected.isEmpty()) {
            return null;
        }
        if (missing.isEmpty()) {
            return AttributionFailureReason.UNEXPECTED_ANCHOR_SELECTED;
        }
        if (actual.isEmpty()) {
            return AttributionFailureReason.EXPECTED_ANCHOR_MISSING;
        }
        if (sameBlockAndRowWithDifferentCell(expected, actual)) {
            return AttributionFailureReason.WRONG_TABLE_CELL_ATTRIBUTION;
        }
        if (sameBlockWithDifferentRow(expected, actual)) {
            return AttributionFailureReason.WRONG_TABLE_ROW_ATTRIBUTION;
        }
        return AttributionFailureReason.WRONG_BLOCK_ATTRIBUTION;
    }

    private static boolean sameBlockAndRowWithDifferentCell(Set<String> expected, Set<String> actual) {
        for (String expectedKey : expected) {
            var expectedParts = expectedKey.split(":");
            if (expectedParts.length != 4 || !"TABLE_CELL".equals(expectedParts[0])) {
                continue;
            }
            for (String actualKey : actual) {
                var actualParts = actualKey.split(":");
                if (actualParts.length == 4
                        && "TABLE_CELL".equals(actualParts[0])
                        && expectedParts[1].equals(actualParts[1])
                        && expectedParts[2].equals(actualParts[2])
                        && !expectedParts[3].equals(actualParts[3])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean sameBlockWithDifferentRow(Set<String> expected, Set<String> actual) {
        for (String expectedKey : expected) {
            var expectedParts = expectedKey.split(":");
            if (expectedParts.length < 3 || !expectedParts[0].startsWith("TABLE_")) {
                continue;
            }
            for (String actualKey : actual) {
                var actualParts = actualKey.split(":");
                if (actualParts.length >= 3
                        && actualParts[0].startsWith("TABLE_")
                        && expectedParts[1].equals(actualParts[1])
                        && !expectedParts[2].equals(actualParts[2])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> intersection(Set<String> left, Set<String> right) {
        var result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        var result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static BigDecimal ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}

record EvidenceOverlapResult(
        BigDecimal expectedRecall,
        BigDecimal actualPrecision,
        int requiredHit,
        Set<String> missingExpectedBlocks,
        Set<String> unexpectedMatchedBlocks,
        AttributionFailureReason attributionFailureReason) {
}

enum AttributionFailureReason {
    EXPECTED_ANCHOR_MISSING,
    UNEXPECTED_ANCHOR_SELECTED,
    WRONG_BLOCK_ATTRIBUTION,
    WRONG_TABLE_ROW_ATTRIBUTION,
    WRONG_TABLE_CELL_ATTRIBUTION,
    CONFLICTING_CANDIDATE_ADMITTED,
    ATTRIBUTION_AMBIGUOUS,
    SOURCE_ANCHOR_UNAVAILABLE
}
