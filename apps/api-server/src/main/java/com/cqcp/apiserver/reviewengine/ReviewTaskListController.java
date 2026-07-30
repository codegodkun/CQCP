package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskListModels.TaskExecutionPage;

import java.net.URI;
import java.util.Objects;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review/tasks")
public final class ReviewTaskListController {

    private final ReviewTaskListService service;

    public ReviewTaskListController(ReviewTaskListService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @GetMapping
    TaskExecutionPage getTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String statusGroup,
            @RequestParam(name = "q", required = false) String query) {
        return service.getTasks(page, size, statusGroup, query);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalidQuery(IllegalArgumentException exception) {
        var problem = ProblemDetail.forStatus(400);
        problem.setType(URI.create("urn:cqcp:problem:invalid-task-list-query"));
        problem.setTitle("任务列表查询参数无效");
        problem.setDetail(exception.getMessage());
        return ResponseEntity.badRequest().body(problem);
    }
}
