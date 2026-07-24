package com.cqcp.apiserver.reviewengine;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC repository for {@code execution_binding_release} queries.
 *
 * <p>Returns all raw rows matching the given selector (purpose + deploymentScope +
 * contractTypeCode) together with their referenced budget and model config rows.
 * The SQL does <strong>not</strong> filter by {@code enabled} or {@code effective_from};
 * that filtering is the responsibility of {@link ExecutionBindingCatalog}.
 */
@Repository
class JdbcExecutionBindingRepository {

    private static final String SELECT_BY_SELECTOR_SQL = """
            SELECT
                b.binding_version,
                b.purpose,
                b.deployment_scope,
                b.contract_type_code,
                b.contract_type_profile_code,
                b.enabled,
                b.effective_from,
                b.content_digest,
                b.contract_type_profile_version,
                b.rule_set_version,
                b.review_budget_profile_version,
                b.model_profile_code,
                b.model_config_version,
                b.parser_version,
                b.prompt_version,
                b.schema_version,
                b.pattern_library_version,
                b.field_lexicon_version,
                b.evidence_selector_version,
                b.provider_type,
                b.model_name,
                b.endpoint_alias,
                /* budget row */
                bg.profile_code         AS budget_profile_code,
                bg.enabled              AS budget_enabled,
                /* model config row */
                mc.provider_type        AS mc_provider_type,
                mc.model_name           AS mc_model_name,
                mc.endpoint_alias       AS mc_endpoint_alias,
                mc.enabled              AS mc_enabled,
                mc.usage_scope,
                mc.is_default_for_new_task,
                mc.secret_required,
                mc.readiness_status
            FROM execution_binding_release b
            LEFT JOIN review_budget_profile_version bg
                ON bg.review_budget_profile_version = b.review_budget_profile_version
            LEFT JOIN model_profile_config_version mc
                ON mc.profile_code = b.model_profile_code
                AND mc.config_version = b.model_config_version
            WHERE b.purpose = ?
              AND b.deployment_scope = ?
              AND b.contract_type_code = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    JdbcExecutionBindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    /**
     * Returns all raw binding rows matching the selector, along with their
     * referenced budget and model-config data.  The query does <em>not</em>
     * apply any {@code enabled} or {@code effective_from} filter — that is
     * left to the caller ({@link ExecutionBindingCatalog}).
     */
    List<BindingCandidate> findBySelector(String purpose, String deploymentScope, String contractTypeCode) {
        return jdbcTemplate.query(
                SELECT_BY_SELECTOR_SQL,
                this::mapRow,
                purpose,
                deploymentScope,
                contractTypeCode);
    }

    private BindingCandidate mapRow(ResultSet rs, int rowNum) throws SQLException {
        var binding = new ExecutionBindingRelease(
                rs.getString("binding_version"),
                rs.getString("purpose"),
                rs.getString("deployment_scope"),
                rs.getString("contract_type_code"),
                rs.getString("contract_type_profile_code"),
                rs.getBoolean("enabled"),
                toInstant(rs.getTimestamp("effective_from")),
                rs.getString("content_digest"),
                rs.getString("contract_type_profile_version"),
                rs.getString("rule_set_version"),
                rs.getString("review_budget_profile_version"),
                rs.getString("model_profile_code"),
                rs.getString("model_config_version"),
                rs.getString("parser_version"),
                rs.getString("prompt_version"),
                rs.getString("schema_version"),
                rs.getString("pattern_library_version"),
                rs.getString("field_lexicon_version"),
                rs.getString("evidence_selector_version"),
                rs.getString("provider_type"),
                rs.getString("model_name"),
                rs.getString("endpoint_alias"));

        var budgetView = new BindingCandidate.BudgetView(
                rs.getString("budget_profile_code"),
                rs.getBoolean("budget_enabled"));

        var modelView = new BindingCandidate.ModelConfigView(
                rs.getString("mc_provider_type"),
                rs.getString("mc_model_name"),
                rs.getString("mc_endpoint_alias"),
                rs.getBoolean("mc_enabled"),
                rs.getString("usage_scope"),
                rs.getBoolean("is_default_for_new_task"),
                rs.getBoolean("secret_required"),
                rs.getString("readiness_status"));

        return new BindingCandidate(binding, budgetView, modelView);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    /**
     * A binding together with the essential fields from its referenced
     * review-budget-profile and model-profile-config rows.
     */
    record BindingCandidate(
            ExecutionBindingRelease binding,
            BudgetView budget,
            ModelConfigView model) {

        record BudgetView(String profileCode, boolean enabled) {}

        record ModelConfigView(
                String providerType,
                String modelName,
                String endpointAlias,
                boolean enabled,
                String usageScope,
                boolean isDefaultForNewTask,
                boolean secretRequired,
                String readinessStatus) {}
    }
}
