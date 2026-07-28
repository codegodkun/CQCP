package com.cqcp.apiserver.reviewengine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** DTOs and exceptions for the Execution Status Query feature. */
final class ReviewExecutionStatusModels {

    private ReviewExecutionStatusModels() { throw new AssertionError("no instances"); }

    // ── Response DTOs ──

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ReviewExecutionStatusResponse(
            @JsonProperty("taskId") String taskId,
            @JsonProperty("executionId") String executionId,
            @JsonProperty("status") String status,
            @JsonProperty("currentStage") String currentStage,
            @JsonProperty("terminal") boolean terminal,
            @JsonProperty("snapshotAvailable") boolean snapshotAvailable,
            @JsonProperty("resultUrl") String resultUrl,
            @JsonProperty("reviewModel") ReviewModelSummary reviewModel,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt,
            @JsonProperty("superseded") Boolean superseded,
            @JsonProperty("supersededReason") String supersededReason) {

        ReviewExecutionStatusResponse {
            // superseded defaults to false when null
            if (superseded == null) superseded = false;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ReviewModelSummary(
            @JsonProperty("modelProfileCode") String modelProfileCode,
            @JsonProperty("providerType") String providerType,
            @JsonProperty("modelName") String modelName,
            @JsonProperty("endpointAlias") String endpointAlias,
            @JsonProperty("modelConfigVersion") String modelConfigVersion) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BusinessErrorResponse(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message,
            @JsonProperty("reason") String reason,
            @JsonProperty("retryable") Boolean retryable,
            @JsonProperty("operatorActionRequired") Boolean operatorActionRequired) {

        BusinessErrorResponse(String code, String message) {
            this(code, message, null, null, null);
        }
    }

    // ── Internal carrier ──

    /** Raw query result from the repository. All fields may be null for 0-row detection. */
    record ExecutionStatusRow(
            String taskId,
            String resultUrl,
            String executionId,
            String status,
            String currentStage,
            String modelProfileCode,
            String providerType,
            String modelName,
            String endpointAlias,
            String modelConfigVersion,
            Instant createdAt,
            Instant updatedAt,
            String snapshotExecutionId,
            String supersededByExecutionId,
            String supersededReason) {}

    // ── Exceptions ──

    /** Thrown when the execution is not found (404). */
    static final class ExecutionNotFoundException extends RuntimeException {
        ExecutionNotFoundException() {
            super("Execution not found");
        }
    }
}
