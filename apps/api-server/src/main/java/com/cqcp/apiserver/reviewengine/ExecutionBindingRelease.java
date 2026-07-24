package com.cqcp.apiserver.reviewengine;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record representing one row of the {@code execution_binding_release} table.
 *
 * <p>Each binding carries metadata (version identity, selector, lifecycle state,
 * content digest) together with the full 14-field tuple that V1 {@code execution}
 * requires as {@code NOT NULL}. A binding is selected by
 * {@link ExecutionBindingCatalog#resolveDefault} and the 14 fields are copied
 * into the new execution row at Task Creation time.
 *
 * <p>Content-immutable fields are set at construction time and never mutated.
 * Lifecycle state ({@link #enabled}) is read from the database but must only
 * be changed via controlled transaction (not through this record).
 *
 * @param bindingVersion              Unique version string, e.g. {@code mvp-demo-engineering-v20260724.1}
 * @param purpose                     Purpose classification, e.g. {@code MVP_DEMO}
 * @param deploymentScope             Deployment scope, e.g. {@code DEMO}
 * @param contractTypeCode            External contract type code, e.g. {@code ENGINEERING}
 * @param contractTypeProfileCode     Legacy profile code for traceability, e.g. {@code ENGINEERING_PROCUREMENT}
 * @param enabled                     Lifecycle state — whether this binding is active for new resolutions
 * @param effectiveFrom               Earliest instant at which this binding can be selected (resolver checks {@code Clock})
 * @param contentDigest               SHA-256 lowercase hex over the 19 digest-input fields (see {@link #digestInputs()})
 * @param contractTypeProfileVersion  Version of the contract-type profile (V1 execution field)
 * @param ruleSetVersion              Version of the rule set (V1 execution field)
 * @param reviewBudgetProfileVersion  Version of the review budget profile (V1 execution field)
 * @param modelProfileCode            Model profile code (V1 execution field)
 * @param modelConfigVersion          Model config version (V1 execution field; maps to snapshot modelProfileVersion)
 * @param parserVersion               Parser version (V1 execution field)
 * @param promptVersion               Prompt version (V1 execution field)
 * @param schemaVersion               Schema version (V1 execution field)
 * @param patternLibraryVersion       Pattern library version (V1 execution field)
 * @param fieldLexiconVersion         Field lexicon version (V1 execution field)
 * @param evidenceSelectorVersion     Evidence selector version (V1 execution field)
 * @param providerType                Provider type, e.g. {@code MOCK} (V1 execution field)
 * @param modelName                   Model name (V1 execution field)
 * @param endpointAlias               Endpoint alias (V1 execution field)
 */
record ExecutionBindingRelease(
        String bindingVersion,
        String purpose,
        String deploymentScope,
        String contractTypeCode,
        String contractTypeProfileCode,
        boolean enabled,
        Instant effectiveFrom,
        String contentDigest,
        // 14 V1 execution NOT NULL fields
        String contractTypeProfileVersion,
        String ruleSetVersion,
        String reviewBudgetProfileVersion,
        String modelProfileCode,
        String modelConfigVersion,
        String parserVersion,
        String promptVersion,
        String schemaVersion,
        String patternLibraryVersion,
        String fieldLexiconVersion,
        String evidenceSelectorVersion,
        String providerType,
        String modelName,
        String endpointAlias) {

    // Compact canonical constructor — rejects null metadata and any null version/model field
    ExecutionBindingRelease {
        Objects.requireNonNull(bindingVersion, "bindingVersion");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(deploymentScope, "deploymentScope");
        Objects.requireNonNull(contractTypeCode, "contractTypeCode");
        Objects.requireNonNull(contractTypeProfileCode, "contractTypeProfileCode");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(contentDigest, "contentDigest");
        Objects.requireNonNull(contractTypeProfileVersion, "contractTypeProfileVersion");
        Objects.requireNonNull(ruleSetVersion, "ruleSetVersion");
        Objects.requireNonNull(reviewBudgetProfileVersion, "reviewBudgetProfileVersion");
        Objects.requireNonNull(modelProfileCode, "modelProfileCode");
        Objects.requireNonNull(modelConfigVersion, "modelConfigVersion");
        Objects.requireNonNull(parserVersion, "parserVersion");
        Objects.requireNonNull(promptVersion, "promptVersion");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(patternLibraryVersion, "patternLibraryVersion");
        Objects.requireNonNull(fieldLexiconVersion, "fieldLexiconVersion");
        Objects.requireNonNull(evidenceSelectorVersion, "evidenceSelectorVersion");
        Objects.requireNonNull(providerType, "providerType");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(endpointAlias, "endpointAlias");
    }

    /**
     * Returns the 10-field {@link VersionReferences} expected by {@link ResultComposer}.
     *
     * <p>The V1 mapping {@code execution.model_config_version == review_result_snapshot.model_profile_version}
     * is realised here: {@code modelProfileVersion} is set to {@link #modelConfigVersion()}.
     */
    VersionReferences versionReferences() {
        return new VersionReferences(
                contractTypeProfileVersion(),
                ruleSetVersion(),
                reviewBudgetProfileVersion(),
                modelConfigVersion(), // modelProfileVersion = modelConfigVersion
                parserVersion(),
                promptVersion(),
                schemaVersion(),
                patternLibraryVersion(),
                fieldLexiconVersion(),
                evidenceSelectorVersion());
    }

    /**
     * The 19 digest-input fields in ADR-017 fixed order.
     *
     * <p>Lifecycle/audit fields ({@code enabled}, {@code effectiveFrom}, {@code contentDigest})
     * and JSONB ({@code modelBudget}) do not participate.
     */
    String[] digestInputs() {
        return new String[]{
                bindingVersion(),
                purpose(),
                deploymentScope(),
                contractTypeCode(),
                contractTypeProfileCode(),
                contractTypeProfileVersion(),
                ruleSetVersion(),
                reviewBudgetProfileVersion(),
                modelProfileCode(),
                modelConfigVersion(),
                parserVersion(),
                promptVersion(),
                schemaVersion(),
                patternLibraryVersion(),
                fieldLexiconVersion(),
                evidenceSelectorVersion(),
                providerType(),
                modelName(),
                endpointAlias(),
        };
    }
}
