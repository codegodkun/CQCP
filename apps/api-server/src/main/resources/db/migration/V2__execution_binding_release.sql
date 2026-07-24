-- V2: Execution Binding Release, Review Budget Profile, Model Profile Config
--
-- ADR-017: 新增三类不可变版本表，为 V1 execution 提供权威 binding source。
-- lifecycle state (enabled / is_default_for_new_task / readiness_status) 可受控切换，
-- content-immutable 字段不可原地修改。
--
-- 本迁移不修改 V1 表、不启用 review-assets runtime loader、不实现发布 API。

-- ============================================================
-- 1. review_budget_profile_version
-- ============================================================
CREATE TABLE review_budget_profile_version (
    review_budget_profile_version VARCHAR(128) PRIMARY KEY,
    profile_code                    VARCHAR(64)  NOT NULL,
    display_name                    VARCHAR(255) NOT NULL,
    model_budget                    JSONB        NOT NULL,
    standard_ratio                  INTEGER      NOT NULL,
    deep_review_ratio               INTEGER      NOT NULL,
    budget_approval_policy_version  VARCHAR(128) NOT NULL,
    enabled                         BOOLEAN      NOT NULL,
    effective_from                  TIMESTAMPTZ  NOT NULL,
    created_at                      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_budget_profile_code CHECK
        (profile_code IN ('STANDARD', 'DEEP_REVIEW', 'EVALUATION')),
    CONSTRAINT chk_budget_ratio_positive CHECK
        (standard_ratio > 0 AND deep_review_ratio > 0)
);

COMMENT ON TABLE review_budget_profile_version IS
    'Review budget profile — content immutable, lifecycle (enabled) mutable.';
COMMENT ON COLUMN review_budget_profile_version.model_budget IS
    'JSONB with fields: maxInputTokensPerCall, maxSharedEvidenceTokens, maxPointOverlayTokens, maxStructuredFieldTokens, maxInstructionAndSchemaTokens, maxOutputTokens, maxModelCallsPerTask, maxModelCallsPerFamily.';

-- 仅 STANDARD 启用；DEEP_REVIEW / EVALUATION 完成结构性 seed 但不可作为 Demo 默认。
INSERT INTO review_budget_profile_version (
    review_budget_profile_version, profile_code, display_name,
    model_budget, standard_ratio, deep_review_ratio,
    budget_approval_policy_version, enabled, effective_from, created_at
) VALUES
    ('budget-standard-v20260724.1', 'STANDARD', '标准审核',
     '{
       "maxInputTokensPerCall": 12000,
       "maxSharedEvidenceTokens": 7000,
       "maxPointOverlayTokens": 1500,
       "maxStructuredFieldTokens": 1000,
       "maxInstructionAndSchemaTokens": 1000,
       "maxOutputTokens": 1500,
       "maxModelCallsPerTask": 4,
       "maxModelCallsPerFamily": 1
     }'::jsonb,
     5, 1, 'budget-approval-policy-mvp-v20260724.1',
     true, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z'),

    ('budget-deep-review-v20260724.1', 'DEEP_REVIEW', '深度审核（预留）',
     '{
       "maxInputTokensPerCall": 12000,
       "maxSharedEvidenceTokens": 7000,
       "maxPointOverlayTokens": 1500,
       "maxStructuredFieldTokens": 1000,
       "maxInstructionAndSchemaTokens": 1000,
       "maxOutputTokens": 1500,
       "maxModelCallsPerTask": 4,
       "maxModelCallsPerFamily": 1
     }'::jsonb,
     5, 1, 'budget-approval-policy-mvp-v20260724.1',
     false, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z'),

    ('budget-evaluation-v20260724.1', 'EVALUATION', '评测（预留）',
     '{
       "maxInputTokensPerCall": 12000,
       "maxSharedEvidenceTokens": 7000,
       "maxPointOverlayTokens": 1500,
       "maxStructuredFieldTokens": 1000,
       "maxInstructionAndSchemaTokens": 1000,
       "maxOutputTokens": 1500,
       "maxModelCallsPerTask": 4,
       "maxModelCallsPerFamily": 1
     }'::jsonb,
     5, 1, 'budget-approval-policy-mvp-v20260724.1',
     false, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z');

COMMENT ON COLUMN review_budget_profile_version.model_budget IS
    '三类 seed 共享架构启动基线数值，不表示差异化质量或 SLA 承诺。差异化必须通过新版本发布并提供评测/批准证据。';

-- ============================================================
-- 2. model_profile_config_version
-- ============================================================
CREATE TABLE model_profile_config_version (
    profile_code            VARCHAR(64)  NOT NULL,
    config_version          VARCHAR(128) NOT NULL,
    display_name            VARCHAR(255) NOT NULL,
    provider_type           VARCHAR(32)  NOT NULL,
    endpoint_alias          VARCHAR(128) NOT NULL,
    model_name              VARCHAR(128) NOT NULL,
    enabled                 BOOLEAN      NOT NULL,
    usage_scope             VARCHAR(32)  NOT NULL,
    is_default_for_new_task BOOLEAN      NOT NULL,
    secret_required         BOOLEAN      NOT NULL,
    readiness_status        VARCHAR(16)  NOT NULL,
    timeout_seconds         INTEGER      NOT NULL,
    retry_count             INTEGER      NOT NULL,
    effective_from          TIMESTAMPTZ  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (profile_code, config_version),
    CONSTRAINT chk_model_provider_type CHECK
        (provider_type IN ('LOCAL', 'PUBLIC_OPENAI_COMPATIBLE', 'MOCK')),
    CONSTRAINT chk_model_usage_scope CHECK
        (usage_scope IN ('DEMO', 'EVALUATION', 'INTERNAL_REVIEW', 'PRODUCTION_REVIEW')),
    CONSTRAINT chk_model_readiness CHECK
        (readiness_status IN ('READY', 'NOT_READY'))
);

