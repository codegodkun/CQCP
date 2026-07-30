-- V3: ADR-018 Model Profile Secret Reference and provider connectivity audit.
--
-- No plaintext secret is stored. Existing MVP_DEMO_MOCK remains unchanged and
-- receives NULL secret_ref because secret_required=false.

ALTER TABLE model_profile_config_version
    ADD COLUMN secret_ref VARCHAR(255);

ALTER TABLE model_profile_config_version
    ADD CONSTRAINT chk_model_profile_secret_ref
    CHECK (
        (secret_required = false AND secret_ref IS NULL)
        OR
        (
            secret_required = true
            AND secret_ref IS NOT NULL
            AND secret_ref IN (
                'env:CQCP_MODEL_DEEPSEEK_API_KEY',
                'file:/run/secrets/cqcp-model-deepseek-api-key'
            )
        )
    );

ALTER TABLE model_profile_config_version
    ADD CONSTRAINT chk_public_model_profile_evaluation_boundary
    CHECK (
        provider_type <> 'PUBLIC_OPENAI_COMPATIBLE'
        OR
        (
            endpoint_alias = 'deepseek-official'
            AND model_name IN ('deepseek-v4-pro', 'deepseek-v4-flash')
            AND usage_scope = 'EVALUATION'
            AND enabled = false
            AND is_default_for_new_task = false
            AND secret_required = true
        )
    );

CREATE FUNCTION enforce_model_profile_config_content_immutable()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(
        NEW.profile_code,
        NEW.config_version,
        NEW.display_name,
        NEW.provider_type,
        NEW.endpoint_alias,
        NEW.model_name,
        NEW.usage_scope,
        NEW.secret_required,
        NEW.secret_ref,
        NEW.timeout_seconds,
        NEW.retry_count,
        NEW.effective_from,
        NEW.created_at
    ) IS DISTINCT FROM ROW(
        OLD.profile_code,
        OLD.config_version,
        OLD.display_name,
        OLD.provider_type,
        OLD.endpoint_alias,
        OLD.model_name,
        OLD.usage_scope,
        OLD.secret_required,
        OLD.secret_ref,
        OLD.timeout_seconds,
        OLD.retry_count,
        OLD.effective_from,
        OLD.created_at
    ) THEN
        RAISE EXCEPTION
            'model_profile_config_version content is immutable; create a new config_version'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_model_profile_config_content_immutable
    BEFORE UPDATE ON model_profile_config_version
    FOR EACH ROW
    EXECUTE FUNCTION enforce_model_profile_config_content_immutable();

CREATE INDEX idx_model_profile_latest_version
    ON model_profile_config_version (profile_code, created_at DESC, config_version DESC);

CREATE TABLE model_profile_connectivity_test (
    connectivity_test_id VARCHAR(64) PRIMARY KEY,
    profile_code VARCHAR(64) NOT NULL,
    config_version VARCHAR(128) NOT NULL,
    status VARCHAR(64) NOT NULL,
    http_status_class VARCHAR(16),
    model_available BOOLEAN NOT NULL DEFAULT false,
    duration_ms BIGINT NOT NULL,
    tested_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_model_connectivity_profile
        FOREIGN KEY (profile_code, config_version)
        REFERENCES model_profile_config_version (profile_code, config_version),
    CONSTRAINT chk_model_connectivity_duration
        CHECK (duration_ms >= 0),
    CONSTRAINT chk_model_connectivity_status
        CHECK (status IN (
            'SUCCEEDED',
            'SECRET_MISSING',
            'AUTHENTICATION_FAILED',
            'RATE_LIMITED',
            'UPSTREAM_5XX',
            'TIMEOUT',
            'REDIRECT_REJECTED',
            'MODEL_NOT_FOUND',
            'MALFORMED_RESPONSE',
            'ENDPOINT_NOT_ALLOWED',
            'NETWORK_ERROR'
        ))
);

CREATE INDEX idx_model_profile_connectivity_latest
    ON model_profile_connectivity_test (
        profile_code,
        config_version,
        tested_at DESC,
        connectivity_test_id DESC
    );

COMMENT ON COLUMN model_profile_config_version.secret_ref IS
    'Server-side env:/file: reference only. Never stores plaintext secret.';

COMMENT ON FUNCTION enforce_model_profile_config_content_immutable() IS
    'Only lifecycle fields enabled, is_default_for_new_task and readiness_status may change in place.';

COMMENT ON TABLE model_profile_connectivity_test IS
    'Stable connectivity outcome only; never stores request/response body, authorization header, prompt, secret, or stack trace.';
