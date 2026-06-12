package com.cqcp.apiserver.admindiagnostics;

import java.util.List;

public record AdminTaskDiagnosticsSource(
        String taskId,
        String executionId,
        String contractName,
        String stage,
        String modelDisplayName,
        String modelProviderType,
        Summary summary,
        List<PointResultSource> pointResults,
        List<StageLogSource> stageLogs,
        List<String> diagnosticCodes,
        String promptPreview,
        String rawOutput,
        String endpointSecret,
        String stackTrace) {

    public record Summary(
            int plannedPointCount,
            int passCount,
            int errorCount,
            int warningCount,
            int notConcludedCount,
            int skippedCount) {
    }

    public record PointResultSource(
            String reviewPointCode,
            String pointStatus,
            String businessMessage,
            String diagnosticCode) {
    }

    public record StageLogSource(
            String stageName,
            String eventType,
            String status,
            String businessReason) {
    }
}
