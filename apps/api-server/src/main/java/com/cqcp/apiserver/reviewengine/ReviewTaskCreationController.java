package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.*;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for {@code POST /api/review/tasks}.
 *
 * <p>Accepts multipart/form-data with {@code file} (DOCX) and {@code metadata} (JSON).
 * Detects unknown multipart parts by enumerating all parts via {@link HttpServletRequest#getParts()}.
 */
@RestController
@RequestMapping("/api/review/tasks")
public final class ReviewTaskCreationController {

    private static final Set<String> KNOWN_PARTS = Set.of("file", "metadata");

    private final ReviewTaskCreationService service;

    public ReviewTaskCreationController(ReviewTaskCreationService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<CreateReviewTaskResponse> postTask(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") String metadataJson) throws IOException, ServletException {

        // Detect unknown multipart parts
        for (Part part : request.getParts()) {
            var name = part.getName();
            if (!KNOWN_PARTS.contains(name)) {
                throw new ValidationException(List.of(
                        new FieldError(name, "UNKNOWN_FIELD", name + " 为未声明字段")));
            }
        }

        // Read file bytes through try-with-resources so the stream is always closed.
        long fileSize = file.getSize();
        String originalFilename = file.getOriginalFilename();
        try (InputStream fileStream = file.getInputStream()) {
            var response = service.createTask(
                    originalFilename, fileStream, fileSize, metadataJson);
            return ResponseEntity.accepted().body(response);
        } catch (IOException e) {
            throw new DocumentStorageException("文件读取失败", e);
        }
    }
}
