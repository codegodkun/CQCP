package com.cqcp.apiserver.modelgateway;

import static com.cqcp.apiserver.modelgateway.ModelProfileAdminModels.ConnectivityStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "cqcp.review.worker.enabled=false",
        "cqcp.admin-api.admin-token=admin-test-token",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ModelProfileSecretNonDisclosureIntegrationTest {

    private static final String SECRET_SENTINEL =
            "CQCP_RAW_SECRET_SENTINEL_6c78a3b45b4b4e18a69af86e";
    private static final String SECRET_REFERENCE = "env:CQCP_MODEL_DEEPSEEK_API_KEY";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ModelSecretResolver secretResolver;

    @MockBean
    private ModelConnectivityClient connectivityClient;

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private String profileCode;

    @BeforeEach
    void setUp() {
        profileCode = "SECRET_" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(java.util.Locale.ROOT);
        when(secretResolver.isValidReference(SECRET_REFERENCE)).thenReturn(true);
        when(secretResolver.resolve(SECRET_REFERENCE)).thenReturn(Optional.of(SECRET_SENTINEL));
        when(connectivityClient.test(
                        any(),
                        eq("deepseek-v4-pro"),
                        eq(SECRET_SENTINEL),
                        any()))
                .thenReturn(new ModelProfileAdminModels.ConnectivityOutcome(
                        ConnectivityStatus.AUTHENTICATION_FAILED,
                        "4XX",
                        false,
                        9));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM model_profile_connectivity_test WHERE profile_code = ?",
                profileCode);
        jdbcTemplate.update(
                "DELETE FROM model_profile_config_version WHERE profile_code = ?",
                profileCode);
    }

    @Test
    void oneRawSecretNeverLeavesConnectivityBoundaryOrCreatesExecutionArtifacts(
            CapturedOutput output) throws Exception {
        var artifactCountsBefore = executionArtifactCounts();
        var createResponse = mockMvc.perform(post("/api/admin/model-profiles")
                        .header("Authorization", "Bearer admin-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secretConfigured").value(true))
                .andReturn()
                .getResponse();

        var connectivityResponse = mockMvc.perform(post(
                                "/api/admin/model-profiles/{profileCode}/connectivity-tests",
                                profileCode)
                        .header("Authorization", "Bearer admin-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATION_FAILED"))
                .andReturn()
                .getResponse();

        var rejectedResponse = mockMvc.perform(post("/api/admin/model-profiles")
                        .header("Authorization", "Bearer admin-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().put("apiKey", SECRET_SENTINEL).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("UNKNOWN_FIELD"))
                .andReturn();

        assertThat(httpSurface(createResponse)).doesNotContain(SECRET_SENTINEL);
        assertThat(httpSurface(connectivityResponse)).doesNotContain(SECRET_SENTINEL);
        assertThat(httpSurface(rejectedResponse.getResponse())).doesNotContain(SECRET_SENTINEL);
        assertThat(String.valueOf(rejectedResponse.getResolvedException()))
                .doesNotContain(SECRET_SENTINEL);
        assertThat(databaseSurface()).doesNotContain(SECRET_SENTINEL);
        assertThat(executionArtifactCounts()).isEqualTo(artifactCountsBefore);
        assertThat(output.getAll()).doesNotContain(SECRET_SENTINEL);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode validRequest() {
        return mapper.createObjectNode()
                .put("profileCode", profileCode)
                .put("displayName", "Secret boundary evaluation")
                .put("providerType", "PUBLIC_OPENAI_COMPATIBLE")
                .put("usageScope", "EVALUATION")
                .put("endpointAlias", "deepseek-official")
                .put("modelName", "deepseek-v4-pro")
                .put("secretRef", SECRET_REFERENCE)
                .put("timeoutSeconds", 30)
                .put("retryCount", 0);
    }

    private String databaseSurface() throws Exception {
        var rows = new LinkedHashMap<String, Object>();
        rows.put("profiles", jdbcTemplate.queryForList("""
                SELECT *
                FROM model_profile_config_version
                WHERE profile_code = ?
                """, profileCode));
        rows.put("connectivity", jdbcTemplate.queryForList("""
                SELECT *
                FROM model_profile_connectivity_test
                WHERE profile_code = ?
                """, profileCode));
        rows.put("snapshots", jdbcTemplate.queryForList("SELECT * FROM review_result_snapshot"));
        rows.put("stageLogs", jdbcTemplate.queryForList("SELECT * FROM task_stage_log"));
        rows.put("tuningPackets", jdbcTemplate.queryForList("SELECT * FROM tuning_packet"));
        return mapper.writeValueAsString(rows);
    }

    private java.util.Map<String, Long> executionArtifactCounts() {
        return java.util.Map.of(
                "executions", count("execution"),
                "snapshots", count("review_result_snapshot"),
                "stageLogs", count("task_stage_log"),
                "tuningPackets", count("tuning_packet"));
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static String httpSurface(
            org.springframework.mock.web.MockHttpServletResponse response) {
        var surface = new StringBuilder(new String(
                response.getContentAsByteArray(),
                java.nio.charset.StandardCharsets.UTF_8));
        for (String name : response.getHeaderNames()) {
            surface.append('\n').append(name).append(':').append(response.getHeaders(name));
        }
        return surface.toString();
    }
}
