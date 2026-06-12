package com.cqcp.apiserver.tuning;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TuningPacketExportService {

    public TuningPacketExport export(TuningPacketSource source, ExportConfig config) {
        var selectedDiagnostics = switch (config.mode()) {
            case SINGLE_POINT -> source.pointDiagnostics().stream()
                    .filter(point -> point.reviewPointCode().equals(config.reviewPointCode()))
                    .toList();
            case FOCUSED -> source.pointDiagnostics().stream()
                    .filter(point -> config.focusDiagnosticCodes().contains(point.diagnosticCode()))
                    .toList();
        };

        if (selectedDiagnostics.isEmpty()) {
            throw new IllegalArgumentException("No diagnostics selected for export");
        }

        if (selectedDiagnostics.stream().anyMatch(PointDiagnostic::containsSensitivePayload)) {
            throw new IllegalArgumentException("Sensitive payload must not be exported");
        }

        return new TuningPacketExport(
                source.tuningPacketId(),
                config.mode(),
                source.executionSummary(),
                List.copyOf(selectedDiagnostics),
                source.versionRefs(),
                "AI Advice != Production Change; export is diagnostic-only and must not overwrite ReviewResultSnapshot.",
                List.of(
                        "FULL_CONTRACT_TEXT",
                        "FULL_PROMPT",
                        "FULL_RAW_OUTPUT",
                        "ENDPOINT_SECRET",
                        "STACK_TRACE"));
    }
}
