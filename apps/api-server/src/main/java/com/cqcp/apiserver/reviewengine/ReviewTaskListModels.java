package com.cqcp.apiserver.reviewengine;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

final class ReviewTaskListModels {

    private ReviewTaskListModels() {
        throw new AssertionError("no instances");
    }

    enum StatusGroup {
        PROCESSING,
        COMPLETED,
        FAILED
    }

    record ModelBinding(
            String profileCode,
            String displayName,
            String providerType,
            String modelName,
            String endpointAlias,
            String configVersion) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ResultStatistics(
            int plannedPointCount,
            int passCount,
            int errorCount,
            int warningCount,
            int notConcludedCount,
            int skippedCount) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TaskExecutionItem(
            String taskId,
            String executionId,
            String contractName,
            String status,
            String currentStage,
            String resultUrl,
            Instant createdAt,
            Instant updatedAt,
            Instant finishedAt,
            ModelBinding modelProfile,
            ResultStatistics resultStatistics) {
    }

    record TaskExecutionPage(
            List<TaskExecutionItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {

        TaskExecutionPage {
            items = List.copyOf(items);
        }
    }
}
