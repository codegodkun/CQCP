package com.cqcp.apiserver.reviewengine;

/**
 * Stable failure reason emitted by {@link ExecutionBindingCatalog} when no
 * valid execution binding can be resolved.
 *
 * <p>Each value maps to exactly one failure condition defined in ADR-017.
 * Consumers ({@code TASK_SPEC-MVP-001-A} and later Task Creation code) must
 * depend on this enum rather than parsing the exception message.
 */
enum ExecutionBindingFailureReason {
    /** No raw rows exist for the given selector (purpose + deploymentScope + contractTypeCode). */
    NOT_FOUND,
    /** More than one enabled-and-effective candidate exists for the selector. */
    AMBIGUOUS,
    /** Raw rows exist but none is enabled AND effective (effectiveFrom ≤ now). */
    INACTIVE_OR_NOT_EFFECTIVE,
    /** Budget/model FK or copied field mismatch, legacy alias broken, or legacy module version mismatch. */
    REFERENCE_MISMATCH,
    /** Model profile not enabled, wrong scope, not default, NOT_READY, or MOCK with secretRequired=true. */
    PROFILE_NOT_READY,
    /** Parser or schema code-owned release version does not match RuntimeArtifactVersions constants. */
    RUNTIME_VERSION_MISMATCH,
    /** Re-computed content digest does not match the stored binding.contentDigest. */
    CONTENT_DIGEST_MISMATCH,
    /** Provider type is not MOCK (LOCAL/PUBLIC_OPENAI_COMPATIBLE have no authoritative readiness source in this task). */
    UNSUPPORTED_PROVIDER,
}

/**
 * Exception thrown when {@link ExecutionBindingCatalog#resolveDefault}
 * cannot select exactly one valid execution binding.
 *
 * <p>The {@link #reason()} enum is the stable contract for error mapping;
 * the message is human-oriented and must never contain secrets, connection
 * strings, or stack traces.
 */
final class ExecutionBindingResolutionException extends RuntimeException {

    private final ExecutionBindingFailureReason reason;

    ExecutionBindingResolutionException(ExecutionBindingFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    ExecutionBindingFailureReason reason() {
        return reason;
    }
}
