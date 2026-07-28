package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.*;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Translates exceptions thrown during Task Creation into the exact DTOs.
 *
 * <p>Does <strong>not</strong> use {@code ProblemDetail} — all response bodies
 * contain only the fields declared in the OpenAPI contract.
 */
@RestControllerAdvice(assignableTypes = ReviewTaskCreationController.class)
final class ReviewTaskCreationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReviewTaskCreationExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ValidationErrorResponse> handleValidation(ValidationException e) {
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse("VALIDATION_ERROR", "请求校验失败", e.getFieldErrors()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ValidationErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        var partName = e.getRequestPartName();
        var code = "REQUIRED_FIELD_MISSING";
        var message = partName + " 为必填项";
        var error = new FieldError(partName, code, message);
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse("VALIDATION_ERROR", "请求校验失败", List.of(error)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ValidationErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        var error = new FieldError("file", "FILE_TOO_LARGE", "file 超过 25 MiB 上限");
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse("VALIDATION_ERROR", "请求校验失败", List.of(error)));
    }

    @ExceptionHandler(ExecutionBindingResolutionException.class)
    ResponseEntity<BusinessErrorResponse> handleBindingUnavailable(ExecutionBindingResolutionException e) {
        log.warn("Execution binding unavailable: reason={}", e.reason());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new BusinessErrorResponse(
                        "EXECUTION_BINDING_UNAVAILABLE",
                        "当前执行绑定不可用",
                        e.reason().name(), false, true));
    }

    @ExceptionHandler(DocumentStorageException.class)
    ResponseEntity<BusinessErrorResponse> handleStorageFailure(DocumentStorageException e) {
        log.error("Document storage failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new BusinessErrorResponse(
                        "DOCUMENT_STORAGE_FAILED", "文档存储失败",
                        null, true, false));
    }

    @ExceptionHandler(InvalidDocumentContentException.class)
    ResponseEntity<ValidationErrorResponse> handleInvalidContent(InvalidDocumentContentException e) {
        var error = new FieldError("file", "INVALID_DOCUMENT_CONTENT", "file 不是有效 DOCX 文档");
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse("VALIDATION_ERROR", "请求校验失败", List.of(error)));
    }
}
