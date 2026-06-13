package com.cqcp.apiserver.reviewengine;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TaskResultQueryController.class)
public final class TaskResultQueryExceptionHandler {

    @ExceptionHandler(TaskResultNotFoundException.class)
    ProblemDetail handleTaskResultNotFound(TaskResultNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "TASK_RESULT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(TaskResultNotReadyException.class)
    ProblemDetail handleTaskResultNotReady(TaskResultNotReadyException exception) {
        return problem(HttpStatus.CONFLICT, "TASK_RESULT_NOT_READY", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(code);
        return problem;
    }
}
