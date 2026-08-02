package com.cqcp.apiserver.reviewengine;

import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public final class TaskResultQueryController {

    private final TaskResultQueryService service;

    public TaskResultQueryController(TaskResultQueryService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @GetMapping("/{taskId}/result")
    public ReviewResultSnapshot getResult(
            @PathVariable String taskId,
            @RequestParam(required = false) String executionId) {
        if (executionId == null) {
            return service.getResult(taskId);
        }
        if (executionId.isBlank() || !executionId.equals(executionId.trim())) {
            throw new TaskResultNotFoundException(taskId, executionId);
        }
        return service.getResult(taskId, executionId);
    }
}
