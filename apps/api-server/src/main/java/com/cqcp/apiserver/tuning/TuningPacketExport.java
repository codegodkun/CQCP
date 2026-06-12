package com.cqcp.apiserver.tuning;

import java.util.List;

public record TuningPacketExport(
        String tuningPacketId,
        ExportMode exportMode,
        ExecutionSummary executionSummary,
        List<PointDiagnostic> pointDiagnostics,
        VersionRefs versionRefs,
        String governanceBoundary,
        List<String> excludedContent) {
}
