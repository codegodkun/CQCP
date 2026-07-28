package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.ExecutionInsert;

import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC repository for inserting Task and Execution rows in the same PostgreSQL transaction.
 */
@Repository
class ReviewTaskCreationRepository {

    private static final String INSERT_TASK_SQL = """
            INSERT INTO task (
                task_id, caller_id, caller_type, source_type,
                contract_name, contract_type_code, result_url,
                currency, contract_metadata, structured_fields_snapshot
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
            """;

    private static final String INSERT_EXECUTION_SQL = """
            INSERT INTO execution (
                execution_id, task_id, status, current_stage,
                supersedes_execution_id,
                contract_type_profile_version, rule_set_version,
                review_budget_profile_version, model_profile_code,
                model_config_version, parser_version, prompt_version,
                schema_version, pattern_library_version,
                field_lexicon_version, evidence_selector_version,
                provider_type, model_name, endpoint_alias
            ) VALUES (?, ?, 'QUEUED', 'QUEUED', NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    ReviewTaskCreationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    void insertTask(String taskId, String callerId, String callerType, String sourceType,
                    String contractName, String contractTypeCode, String resultUrl,
                    String currency, String contractMetadataJson, String structuredFieldsSnapshotJson) {
        jdbcTemplate.update(INSERT_TASK_SQL,
                taskId, callerId, callerType, sourceType,
                contractName, contractTypeCode, resultUrl,
                currency, contractMetadataJson, structuredFieldsSnapshotJson);
    }

    void insertExecution(String taskId, ExecutionInsert exec) {
        jdbcTemplate.update(INSERT_EXECUTION_SQL,
                exec.executionId(), taskId,
                exec.contractTypeProfileVersion(),
                exec.ruleSetVersion(),
                exec.reviewBudgetProfileVersion(),
                exec.modelProfileCode(),
                exec.modelConfigVersion(),
                exec.parserVersion(),
                exec.promptVersion(),
                exec.schemaVersion(),
                exec.patternLibraryVersion(),
                exec.fieldLexiconVersion(),
                exec.evidenceSelectorVersion(),
                exec.providerType(),
                exec.modelName(),
                exec.endpointAlias());
    }
}
