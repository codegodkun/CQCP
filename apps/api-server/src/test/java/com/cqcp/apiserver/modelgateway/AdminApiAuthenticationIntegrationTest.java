package com.cqcp.apiserver.modelgateway;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "cqcp.review.worker.enabled=false",
        "cqcp.admin-api.admin-token=admin-test-token",
        "cqcp.admin-api.readonly-token=readonly-test-token"
})
@AutoConfigureMockMvc
class AdminApiAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private ModelProfileAdminService service;

    @BeforeEach
    void resetSpy() {
        clearInvocations(service);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("anonymousAdminRequests")
    void everyAdminEndpointRejectsMissingAuthenticationBeforeService(
            String method,
            String path) throws Exception {
        var request = switch (method) {
            case "GET" -> get(path);
            case "HEAD" -> head(path);
            case "POST" -> post(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "PUT" -> put(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("ADMIN_AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedNonAdminIsForbiddenBeforeService() throws Exception {
        mockMvc.perform(get("/api/admin/model-profiles")
                        .header("Authorization", "Bearer readonly-test-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void validAdminCredentialReachesService() throws Exception {
        mockMvc.perform(get("/api/admin/model-profiles")
                        .header("Authorization", "Bearer admin-test-token"))
                .andExpect(status().isOk());

        verify(service).listProfiles();
    }

    private static Stream<Arguments> anonymousAdminRequests() {
        var collection = matrixVariants("api", "admin", "model-profiles");
        var profile = matrixVariants("api", "admin", "model-profiles", "DEEPSEEK_EVAL");
        var connectivity = matrixVariants(
                "api", "admin", "model-profiles", "DEEPSEEK_EVAL", "connectivity-tests");
        return Stream.of(
                        collection.stream().flatMap(path -> Stream.of(
                                Arguments.of("GET", path),
                                Arguments.of("HEAD", path),
                                Arguments.of("POST", path))),
                        profile.stream().map(path -> Arguments.of("PUT", path)),
                        connectivity.stream().flatMap(path -> Stream.of(
                                Arguments.of("POST", path),
                                Arguments.of("HEAD", path))))
                .flatMap(stream -> stream);
    }

    private static java.util.List<String> matrixVariants(String... segments) {
        var paths = new java.util.ArrayList<String>();
        paths.add("/" + String.join("/", segments));
        for (int index = 0; index < segments.length; index++) {
            var variant = segments.clone();
            variant[index] = variant[index] + ";v=1";
            paths.add("/" + String.join("/", variant));
        }
        return paths;
    }
}
