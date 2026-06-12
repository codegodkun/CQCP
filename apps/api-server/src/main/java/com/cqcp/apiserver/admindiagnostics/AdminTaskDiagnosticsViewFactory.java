package com.cqcp.apiserver.admindiagnostics;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdminTaskDiagnosticsViewFactory {

    public AdminTaskDiagnosticsView create(AdminTaskDiagnosticsSource source) {
        return new AdminTaskDiagnosticsView(
                source.taskId(),
                source.executionId(),
                source.contractName(),
                source.stage(),
                new AdminTaskDiagnosticsView.ModelSummary(
                        source.modelDisplayName(),
                        source.modelProviderType()),
                new AdminTaskDiagnosticsView.Summary(
                        source.summary().plannedPointCount(),
                        source.summary().passCount(),
                        source.summary().errorCount(),
                        source.summary().warningCount(),
                        source.summary().notConcludedCount(),
                        source.summary().skippedCount()),
                source.pointResults().stream()
                        .map(point -> new AdminTaskDiagnosticsView.PointResultView(
                                point.reviewPointCode(),
                                point.pointStatus(),
                                point.businessMessage(),
                                point.diagnosticCode()))
                        .toList(),
                source.stageLogs().stream()
                        .map(log -> new AdminTaskDiagnosticsView.StageLogView(
                                log.stageName(),
                                log.eventType(),
                                log.status(),
                                log.businessReason()))
                        .toList(),
                List.copyOf(source.diagnosticCodes()),
                List.of(
                        "promptPreview",
                        "rawOutput",
                        "endpointSecret",
                        "stackTrace"));
    }
}
