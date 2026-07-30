package com.cqcp.apiserver.modelgateway;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

final class ModelProfileAdminModels {

    private ModelProfileAdminModels() {
        throw new AssertionError("no instances");
    }

    static final String PUBLIC_PROVIDER = "PUBLIC_OPENAI_COMPATIBLE";
    static final String EVALUATION_SCOPE = "EVALUATION";

    enum ConnectivityStatus {
        SUCCEEDED,
        SECRET_MISSING,
        AUTHENTICATION_FAILED,
        RATE_LIMITED,
        UPSTREAM_5XX,
        TIMEOUT,
        REDIRECT_REJECTED,
        MODEL_NOT_FOUND,
        MALFORMED_RESPONSE,
        ENDPOINT_NOT_ALLOWED,
        NETWORK_ERROR
    }

    enum ProfileReadiness {
        READY,
        SECRET_MISSING,
        NOT_TESTED,
        CONNECTIVITY_FAILED,
        READY_FOR_EVALUATION_CONFIG
    }

    record ModelProfileConfigRow(
            String profileCode,
            String configVersion,
            String displayName,
            String providerType,
            String endpointAlias,
            String modelName,
            boolean enabled,
            String usageScope,
            boolean defaultForNewTask,
            boolean secretRequired,
            String secretRef,
            String databaseReadinessStatus,
            int timeoutSeconds,
            int retryCount,
            Instant effectiveFrom,
            Instant createdAt) {
    }

    record ConnectivityRow(
            String connectivityTestId,
            String profileCode,
            String configVersion,
            ConnectivityStatus status,
            String httpStatusClass,
            boolean modelAvailable,
            long durationMs,
            Instant testedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ConnectivityView(
            String connectivityTestId,
            ConnectivityStatus status,
            String httpStatusClass,
            boolean modelAvailable,
            long durationMs,
            Instant testedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ModelProfileView(
            String profileCode,
            String configVersion,
            String displayName,
            String providerType,
            String endpointAlias,
            String modelName,
            String usageScope,
            boolean enabled,
            boolean defaultForNewTask,
            boolean secretConfigured,
            ProfileReadiness readiness,
            int timeoutSeconds,
            int retryCount,
            ConnectivityView latestConnectivityTest) {
    }

    record ModelProfileListResponse(List<ModelProfileView> items) {
        ModelProfileListResponse {
            items = List.copyOf(items);
        }
    }

    record ConnectivityOutcome(
            ConnectivityStatus status,
            String httpStatusClass,
            boolean modelAvailable,
            long durationMs) {
    }

    static final class ModelProfileAdminException extends RuntimeException {
        private final int httpStatus;
        private final String code;

        ModelProfileAdminException(int httpStatus, String code, String message) {
            super(message);
            this.httpStatus = httpStatus;
            this.code = code;
        }

        int httpStatus() {
            return httpStatus;
        }

        String code() {
            return code;
        }
    }
}
