package com.cqcp.apiserver.reviewengine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTOs, exceptions, and field-error records for the Task Creation feature.
 *
 * <p>All exception classes are package-private.  {@code ExecutionBindingResolutionException}
 * is handled directly by {@link ReviewTaskCreationExceptionHandler} without wrapping.
 */
final class ReviewTaskCreationModels {

    private ReviewTaskCreationModels() {
        throw new AssertionError("no instances");
    }

    // ==================== Validation / Business DTOs ====================

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ValidationErrorResponse(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message,
            @JsonProperty("fieldErrors") List<FieldError> fieldErrors) {

        ValidationErrorResponse {
            code = "VALIDATION_ERROR";
            message = "请求校验失败";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BusinessErrorResponse(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message,
            @JsonProperty("reason") String reason,
            @JsonProperty("retryable") Boolean retryable,
            @JsonProperty("operatorActionRequired") Boolean operatorActionRequired) {

        BusinessErrorResponse {
            // All fields are explicitly provided; reason may be null (→ NON_NULL omits it)
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FieldError(
            @JsonProperty("field") String field,
            @JsonProperty("code") String code,
            @JsonProperty("message") String message) {

        FieldError {
            // compact canonical record constructor, no validation needed
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreateReviewTaskResponse(
            @JsonProperty("taskId") String taskId,
            @JsonProperty("executionId") String executionId,
            @JsonProperty("status") String status,
            @JsonProperty("resultUrl") String resultUrl) {

        CreateReviewTaskResponse {
            status = "QUEUED";
        }
    }

    record ExecutionInsert(
            String executionId,
            String taskId,
            String contractTypeProfileVersion,
            String ruleSetVersion,
            String reviewBudgetProfileVersion,
            String modelProfileCode,
            String modelConfigVersion,
            String parserVersion,
            String promptVersion,
            String schemaVersion,
            String patternLibraryVersion,
            String fieldLexiconVersion,
            String evidenceSelectorVersion,
            String providerType,
            String modelName,
            String endpointAlias) {

        ExecutionInsert {
            // all-fields carrier record for JDBC insert parameters
        }
    }

    // ==================== Exceptions ====================

    /**
     * Thrown when file I/O fails (create/read/write/move/delete).
     * Mapped to HTTP 503 DOCUMENT_STORAGE_FAILED.
     */
    static final class DocumentStorageException extends RuntimeException {
        DocumentStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when the uploaded file is not a valid DOCX (invalid content, wrong format).
     * Mapped to HTTP 400 INVALID_DOCUMENT_CONTENT.
     */
    static final class InvalidDocumentContentException extends RuntimeException {
        InvalidDocumentContentException(String message) {
            super(message);
        }
        InvalidDocumentContentException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when one or more field-level validation errors are detected.
     * The handler returns HTTP 400 with a {@link ValidationErrorResponse}.
     */
    static final class ValidationException extends RuntimeException {
        private final transient List<FieldError> fieldErrors;

        ValidationException(List<FieldError> fieldErrors) {
            super("请求校验失败");
            this.fieldErrors = List.copyOf(fieldErrors);
        }

        List<FieldError> getFieldErrors() {
            return fieldErrors;
        }
    }
}
