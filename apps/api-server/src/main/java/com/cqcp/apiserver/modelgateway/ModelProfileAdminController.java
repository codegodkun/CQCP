package com.cqcp.apiserver.modelgateway;

import static com.cqcp.apiserver.modelgateway.ModelProfileAdminModels.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/model-profiles")
public final class ModelProfileAdminController {

    private final ModelProfileAdminService service;

    public ModelProfileAdminController(ModelProfileAdminService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @GetMapping
    ModelProfileListResponse listProfiles() {
        return service.listProfiles();
    }

    @PostMapping
    ResponseEntity<ModelProfileView> createProfile(@RequestBody JsonNode request) {
        return ResponseEntity.status(201).body(service.createProfile(request));
    }

    @PutMapping("/{profileCode}")
    ModelProfileView createNewVersion(
            @PathVariable String profileCode,
            @RequestBody JsonNode request) {
        return service.createNewVersion(profileCode, request);
    }

    @PostMapping("/{profileCode}/connectivity-tests")
    ConnectivityView testConnectivity(
            @PathVariable String profileCode,
            @RequestBody(required = false) JsonNode request) {
        if (request != null && !(request.isObject() && request.isEmpty())) {
            throw new ModelProfileAdminException(
                    400,
                    "CONNECTIVITY_BODY_NOT_ALLOWED",
                    "连通测试请求体必须为空，不接收 endpoint、KEY 或 prompt");
        }
        return service.testConnectivity(profileCode);
    }

    @ExceptionHandler(ModelProfileAdminException.class)
    ResponseEntity<ProblemDetail> adminError(ModelProfileAdminException exception) {
        var problem = ProblemDetail.forStatus(exception.httpStatus());
        problem.setType(URI.create(
                "urn:cqcp:problem:" + exception.code().toLowerCase(java.util.Locale.ROOT)));
        problem.setTitle(exception.code());
        problem.setDetail(exception.getMessage());
        return ResponseEntity.status(exception.httpStatus()).body(problem);
    }
}
