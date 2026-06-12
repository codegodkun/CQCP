package com.cqcp.apiserver.tuning;

import java.util.List;

public record PointDiagnostic(
        String reviewPointCode,
        String pointStatus,
        String diagnosticCode,
        String businessReason,
        List<String> evidenceBlockIds,
        String evidenceSummary,
        boolean containsSensitivePayload) {
}
