package com.cqcp.apiserver.tuning;

import java.util.List;

public record ExportConfig(
        ExportMode mode,
        String reviewPointCode,
        List<String> focusDiagnosticCodes) {
}
