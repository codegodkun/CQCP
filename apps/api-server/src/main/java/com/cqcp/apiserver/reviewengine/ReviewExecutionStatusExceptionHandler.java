package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewExecutionStatusModels.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReviewExecutionStatusController.class)
final class ReviewExecutionStatusExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReviewExecutionStatusExceptionHandler.class);

    @ExceptionHandler(ExecutionNotFoundException.class)
    ResponseEntity<BusinessErrorResponse> handleNotFound(ExecutionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BusinessErrorResponse("REVIEW_EXECUTION_NOT_FOUND", "\u672a\u627e\u5230\u6307\u5b9a\u5ba1\u6838\u6267\u884c"));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<BusinessErrorResponse> handleInternalError(IllegalStateException e) {
        log.warn("Execution status query failed: exceptionType={}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BusinessErrorResponse("INTERNAL_ERROR", "\u5ba1\u6838\u6267\u884c\u72b6\u6001\u67e5\u8be2\u5931\u8d25"));
    }
}