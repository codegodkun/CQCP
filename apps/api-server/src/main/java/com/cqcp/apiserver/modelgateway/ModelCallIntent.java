package com.cqcp.apiserver.modelgateway;

import java.util.List;

public record ModelCallIntent(
        String taskId,
        String executionId,
        String family,
        String pointCode,
        List<String> inputBlockIds,
        String inputBlockIdsHash,
        String promptVersion,
        String schemaVersion,
        String modelVersion,
        int attempt) {
}
