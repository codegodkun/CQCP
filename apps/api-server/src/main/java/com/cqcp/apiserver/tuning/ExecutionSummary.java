package com.cqcp.apiserver.tuning;

import java.util.List;

public record ExecutionSummary(
        String taskId,
        String executionId,
        String stage,
        String modelProfileCode,
        List<String> diagnosticCodes,
        String businessSummary) {
}
