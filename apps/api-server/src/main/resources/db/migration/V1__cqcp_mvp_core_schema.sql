CREATE TABLE task (
    task_id VARCHAR(64) PRIMARY KEY,
    caller_id VARCHAR(128),
    caller_type VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    contract_name VARCHAR(255) NOT NULL,
    contract_type_code VARCHAR(64),
    result_url TEXT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    contract_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    structured_fields_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE execution (
    execution_id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task (task_id),
    status VARCHAR(32) NOT NULL,
    current_stage VARCHAR(64) NOT NULL,
    supersedes_execution_id VARCHAR(64),
    contract_type_profile_version VARCHAR(64) NOT NULL,
    rule_set_version VARCHAR(64) NOT NULL,
    review_budget_profile_version VARCHAR(64) NOT NULL,
    model_profile_code VARCHAR(64) NOT NULL,
    model_config_version VARCHAR(64) NOT NULL,
    parser_version VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    pattern_library_version VARCHAR(64) NOT NULL,
    field_lexicon_version VARCHAR(64) NOT NULL,
    evidence_selector_version VARCHAR(64) NOT NULL,
    provider_type VARCHAR(32) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    endpoint_alias VARCHAR(128) NOT NULL,
    stage_lease_owner VARCHAR(128),
    stage_lease_acquired_at TIMESTAMPTZ,
    stage_lease_expires_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (
        status IN (
            'CREATED',
            'QUEUED',
            'PARSING',
            'INDEXING',
            'PLANNING',
            'BUILDING_EVIDENCE',
            'REVIEWING_RULES',
            'REVIEWING_MODEL',
            'COMPOSING',
            'SUCCESS',
            'PARTIAL_SUCCESS',
            'FAILED',
            'CANCELLED'
        )
    )
);

ALTER TABLE execution
    ADD CONSTRAINT uq_execution_task_execution
    UNIQUE (task_id, execution_id);

ALTER TABLE execution
    ADD CONSTRAINT fk_execution_supersedes_execution
    FOREIGN KEY (supersedes_execution_id) REFERENCES execution (execution_id);

CREATE TABLE task_stage_log (
    task_stage_log_id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task (task_id),
    execution_id VARCHAR(64) NOT NULL REFERENCES execution (execution_id),
    stage_name VARCHAR(64) NOT NULL,
    attempt INTEGER NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    summary_status VARCHAR(32) NOT NULL,
    business_reason TEXT,
    diagnostic_code VARCHAR(64),
    duration_ms BIGINT,
    detail_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (attempt >= 1)
);

ALTER TABLE task_stage_log
    ADD CONSTRAINT fk_task_stage_log_task_execution
    FOREIGN KEY (task_id, execution_id) REFERENCES execution (task_id, execution_id);

CREATE TABLE review_result_snapshot (
    task_id VARCHAR(64) NOT NULL REFERENCES task (task_id),
    execution_id VARCHAR(64) PRIMARY KEY REFERENCES execution (execution_id),
    superseded_by_execution_id VARCHAR(64) REFERENCES execution (execution_id),
    superseded_reason VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    review_completeness JSONB NOT NULL DEFAULT '{}'::jsonb,
    point_results JSONB NOT NULL DEFAULT '[]'::jsonb,
    findings JSONB NOT NULL DEFAULT '[]'::jsonb,
    diagnostics JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_anchors JSONB NOT NULL DEFAULT '[]'::jsonb,
    structured_fields_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled_review_points_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    disabled_review_points_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    contract_type_profile_version VARCHAR(64) NOT NULL,
    rule_set_version VARCHAR(64) NOT NULL,
    review_budget_profile_version VARCHAR(64) NOT NULL,
    model_profile_version VARCHAR(64) NOT NULL,
    parser_version VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    pattern_library_version VARCHAR(64) NOT NULL,
    field_lexicon_version VARCHAR(64) NOT NULL,
    evidence_selector_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (
        status IN (
            'SUCCESS',
            'PARTIAL_SUCCESS',
            'FAILED'
        )
    )
);

ALTER TABLE review_result_snapshot
    ADD CONSTRAINT fk_review_result_snapshot_task_execution
    FOREIGN KEY (task_id, execution_id) REFERENCES execution (task_id, execution_id);

CREATE TABLE tuning_packet (
    tuning_packet_id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task (task_id),
    execution_id VARCHAR(64) NOT NULL REFERENCES execution (execution_id),
    export_mode VARCHAR(32) NOT NULL,
    snapshot_status VARCHAR(32) NOT NULL,
    export_config_version VARCHAR(64) NOT NULL,
    snapshot_created_at TIMESTAMPTZ NOT NULL,
    execution_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    evidence_summary JSONB NOT NULL DEFAULT '[]'::jsonb,
    version_references JSONB NOT NULL DEFAULT '{}'::jsonb,
    redacted_context JSONB NOT NULL DEFAULT '{}'::jsonb,
    external_advice_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (
        export_mode IN (
            'SINGLE_POINT',
            'FOCUSED'
        )
    )
);

ALTER TABLE tuning_packet
    ADD CONSTRAINT fk_tuning_packet_task_execution
    FOREIGN KEY (task_id, execution_id) REFERENCES execution (task_id, execution_id);

CREATE TABLE point_diagnostic (
    point_diagnostic_id BIGSERIAL PRIMARY KEY,
    tuning_packet_id VARCHAR(64) NOT NULL REFERENCES tuning_packet (tuning_packet_id),
    execution_id VARCHAR(64) NOT NULL REFERENCES execution (execution_id),
    review_point_code VARCHAR(64) NOT NULL,
    business_reason VARCHAR(64),
    suspected_failure_classes JSONB NOT NULL DEFAULT '[]'::jsonb,
    missing_slots JSONB NOT NULL DEFAULT '[]'::jsonb,
    candidate_ambiguity_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    model_call_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    diagnostic_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_execution_task_created_at
    ON execution (task_id, created_at DESC);

CREATE UNIQUE INDEX uq_execution_single_non_terminal
    ON execution (task_id)
    WHERE status IN (
        'CREATED',
        'QUEUED',
        'PARSING',
        'INDEXING',
        'PLANNING',
        'BUILDING_EVIDENCE',
        'REVIEWING_RULES',
        'REVIEWING_MODEL',
        'COMPOSING'
    );

CREATE INDEX idx_task_stage_log_execution_created_at
    ON task_stage_log (execution_id, created_at DESC);

CREATE INDEX idx_review_result_snapshot_task_created_at
    ON review_result_snapshot (task_id, created_at DESC);

CREATE INDEX idx_review_result_snapshot_task_execution
    ON review_result_snapshot (task_id, execution_id);

CREATE INDEX idx_review_result_snapshot_latest_non_superseded
    ON review_result_snapshot (task_id, created_at DESC)
    WHERE superseded_by_execution_id IS NULL;

CREATE INDEX idx_tuning_packet_execution_created_at
    ON tuning_packet (execution_id, created_at DESC);

CREATE INDEX idx_point_diagnostic_tuning_packet
    ON point_diagnostic (tuning_packet_id, review_point_code);
