package com.cqcp.apiserver.modelgateway;

import static com.cqcp.apiserver.modelgateway.ModelProfileAdminModels.ModelProfileAdminException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ModelProfileAdminControllerTest {

    private static final String SECRET_SENTINEL = "CQCP_CONTROLLER_SECRET_SENTINEL";

    private final ModelProfileAdminService service = mock(ModelProfileAdminService.class);
    private final ModelProfileAdminController controller =
            new ModelProfileAdminController(service);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void connectivityTestRejectsEndpointKeyAndPromptRequestBodyWithoutCallingService() {
        var body = mapper.createObjectNode()
                .put("endpoint", "https://attacker.invalid")
                .put("apiKey", SECRET_SENTINEL)
                .put("prompt", "合同全文");

        assertThatThrownBy(() -> controller.testConnectivity("DEEPSEEK_EVAL", body))
                .isInstanceOfSatisfying(ModelProfileAdminException.class, exception -> {
                    assertThat(exception.httpStatus()).isEqualTo(400);
                    assertThat(exception.code()).isEqualTo("CONNECTIVITY_BODY_NOT_ALLOWED");
                    assertThat(exception.getMessage()).doesNotContain(SECRET_SENTINEL);
                    assertThat(exception.getMessage()).doesNotContain("attacker.invalid");
                });
        verifyNoInteractions(service);
    }

    @Test
    void connectivityProblemResponseContainsOnlyStableCategory() throws Exception {
        var exception = new ModelProfileAdminException(
                400,
                "CONNECTIVITY_BODY_NOT_ALLOWED",
                "连通测试请求体必须为空");

        var response = controller.adminError(exception);
        var json = mapper.writeValueAsString(response.getBody());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json).contains("CONNECTIVITY_BODY_NOT_ALLOWED");
        assertThat(json).doesNotContain(SECRET_SENTINEL);
        assertThat(json).doesNotContain("Authorization");
    }

    @Test
    void duplicateAndUnsupportedProfileConflictsUseProblemJson() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(service.createProfile(any())).thenThrow(new ModelProfileAdminException(
                409,
                "PROFILE_ALREADY_EXISTS",
                "profileCode 已存在"));

        mockMvc.perform(post("/api/admin/model-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("PROFILE_ALREADY_EXISTS"));

        when(service.testConnectivity("UNSUPPORTED")).thenThrow(
                new ModelProfileAdminException(
                        409,
                        "PROFILE_PROVIDER_UNSUPPORTED",
                        "该 profile 不支持连通测试"));
        mockMvc.perform(post(
                                "/api/admin/model-profiles/UNSUPPORTED/connectivity-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value(
                        "PROFILE_PROVIDER_UNSUPPORTED"));
    }
}
