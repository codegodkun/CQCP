package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewExecutionStatusModels.*;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Service for the Execution Status Query API.
 *
 * <p>Maps internal execution status to public status, validates all fields,
 * and constructs the response DTO.  All validation failures throw
 * {@link IllegalStateException} (fail-closed).
 */
@Service
class ReviewExecutionStatusService {

    private final ReviewExecutionStatusRepository repository;

    ReviewExecutionStatusService(ReviewExecutionStatusRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** All 13 OpenAPI ExecutionStage values. */
    static final Set<String> VALID_CURRENT_STAGES = Set.of(
            "CREATED", "QUEUED", "PARSING", "INDEXING", "PLANNING",
            "BUILDING_EVIDENCE", "REVIEWING_RULES", "REVIEWING_MODEL", "COMPOSING",
            "SUCCESS", "PARTIAL_SUCCESS", "FAILED", "CANCELLED");

    /** Allowed providerType values. */
    static final Set<String> VALID_PROVIDER_TYPES = Set.of(
            "LOCAL", "PUBLIC_OPENAI_COMPATIBLE", "MOCK");

    /** Allowed SupersededReason values (from OpenAPI). */
    static final Set<String> VALID_SUPERSEDED_REASONS = Set.of(
            "TYPE_CORRECTION", "BUDGET_UPGRADE", "MANUAL_RERUN",
            "RULESET_RERUN", "MODEL_UPGRADE", "PARSER_UPGRADE", "ADMIN_RECOVERY");

    /**
     * Map internal ExecutionStatus string to public status.
     *
     * @throws IllegalStateException if the internal status is unknown
     */
    static String mapPublicStatus(String internalStatus) {
        if (internalStatus == null) {
            throw new IllegalStateException("Execution status is null");
        }
        return switch (internalStatus) {
            case "CREATED", "QUEUED" -> "QUEUED";
            case "PARSING", "INDEXING", "PLANNING", "BUILDING_EVIDENCE",
                 "REVIEWING_RULES", "REVIEWING_MODEL", "COMPOSING" -> "PROCESSING";
            case "SUCCESS" -> "SUCCESS";
            case "PARTIAL_SUCCESS" -> "PARTIAL_SUCCESS";
            case "FAILED", "CANCELLED" -> "FAILED";
            default -> throw new IllegalStateException("Unknown execution status: " + internalStatus);
        };
    }

    /**
     * Determine terminal flag from internal status string.
     */
    static boolean isTerminal(String internalStatus) {
        return "SUCCESS".equals(internalStatus) || "PARTIAL_SUCCESS".equals(internalStatus)
                || "FAILED".equals(internalStatus) || "CANCELLED".equals(internalStatus);
    }

    ReviewExecutionStatusResponse findStatus(String taskId, String executionId) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(executionId, "executionId");

        var row = repository.findStatus(taskId, executionId)
                .orElseThrow(ExecutionNotFoundException::new);

        // ── Pre-construct validation ──
        // Validate required fields before building the DTO
        validateRequired(row.taskId(), "taskId");
        validateRequired(row.executionId(), "executionId");
        var statusStr = validateRequired(row.status(), "status");
        var currentStage = validateRequired(row.currentStage(), "currentStage");
        validateRequired(row.resultUrl(), "resultUrl");
        validateRequired(row.modelProfileCode(), "reviewModel.modelProfileCode");
        validateRequired(row.providerType(), "reviewModel.providerType");
        validateRequired(row.modelName(), "reviewModel.modelName");
        validateRequired(row.endpointAlias(), "reviewModel.endpointAlias");
        validateRequired(row.modelConfigVersion(), "reviewModel.modelConfigVersion");
        validateRequired(row.createdAt(), "createdAt");
        validateRequired(row.updatedAt(), "updatedAt");

        // Validate currentStage is one of 13 allowed values
        if (!VALID_CURRENT_STAGES.contains(currentStage)) {
            throw new IllegalStateException("Unknown currentStage: " + currentStage);
        }

        // Validate providerType
        if (!VALID_PROVIDER_TYPES.contains(row.providerType())) {
            throw new IllegalStateException("Unknown providerType: " + row.providerType());
        }

        // Map public status
        var mappedStatus = mapPublicStatus(statusStr);
        var terminal = isTerminal(statusStr);

        // Snapshot + superseded
        var snapshotAvailable = row.snapshotExecutionId() != null;
        var superseded = row.supersededByExecutionId() != null;
        String supersededReason = null;
        if (row.supersededReason() != null) {
            if (!VALID_SUPERSEDED_REASONS.contains(row.supersededReason())) {
                throw new IllegalStateException("Unknown supersededReason: " + row.supersededReason());
            }
            supersededReason = row.supersededReason();
        }

        var reviewModel = new ReviewModelSummary(
                row.modelProfileCode(), row.providerType(), row.modelName(),
                row.endpointAlias(), row.modelConfigVersion());

        return new ReviewExecutionStatusResponse(
                row.taskId(), row.executionId(), mappedStatus, currentStage,
                terminal, snapshotAvailable, row.resultUrl(),
                reviewModel, row.createdAt(), row.updatedAt(),
                superseded, supersededReason);
    }

    private static String validateRequired(String value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("Required field is null: " + fieldName);
        }
        return value;
    }

    private static Instant validateRequired(Instant value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("Required field is null: " + fieldName);
        }
        return value;
    }
}
