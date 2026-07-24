package com.cqcp.apiserver.reviewengine;

/**
 * Code-owned immutable release constants for parser and model-output schema.
 *
 * <p>These version strings are independent of {@code openapi.yaml} version, fixture
 * version, and {@code RuleSetVersion}. They are the authoritative source that the
 * {@link ExecutionBindingCatalog} checks at resolution time; if the binding's
 * stored value does not match, the resolver fails closed with
 * {@link ExecutionBindingFailureReason#RUNTIME_VERSION_MISMATCH}.
 *
 * <p>This class is a final utility container and is not meant to be instantiated.
 */
public final class RuntimeArtifactVersions {

    /** Parser release for the current code-owned DOCX word-parser path. */
    public static final String PARSER_VERSION = "parser-docx-word-v20260724.1";

    /** Model-output artifact schema release for the current code-owned schema. */
    public static final String SCHEMA_VERSION = "model-output-artifact-v20260724.1";

    private RuntimeArtifactVersions() {
        throw new AssertionError("no instances");
    }
}
