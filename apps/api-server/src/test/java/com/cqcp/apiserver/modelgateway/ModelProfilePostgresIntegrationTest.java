package com.cqcp.apiserver.modelgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "cqcp.review.worker.enabled=false")
class ModelProfilePostgresIntegrationTest {

    @Autowired
    private ModelProfileAdminService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private ModelProfileAdminRepository repository;

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private String profileCode;

    @BeforeEach
    void setUp() {
        profileCode = "PG_" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(java.util.Locale.ROOT);
    }

    @AfterEach
    void cleanUp() {
        doCallRealMethod().when(repository).insertProfile(any());
        jdbcTemplate.update(
                "DELETE FROM model_profile_connectivity_test WHERE profile_code = ?",
                profileCode);
        jdbcTemplate.update(
                "DELETE FROM model_profile_config_version WHERE profile_code = ?",
                profileCode);
    }

    @Test
    void updateCreatesImmutableSecondRowAndPreservesOriginalContent() {
        var first = service.createProfile(request(true, "DeepSeek V1", "deepseek-v4-pro"));
        var second = service.createNewVersion(
                profileCode,
                request(false, "DeepSeek V2", "deepseek-v4-flash"));

        var rows = jdbcTemplate.queryForList("""
                SELECT config_version, display_name, model_name, secret_ref,
                       enabled, is_default_for_new_task
                FROM model_profile_config_version
                WHERE profile_code = ?
                ORDER BY created_at, config_version
                """, profileCode);

        assertThat(rows).hasSize(2);
        assertThat(first.configVersion()).isNotEqualTo(second.configVersion());
        assertThat(rows.get(0))
                .containsEntry("config_version", first.configVersion())
                .containsEntry("display_name", "DeepSeek V1")
                .containsEntry("model_name", "deepseek-v4-pro")
                .containsEntry("secret_ref", "env:CQCP_MODEL_DEEPSEEK_API_KEY")
                .containsEntry("enabled", false)
                .containsEntry("is_default_for_new_task", false);
        assertThat(rows.get(1))
                .containsEntry("config_version", second.configVersion())
                .containsEntry("display_name", "DeepSeek V2")
                .containsEntry("model_name", "deepseek-v4-flash");
    }

    @Test
    void insertFailureRollsBackWithoutCreatingPartialVersion() {
        insertExistingProfile();
        doThrow(new DataIntegrityViolationException("forced insert failure"))
                .when(repository)
                .insertProfile(any());

        assertThatThrownBy(() -> service.createNewVersion(
                profileCode,
                request(false, "Will Fail", "deepseek-v4-flash")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT enabled
                FROM model_profile_config_version
                WHERE profile_code = ?
                """, Boolean.class, profileCode))
                .isFalse();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM model_profile_config_version
                WHERE profile_code = ?
                """, Integer.class, profileCode))
                .isOne();
    }

    private com.fasterxml.jackson.databind.node.ObjectNode request(
            boolean includeProfileCode,
            String displayName,
            String modelName) {
        var request = mapper.createObjectNode()
                .put("displayName", displayName)
                .put("providerType", "PUBLIC_OPENAI_COMPATIBLE")
                .put("usageScope", "EVALUATION")
                .put("endpointAlias", "deepseek-official")
                .put("modelName", modelName)
                .put("secretRef", "env:CQCP_MODEL_DEEPSEEK_API_KEY")
                .put("timeoutSeconds", 30)
                .put("retryCount", 0);
        if (includeProfileCode) request.put("profileCode", profileCode);
        return request;
    }

    private void insertExistingProfile() {
        jdbcTemplate.update("""
                INSERT INTO model_profile_config_version (
                    profile_code, config_version, display_name,
                    provider_type, endpoint_alias, model_name,
                    enabled, usage_scope, is_default_for_new_task,
                    secret_required, secret_ref, readiness_status,
                    timeout_seconds, retry_count,
                    effective_from, created_at
                ) VALUES (
                    ?, ?, 'Rollback Profile',
                    'PUBLIC_OPENAI_COMPATIBLE', 'deepseek-official', 'deepseek-v4-pro',
                    false, 'EVALUATION', false,
                    true, 'env:CQCP_MODEL_DEEPSEEK_API_KEY', 'NOT_READY',
                    30, 0, NOW(), NOW()
                )
                """,
                profileCode,
                "model-config-" + profileCode.toLowerCase(java.util.Locale.ROOT) + "-v1");
    }
}
