package com.cqcp.apiserver.reviewengine;

import com.cqcp.apiserver.tuning.PointDiagnostic;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ResultComposer {

    public ReviewResultSnapshot compose(ResultComposerInput input, ReviewEngineResult reviewEngineResult) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(reviewEngineResult, "reviewEngineResult");

        var pointResults = List.copyOf(reviewEngineResult.pointResults());
        var diagnostics = List.copyOf(reviewEngineResult.pointDiagnostics());
        var findings = buildFindings(pointResults);
        var sourceAnchors = collectSourceAnchors(pointResults);
        var status = deriveSnapshotStatus(reviewEngineResult.summary());

        return new ReviewResultSnapshot(
                input.taskId(),
                input.executionId(),
                null,
                null,
                status,
                reviewEngineResult.summary(),
                reviewEngineResult.reviewCompleteness(),
                pointResults,
                findings,
                diagnostics,
                sourceAnchors,
                input.structuredFieldsSnapshot(),
                input.enabledReviewPointsSnapshot(),
                input.disabledReviewPointsSnapshot(),
                input.versionReferences().contractTypeProfileVersion(),
                input.versionReferences().ruleSetVersion(),
                input.versionReferences().reviewBudgetProfileVersion(),
                input.versionReferences().modelProfileVersion(),
                input.versionReferences().parserVersion(),
                input.versionReferences().promptVersion(),
                input.versionReferences().schemaVersion(),
                input.versionReferences().patternLibraryVersion(),
                input.versionReferences().fieldLexiconVersion(),
                input.versionReferences().evidenceSelectorVersion(),
                input.createdAt());
    }

    private SnapshotStatus deriveSnapshotStatus(ReviewSummary summary) {
        return summary.notConcludedCount() > 0 ? SnapshotStatus.PARTIAL_SUCCESS : SnapshotStatus.SUCCESS;
    }

    private List<ReviewFinding> buildFindings(List<PointReviewResult> pointResults) {
        var findings = new ArrayList<ReviewFinding>();
        for (PointReviewResult pointResult : pointResults) {
            if (pointResult.pointStatus() != PointStatus.ERROR
                    && pointResult.pointStatus() != PointStatus.WARNING) {
                continue;
            }
            findings.add(new ReviewFinding(
                    pointResult.reviewPointCode(),
                    pointResult.findingSeverity(),
                    pointResult.businessMessage(),
                    pointResult.sourceAnchors()));
        }
        return List.copyOf(findings);
    }

    private List<SourceAnchorSummary> collectSourceAnchors(List<PointReviewResult> pointResults) {
        var anchors = new LinkedHashMap<String, SourceAnchorSummary>();
        for (PointReviewResult pointResult : pointResults) {
            for (SourceAnchorSummary anchor : pointResult.sourceAnchors()) {
                anchors.putIfAbsent(anchor.blockId(), anchor);
            }
        }
        return List.copyOf(anchors.values());
    }
}

record ResultComposerInput(
        String taskId,
        String executionId,
        Map<String, String> structuredFieldsSnapshot,
        List<ReviewPointSnapshot> enabledReviewPointsSnapshot,
        List<ReviewPointSnapshot> disabledReviewPointsSnapshot,
        VersionReferences versionReferences,
        Instant createdAt) {

    ResultComposerInput {
        structuredFieldsSnapshot = Map.copyOf(structuredFieldsSnapshot);
        enabledReviewPointsSnapshot = List.copyOf(enabledReviewPointsSnapshot);
        disabledReviewPointsSnapshot = List.copyOf(disabledReviewPointsSnapshot);
        Objects.requireNonNull(versionReferences, "versionReferences");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}

enum SnapshotStatus {
    SUCCESS,
    PARTIAL_SUCCESS
}

record ReviewResultSnapshot(
        String taskId,
        String executionId,
        String supersededByExecutionId,
        String supersededReason,
        SnapshotStatus status,
        ReviewSummary summary,
        ReviewCompleteness reviewCompleteness,
        List<PointReviewResult> pointResults,
        List<ReviewFinding> findings,
        List<PointDiagnostic> diagnostics,
        List<SourceAnchorSummary> sourceAnchors,
        Map<String, String> structuredFieldsSnapshot,
        List<ReviewPointSnapshot> enabledReviewPointsSnapshot,
        List<ReviewPointSnapshot> disabledReviewPointsSnapshot,
        String contractTypeProfileVersion,
        String ruleSetVersion,
        String reviewBudgetProfileVersion,
        String modelProfileVersion,
        String parserVersion,
        String promptVersion,
        String schemaVersion,
        String patternLibraryVersion,
        String fieldLexiconVersion,
        String evidenceSelectorVersion,
        Instant createdAt) {

    ReviewResultSnapshot {
        pointResults = List.copyOf(pointResults);
        findings = List.copyOf(findings);
        diagnostics = List.copyOf(diagnostics);
        sourceAnchors = List.copyOf(sourceAnchors);
        structuredFieldsSnapshot = Map.copyOf(structuredFieldsSnapshot);
        enabledReviewPointsSnapshot = List.copyOf(enabledReviewPointsSnapshot);
        disabledReviewPointsSnapshot = List.copyOf(disabledReviewPointsSnapshot);
    }
}

record ReviewFinding(
        ReviewPointCode reviewPointCode,
        FindingSeverity severity,
        String businessMessage,
        List<SourceAnchorSummary> sourceAnchors) {

    ReviewFinding {
        sourceAnchors = List.copyOf(sourceAnchors);
    }
}

record ReviewPointSnapshot(
        ReviewPointCode reviewPointCode,
        String displayCode,
        String displayName,
        String reviewPointFamily,
        String contractType,
        FindingSeverity defaultSeverity,
        int displayOrder) {
}

record VersionReferences(
        String contractTypeProfileVersion,
        String ruleSetVersion,
        String reviewBudgetProfileVersion,
        String modelProfileVersion,
        String parserVersion,
        String promptVersion,
        String schemaVersion,
        String patternLibraryVersion,
        String fieldLexiconVersion,
        String evidenceSelectorVersion) {
}
