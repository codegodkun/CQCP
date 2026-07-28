package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewExecutionStatusModels.*;

import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for {@code GET /api/review/tasks/{taskId}/executions/{executionId}}.
 *
 * <p>Returns the public execution status.  Does not expose stage logs,
 * diagnostics, SYS-*, prompt, raw output, endpoint URL, secrets, or stack traces.
 */
@RestController
@RequestMapping("/api/review/tasks/{taskId}/executions/{executionId}")
public final class ReviewExecutionStatusController {

    private final ReviewExecutionStatusService service;

    public ReviewExecutionStatusController(ReviewExecutionStatusService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @GetMapping
    ResponseEntity<ReviewExecutionStatusResponse> getExecutionStatus(
            @PathVariable("taskId") String taskId,
            @PathVariable("executionId") String executionId) {

        var response = service.findStatus(taskId, executionId);
        return ResponseEntity.ok(response);
    }
}
