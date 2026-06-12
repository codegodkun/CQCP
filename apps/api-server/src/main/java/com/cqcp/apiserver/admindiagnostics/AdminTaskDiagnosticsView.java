package com.cqcp.apiserver.admindiagnostics;

import java.util.List;

public record AdminTaskDiagnosticsView(
        String taskId,
        String executionId,
        String contractName,
        String stage,
        ModelSummary model,
        Summary summary,
        List<PointResultView> pointResults,
        List<StageLogView> stageLogs,
        List<String> diagnosticCodes,
        List<String> excludedSensitiveFields) {

    public record ModelSummary(
            String displayName,
            String providerType) {
    }

    public record Summary(
            int plannedPointCount,
            int passCount,
            int errorCount,
            int warningCount,
            int notConcludedCount,
            int skippedCount) {
    }

    public record PointResultView(
            String reviewPointCode,
            String pointStatus,
            String businessMessage,
            String diagnosticCode) {
    }

    public record StageLogView(
            String stageName,
            String eventType,
            String status,
            String businessReason) {
    }
}
