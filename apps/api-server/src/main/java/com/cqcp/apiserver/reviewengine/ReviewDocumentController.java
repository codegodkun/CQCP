package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewDocumentModels.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review/tasks/{taskId}/executions/{executionId}")
public final class ReviewDocumentController {

    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ReviewDocumentService service;

    public ReviewDocumentController(ReviewDocumentService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @GetMapping("/document-preview")
    DocumentPreview getPreview(
            @PathVariable String taskId,
            @PathVariable String executionId) {
        return service.getPreview(taskId, executionId);
    }

    @GetMapping("/document")
    ResponseEntity<ByteArrayResource> getDocument(
            @PathVariable String taskId,
            @PathVariable String executionId) {
        var document = service.getDocument(taskId, executionId);
        var disposition = ContentDisposition.attachment()
                .filename(document.originalFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(DOCX)
                .contentLength(document.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-CQCP-Document-SHA256", document.sha256())
                .body(new ByteArrayResource(document.content()));
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(DocumentNotFoundException exception) {
        return problem(404, "DOCUMENT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(DocumentConflictException.class)
    ResponseEntity<ProblemDetail> conflict(DocumentConflictException exception) {
        return problem(409, exception.code(), exception.getMessage());
    }

    @ExceptionHandler(DocumentUnavailableException.class)
    ResponseEntity<ProblemDetail> unavailable(DocumentUnavailableException exception) {
        return problem(503, "DOCUMENT_UNAVAILABLE", "合同文档暂不可用");
    }

    private ResponseEntity<ProblemDetail> problem(int status, String code, String detail) {
        var problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("urn:cqcp:problem:" + code.toLowerCase(java.util.Locale.ROOT)));
        problem.setTitle(code);
        problem.setDetail(detail);
        return ResponseEntity.status(status).body(problem);
    }
}