COMMENT ON TABLE model_profile_config_version IS
    'Model profile config — content immutable, lifecycle (enabled / is_default_for_new_task / readiness_status) mutable.';

-- 每个 usage_scope 最多一个 enabled + is_default_for_new_task
CREATE UNIQUE INDEX uq_model_profile_default_per_scope
    ON model_profile_config_version (usage_scope)
    WHERE enabled = true AND is_default_for_new_task = true;

INSERT INTO model_profile_config_version (
    profile_code, config_version, display_name,
    provider_type, endpoint_alias, model_name,
    enabled, usage_scope, is_default_for_new_task,
    secret_required, readiness_status,
    timeout_seconds, retry_count,
    effective_from, created_at
) VALUES (
    'MVP_DEMO_MOCK', 'model-config-mvp-demo-mock-v20260724.1', 'MVP Demo Mock',
    'MOCK', 'mock-local', 'cqcp-demo-mock',
    true, 'DEMO', true,
    false, 'READY',
    30, 0,
    '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z'
);

-- ============================================================
-- 3. execution_binding_release
-- ============================================================
CREATE TABLE execution_binding_release (
    binding_version                 VARCHAR(128) PRIMARY KEY,
    purpose                         VARCHAR(64)  NOT NULL,
    deployment_scope                VARCHAR(64)  NOT NULL,
    contract_type_code              VARCHAR(64)  NOT NULL,
    contract_type_profile_code      VARCHAR(64)  NOT NULL,
    enabled                         BOOLEAN      NOT NULL,
    effective_from                  TIMESTAMPTZ  NOT NULL,
    content_digest                  VARCHAR(64)  NOT NULL,

    -- V1 execution 14 NOT NULL fields
    contract_type_profile_version   VARCHAR(128) NOT NULL,
    rule_set_version                VARCHAR(128) NOT NULL,
    review_budget_profile_version   VARCHAR(128) NOT NULL,
    model_profile_code              VARCHAR(64)  NOT NULL,
    model_config_version            VARCHAR(128) NOT NULL,
    parser_version                  VARCHAR(128) NOT NULL,
    prompt_version                  VARCHAR(128) NOT NULL,
    schema_version                  VARCHAR(128) NOT NULL,
    pattern_library_version         VARCHAR(128) NOT NULL,
    field_lexicon_version           VARCHAR(128) NOT NULL,
    evidence_selector_version       VARCHAR(128) NOT NULL,
    provider_type                   VARCHAR(32)  NOT NULL,
    model_name                      VARCHAR(128) NOT NULL,
    endpoint_alias                  VARCHAR(128) NOT NULL,

    -- Foreign keys
    CONSTRAINT fk_binding_budget
        FOREIGN KEY (review_budget_profile_version)
        REFERENCES review_budget_profile_version (review_budget_profile_version),
    CONSTRAINT fk_binding_model
        FOREIGN KEY (model_profile_code, model_config_version)
        REFERENCES model_profile_config_version (profile_code, config_version),

    -- content_digest hex shape validation (Java Catalog recomputes and verifies full value)
    CONSTRAINT chk_content_digest_hex CHECK
        (content_digest ~ '^[a-f0-9]{64}$')
);

COMMENT ON TABLE execution_binding_release IS
    'Deployment-level immutable execution binding — selects all 14 V1 NOT NULL version/model fields in one row. Content immutable; enabled is lifecycle state. Not a fourth business-configurable package type.';

-- Same selector may have at most one enabled row
CREATE UNIQUE INDEX uq_execution_binding_release_active
    ON execution_binding_release (purpose, deployment_scope, contract_type_code)
    WHERE enabled = true;

-- Seed: single MVP_DEMO / DEMO / ENGINEERING binding
INSERT INTO execution_binding_release (
    binding_version, purpose, deployment_scope,
    contract_type_code, contract_type_profile_code,
    enabled, effective_from, content_digest,
    contract_type_profile_version, rule_set_version, review_budget_profile_version,
    model_profile_code, model_config_version,
    parser_version, prompt_version, schema_version,
    pattern_library_version, field_lexicon_version, evidence_selector_version,
    provider_type, model_name, endpoint_alias
) VALUES (
    'mvp-demo-engineering-v20260724.1',
    'MVP_DEMO', 'DEMO',
    'ENGINEERING', 'ENGINEERING_PROCUREMENT',
    true, '2026-07-24T00:00:00Z',
    -- content_digest: SHA-256 of compact JSON array of 19 fields in ADR-017 fixed order,
    -- serialised with default Jackson JsonMapper as UTF-8 compact JSON array.
    -- Independent pre-computed value; Java ExecutionBindingCatalog recomputes at resolution
    -- time and must match.  If the 19 fields, their order, or the serialisation changes,
    -- this value MUST be recomputed from the production Java algorithm.
    'd79d1d7056d15eb9ed189cabc9980ea000c4188fbd725f9b5c847727488f658b',

    'v20260705.1', 'v20260705.1', 'budget-standard-v20260724.1',
    'MVP_DEMO_MOCK', 'model-config-mvp-demo-mock-v20260724.1',
    'parser-docx-word-v20260724.1', 'v20260705.1', 'model-output-artifact-v20260724.1',
    'v20260705.1', 'v20260705.1', 'v20260705.1',
    'MOCK', 'cqcp-demo-mock', 'mock-local'
);
