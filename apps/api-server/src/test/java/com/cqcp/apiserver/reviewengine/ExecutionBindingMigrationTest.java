package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for V2__execution_binding_release.sql.
 *
 * <p>These tests reuse the existing {@code @SpringBootTest} + Flyway setup
 * and connect to the real PostgreSQL 16 specified by
 * {@code CQCP_DB_URL / CQCP_DB_USERNAME / CQCP_DB_PASSWORD}.
 * INSERT/UPDATE tests use {@code @Transactional @Rollback} to avoid leaking
 * state.  No Testcontainers, no embedded DB, no new dependencies.
 */
@SpringBootTest
@Transactional
@Rollback
class ExecutionBindingMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v1AndV2MigrationsApplied() {
        var versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history ORDER BY version");
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).get("version")).isEqualTo("1");
        assertThat(versions.get(1).get("version")).isEqualTo("2");
    }

    @Test
    void budgetSeedsMatchFrozenValues() {
        var rows = jdbcTemplate.queryForList(
                "SELECT review_budget_profile_version, profile_code, display_name, enabled, "
                        + "standard_ratio, deep_review_ratio, "
                        + "budget_approval_policy_version, "
                        + "to_char(effective_from AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') AS effective_from_str, "
                        + "to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') AS created_at_str, "
                        + "model_budget->>'maxInputTokensPerCall' AS max_input, "
                        + "model_budget->>'maxSharedEvidenceTokens' AS max_shared, "
                        + "model_budget->>'maxPointOverlayTokens' AS max_overlay, "
                        + "model_budget->>'maxStructuredFieldTokens' AS max_struct, "
                        + "model_budget->>'maxInstructionAndSchemaTokens' AS max_instruct, "
                        + "model_budget->>'maxOutputTokens' AS max_output, "
                        + "model_budget->>'maxModelCallsPerTask' AS max_calls_task, "
                        + "model_budget->>'maxModelCallsPerFamily' AS max_calls_family "
                        + "FROM review_budget_profile_version");

        assertThat(rows).hasSize(3);

        var byCode = rows.stream().collect(
                Collectors.toMap(r -> (String) r.get("profile_code"), r -> r));

        for (var entry : byCode.entrySet()) {
            var row = entry.getValue();
            assertThat(row.get("effective_from_str")).isEqualTo("2026-07-24T00:00:00Z");
            assertThat(row.get("created_at_str")).isEqualTo("2026-07-24T00:00:00Z");
            assertThat(row.get("budget_approval_policy_version"))
                    .isEqualTo("budget-approval-policy-mvp-v20260724.1");
            assertThat(Integer.parseInt((String) row.get("max_input"))).isEqualTo(12000);
            assertThat(Integer.parseInt((String) row.get("max_shared"))).isEqualTo(7000);
            assertThat(Integer.parseInt((String) row.get("max_overlay"))).isEqualTo(1500);
            assertThat(Integer.parseInt((String) row.get("max_struct"))).isEqualTo(1000);
            assertThat(Integer.parseInt((String) row.get("max_instruct"))).isEqualTo(1000);
            assertThat(Integer.parseInt((String) row.get("max_output"))).isEqualTo(1500);
            assertThat(Integer.parseInt((String) row.get("max_calls_task"))).isEqualTo(4);
            assertThat(Integer.parseInt((String) row.get("max_calls_family"))).isEqualTo(1);
        }

        var standard = byCode.get("STANDARD");
        assertThat(standard).isNotNull();
        assertThat(standard.get("review_budget_profile_version")).isEqualTo("budget-standard-v20260724.1");
        assertThat(standard.get("display_name")).isEqualTo("标准审核");
        assertThat(standard.get("enabled")).isEqualTo(true);
        assertThat(standard.get("standard_ratio")).isEqualTo(5);
        assertThat(standard.get("deep_review_ratio")).isEqualTo(1);

        var deep = byCode.get("DEEP_REVIEW");
        assertThat(deep).isNotNull();
        assertThat(deep.get("display_name")).isEqualTo("深度审核（预留）");
        assertThat(deep.get("enabled")).isEqualTo(false);
        assertThat(deep.get("review_budget_profile_version")).isEqualTo("budget-deep-review-v20260724.1");
        assertThat(deep.get("standard_ratio")).isEqualTo(5);
        assertThat(deep.get("deep_review_ratio")).isEqualTo(1);

        var eval = byCode.get("EVALUATION");
        assertThat(eval).isNotNull();
        assertThat(eval.get("display_name")).isEqualTo("评测（预留）");
        assertThat(eval.get("enabled")).isEqualTo(false);
        assertThat(eval.get("review_budget_profile_version")).isEqualTo("budget-evaluation-v20260724.1");
        assertThat(eval.get("standard_ratio")).isEqualTo(5);
        assertThat(eval.get("deep_review_ratio")).isEqualTo(1);
    }

    @Test
    void demMockSeedMatchesFrozenValues() {
        var rows = jdbcTemplate.queryForList(
                "SELECT profile_code, config_version, display_name, provider_type, "
                        + "endpoint_alias, model_name, enabled, usage_scope, "
                        + "is_default_for_new_task, secret_required, readiness_status, "
                        + "timeout_seconds, retry_count, "
                        + "to_char(effective_from AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') AS effective_from_str, "
                        + "to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') AS created_at_str "
                        + "FROM model_profile_config_version");

        assertThat(rows).hasSize(1);
        var row = rows.getFirst();
        assertThat(row.get("profile_code")).isEqualTo("MVP_DEMO_MOCK");
        assertThat(row.get("config_version")).isEqualTo("model-config-mvp-demo-mock-v20260724.1");
        assertThat(row.get("display_name")).isEqualTo("MVP Demo Mock");
        assertThat(row.get("provider_type")).isEqualTo("MOCK");
        assertThat(row.get("endpoint_alias")).isEqualTo("mock-local");
        assertThat(row.get("model_name")).isEqualTo("cqcp-demo-mock");
        assertThat(row.get("enabled")).isEqualTo(true);
        assertThat(row.get("usage_scope")).isEqualTo("DEMO");
        assertThat(row.get("is_default_for_new_task")).isEqualTo(true);
        assertThat(row.get("secret_required")).isEqualTo(false);
        assertThat(row.get("readiness_status")).isEqualTo("READY");
        assertThat(row.get("timeout_seconds")).isEqualTo(30);
        assertThat(row.get("retry_count")).isEqualTo(0);
        assertThat(row.get("effective_from_str")).isEqualTo("2026-07-24T00:00:00Z");
        assertThat(row.get("created_at_str")).isEqualTo("2026-07-24T00:00:00Z");
    }

    @Test
    void demoMockUniqueDefaultPerScope() {
        assertThrows(DuplicateKeyException.class, () ->
                jdbcTemplate.update("""
                        INSERT INTO model_profile_config_version (
                            profile_code, config_version, display_name,
                            provider_type, endpoint_alias, model_name,
                            enabled, usage_scope, is_default_for_new_task,
                            secret_required, readiness_status,
                            timeout_seconds, retry_count,
                            effective_from, created_at
                        ) VALUES (
                            'DUPLICATE_DEMO', 'dup-v1', 'Duplicate',
                            'MOCK', 'mock-dup', 'dup-model',
                            true, 'DEMO', true,
                            false, 'READY',
                            30, 0,
                            '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z'
                        )
                        """));
    }

    @Test
    void bindingSeedAllNotNull() {
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM execution_binding_release", Integer.class);
        assertThat(count).isPositive();

        var row = jdbcTemplate.queryForMap(
                "SELECT * FROM execution_binding_release LIMIT 1");
        assertThat(row.get("binding_version")).isNotNull();
        assertThat(row.get("contract_type_profile_version")).isNotNull();
        assertThat(row.get("rule_set_version")).isNotNull();
        assertThat(row.get("review_budget_profile_version")).isNotNull();
        assertThat(row.get("model_profile_code")).isNotNull();
        assertThat(row.get("model_config_version")).isNotNull();
        assertThat(row.get("parser_version")).isNotNull();
        assertThat(row.get("prompt_version")).isNotNull();
        assertThat(row.get("schema_version")).isNotNull();
        assertThat(row.get("pattern_library_version")).isNotNull();
        assertThat(row.get("field_lexicon_version")).isNotNull();
        assertThat(row.get("evidence_selector_version")).isNotNull();
        assertThat(row.get("provider_type")).isNotNull();
        assertThat(row.get("model_name")).isNotNull();
        assertThat(row.get("endpoint_alias")).isNotNull();
    }

    @Test
    void bindingForeignKeyIntegrityBudget() {
        var ex = assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("""
                        INSERT INTO execution_binding_release (
                            binding_version, purpose, deployment_scope,
                            contract_type_code, contract_type_profile_code,
                            enabled, effective_from, content_digest,
                            contract_type_profile_version, rule_set_version,
                            review_budget_profile_version,
                            model_profile_code, model_config_version,
                            parser_version, prompt_version, schema_version,
                            pattern_library_version, field_lexicon_version,
                            evidence_selector_version,
                            provider_type, model_name, endpoint_alias
                        ) VALUES (
                            'bad-fk-budget', 'OTHER_SCOPE', 'OTHER',
                            'OTHER', 'OTHER',
                            false, '2026-07-24T00:00:00Z', '0000000000000000000000000000000000000000000000000000000000000000',
                            'v1', 'v1', 'nonexistent-budget',
                            'MVP_DEMO_MOCK', 'model-config-mvp-demo-mock-v20260724.1',
                            'pv1', 'prv1', 'sv1',
                            'plv1', 'flv1', 'esv1',
                            'MOCK', 'mock', 'mock-alias'
                        )
                        """));
        assertThat(ex.getMostSpecificCause().getMessage()).contains("fk_binding_budget");
    }

    @Test
    void bindingForeignKeyIntegrityModel() {
        var ex = assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("""
                        INSERT INTO execution_binding_release (
                            binding_version, purpose, deployment_scope,
                            contract_type_code, contract_type_profile_code,
                            enabled, effective_from, content_digest,
                            contract_type_profile_version, rule_set_version,
                            review_budget_profile_version,
                            model_profile_code, model_config_version,
                            parser_version, prompt_version, schema_version,
                            pattern_library_version, field_lexicon_version,
                            evidence_selector_version,
                            provider_type, model_name, endpoint_alias
                        ) VALUES (
                            'bad-fk-model', 'OTHER_SCOPE', 'OTHER',
                            'OTHER', 'OTHER',
                            false, '2026-07-24T00:00:00Z', '0000000000000000000000000000000000000000000000000000000000000000',
                            'v1', 'v1', 'budget-standard-v20260724.1',
                            'NONEXISTENT_PROFILE', 'nonexistent-config',
                            'pv1', 'prv1', 'sv1',
                            'plv1', 'flv1', 'esv1',
                            'MOCK', 'mock', 'mock-alias'
                        )
                        """));
        assertThat(ex.getMostSpecificCause().getMessage()).contains("fk_binding_model");
    }

    @Test
    void lifecycleUniqueIndexRejectsDoubleEnabled() {
        assertThrows(DuplicateKeyException.class, () ->
                jdbcTemplate.update("""
                        INSERT INTO execution_binding_release (
                            binding_version, purpose, deployment_scope,
                            contract_type_code, contract_type_profile_code,
                            enabled, effective_from, content_digest,
                            contract_type_profile_version, rule_set_version,
                            review_budget_profile_version,
                            model_profile_code, model_config_version,
                            parser_version, prompt_version, schema_version,
                            pattern_library_version, field_lexicon_version,
                            evidence_selector_version,
                            provider_type, model_name, endpoint_alias
                        ) VALUES (
                            'dup-enabled-v2', 'MVP_DEMO', 'DEMO',
                            'ENGINEERING', 'ENGINEERING_PROCUREMENT',
                            true, '2026-07-24T00:00:00Z', '0000000000000000000000000000000000000000000000000000000000000000',
                            'v1', 'v1', 'budget-standard-v20260724.1',
                            'MVP_DEMO_MOCK', 'model-config-mvp-demo-mock-v20260724.1',
                            'pv1', 'prv1', 'sv1',
                            'plv1', 'flv1', 'esv1',
                            'MOCK', 'mock', 'mock-alias'
                        )
                        """));
    }

    @Test
    void lifecycleBindingSwitch() {
        // Step 1: Insert old enabled + new disabled (same custom selector, different binding_version)
        jdbcTemplate.update("""
                INSERT INTO execution_binding_release (
                    binding_version, purpose, deployment_scope,
                    contract_type_code, contract_type_profile_code,
                    enabled, effective_from, content_digest,
                    contract_type_profile_version, rule_set_version,
                    review_budget_profile_version,
                    model_profile_code, model_config_version,
                    parser_version, prompt_version, schema_version,
                    pattern_library_version, field_lexicon_version,
                    evidence_selector_version,
                    provider_type, model_name, endpoint_alias
                ) VALUES (
                    'lc-old', 'MVP_DEMO', 'lc-test-domain',
                    'ENGINEERING', 'ENGINEERING_PROCUREMENT',
                    true, '2026-07-24T00:00:00Z', '0000000000000000000000000000000000000000000000000000000000000000',
                    'v1', 'v1', 'budget-standard-v20260724.1',
                    'MVP_DEMO_MOCK', 'model-config-mvp-demo-mock-v20260724.1',
                    'pv1', 'prv1', 'sv1',
                    'plv1', 'flv1', 'esv1',
                    'MOCK', 'mock', 'mock-alias'
                ), (
                    'lc-new', 'MVP_DEMO', 'lc-test-domain',
                    'ENGINEERING', 'ENGINEERING_PROCUREMENT',
                    false, '2026-07-24T00:00:00Z', '0000000000000000000000000000000000000000000000000000000000000000',
                    'v1', 'v1', 'budget-standard-v20260724.1',
                    'MVP_DEMO_MOCK', 'model-config-mvp-demo-mock-v20260724.1',
                    'pv1', 'prv1', 'sv1',
                    'plv1', 'flv1', 'esv1',
                    'MOCK', 'mock', 'mock-alias'
                )
                """);

        // Step 2: Disable old, enable new — lifecycle switch in single transaction
        jdbcTemplate.update("UPDATE execution_binding_release SET enabled = false "
                + "WHERE binding_version = 'lc-old'");
        jdbcTemplate.update("UPDATE execution_binding_release SET enabled = true "
                + "WHERE binding_version = 'lc-new'");

        // Step 3: Verify exactly one enabled row for this selector
        var enabled = jdbcTemplate.queryForList(
                "SELECT binding_version FROM execution_binding_release "
                        + "WHERE purpose = 'MVP_DEMO' AND deployment_scope = 'lc-test-domain' "
                        + "AND contract_type_code = 'ENGINEERING' AND enabled = true");
        assertThat(enabled).hasSize(1);
        assertThat(enabled.getFirst().get("binding_version")).isEqualTo("lc-new");
    }

    @Test
    void lifecycleModelDefaultSwitch() {
        // Step 1: Insert a new disabled + non-default row
        jdbcTemplate.update("""
                INSERT INTO model_profile_config_version (
                    profile_code, config_version, display_name,
                    provider_type, endpoint_alias, model_name,
                    enabled, usage_scope, is_default_for_new_task,
                    secret_required, readiness_status,
                    timeout_seconds, retry_count,
                    effective_from, created_at
                ) VALUES (
                    'LC_SWITCH_TEST', 'lc-switch-v1', 'Lifecycle Switch Test',
                    'MOCK', 'mock-lc', 'lc-model',
                    false, 'DEMO', false,
                    false, 'READY',
                    30, 0,
                    '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z'
                )
                """);

        // Step 2: Disable old default
        jdbcTemplate.update("UPDATE model_profile_config_version "
                + "SET enabled = false, is_default_for_new_task = false "
                + "WHERE profile_code = 'MVP_DEMO_MOCK'");

        // Step 3: Enable new + make default
        jdbcTemplate.update("UPDATE model_profile_config_version "
                + "SET enabled = true, is_default_for_new_task = true "
                + "WHERE profile_code = 'LC_SWITCH_TEST'");

        // Step 4: Verify exactly one enabled default for DEMO scope
        var defaults = jdbcTemplate.queryForList(
                "SELECT profile_code FROM model_profile_config_version "
                        + "WHERE usage_scope = 'DEMO' AND enabled = true AND is_default_for_new_task = true");
        assertThat(defaults).hasSize(1);
        assertThat(defaults.getFirst().get("profile_code")).isEqualTo("LC_SWITCH_TEST");
    }

    @Test
    void contentDigestHexConstraint() {
        var ex = assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("""
                        INSERT INTO execution_binding_release (
                            binding_version, purpose, deployment_scope,
                            contract_type_code, contract_type_profile_code,
                            enabled, effective_from, content_digest,
                            contract_type_profile_version, rule_set_version,
                            review_budget_profile_version,
                            model_profile_code, model_config_version,
                            parser_version, prompt_version, schema_version,
                            pattern_library_version, field_lexicon_version,
                            evidence_selector_version,
                            provider_type, model_name, endpoint_alias
                        ) VALUES (
                            'bad-digest', 'OTHER_SCOPE', 'OTHER',
                            'OTHER', 'OTHER',
                            false, '2026-07-24T00:00:00Z', 'NOT_A_VALID_HEX',
                            'v1', 'v1', 'budget-standard-v20260724.1',
                            'MVP_DEMO_MOCK', 'model-config-mvp-demo-mock-v20260724.1',
                            'pv1', 'prv1', 'sv1',
                            'plv1', 'flv1', 'esv1',
                            'MOCK', 'mock', 'mock-alias'
                        )
                        """));
        assertThat(ex.getMostSpecificCause().getMessage()).contains("chk_content_digest_hex");
    }

    @Test
    void readinessCheckConstraint() {
        assertThrows(Exception.class, () ->
                jdbcTemplate.update("""
                        INSERT INTO model_profile_config_version (
                            profile_code, config_version, display_name,
                            provider_type, endpoint_alias, model_name,
                            enabled, usage_scope, is_default_for_new_task,
                            secret_required, readiness_status,
                            timeout_seconds, retry_count,
                            effective_from, created_at
                        ) VALUES (
                            'BAD_READINESS', 'bad-v1', 'Bad Readiness',
                            'MOCK', 'bad-mock', 'bad-model',
                            true, 'DEMO', false,
                            false, 'INVALID_STATUS',
                            30, 0,
                            '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z'
                        )
                        """));
    }

    @Test
    void engineeringAliasPersisted() {
        var rows = jdbcTemplate.queryForList(
                "SELECT contract_type_code, contract_type_profile_code, contract_type_profile_version "
                        + "FROM execution_binding_release");
        assertThat(rows).isNotEmpty();
        var row = rows.getFirst();
        assertThat(row.get("contract_type_code")).isEqualTo("ENGINEERING");
        assertThat(row.get("contract_type_profile_code")).isEqualTo("ENGINEERING_PROCUREMENT");
        assertThat(row.get("contract_type_profile_version")).isEqualTo("v20260705.1");
    }

    // ---- digest and catalog happy-path verification ----
    // The constants below are cross-checked between a standalone Node.js SHA-256
    // computation and the Java ExecutionBindingCatalog.computeDigest() output.
    // DO NOT replace with a value dynamically extracted from the runtime — that
    // would create a circular verification.

    private static final String EXPECTED_SEED_DIGEST =
            "d79d1d7056d15eb9ed189cabc9980ea000c4188fbd725f9b5c847727488f658b";

    @Test
    void seedBindingDigestMatchesPrecomputedConstant() {
        var storedDigest = jdbcTemplate.queryForObject(
                "SELECT content_digest FROM execution_binding_release "
                        + "WHERE binding_version = 'mvp-demo-engineering-v20260724.1'",
                String.class);

        // Independent pre-computed constant, not derived from the runtime.
        assertThat(storedDigest).isEqualTo(EXPECTED_SEED_DIGEST);
    }

    @Test
    void seedBindingResolvableByRealRepositoryAndCatalog() {
        var repo = new JdbcExecutionBindingRepository(jdbcTemplate);
        var fixedClock = Clock.fixed(
                Instant.parse("2026-07-24T12:00:00Z"), ZoneOffset.UTC);
        var catalog = new ExecutionBindingCatalog(repo, fixedClock);

        var binding = catalog.resolveDefault("MVP_DEMO", "DEMO", "ENGINEERING");

        assertThat(binding).isNotNull();
        assertThat(binding.bindingVersion()).isEqualTo("mvp-demo-engineering-v20260724.1");
        assertThat(binding.contractTypeCode()).isEqualTo("ENGINEERING");
        assertThat(binding.contractTypeProfileCode()).isEqualTo("ENGINEERING_PROCUREMENT");
        assertThat(binding.modelConfigVersion()).isEqualTo("model-config-mvp-demo-mock-v20260724.1");
        assertThat(binding.providerType()).isEqualTo("MOCK");
        assertThat(binding.modelName()).isEqualTo("cqcp-demo-mock");

        // Production digest algorithm recomputed by the runtime itself
        assertThat(ExecutionBindingCatalog.computeDigest(binding))
                .isEqualTo(EXPECTED_SEED_DIGEST);
    }
}
