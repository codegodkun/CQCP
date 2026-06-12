package com.cqcp.apiserver.tuning;

import java.util.List;

public record TuningPacketSource(
        String tuningPacketId,
        ExecutionSummary executionSummary,
        List<PointDiagnostic> pointDiagnostics,
        VersionRefs versionRefs) {
}
