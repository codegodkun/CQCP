package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ExecutionBindingFailureReason.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Resolves the single valid {@link ExecutionBindingRelease} for a given
 * (purpose, deploymentScope, contractTypeCode) selector.
 *
 * <p>The resolution chain follows ADR-017 and TASK_SPEC-037-A:
 * <ol>
 *   <li>Fetch all raw rows from the repository (no SQL-level filter).</li>
 *   <li>If no raw rows exist → {@link ExecutionBindingFailureReason#NOT_FOUND}.</li>
 *   <li>Filter to enabled + effective (effectiveFrom ≤ now).</li>
 *   <li>If zero effective candidates → {@link ExecutionBindingFailureReason#INACTIVE_OR_NOT_EFFECTIVE}.</li>
 *   <li>If more than one effective candidate → {@link ExecutionBindingFailureReason#AMBIGUOUS}.</li>
 *   <li>Exactly one → validate budget/model references, provider readiness,
 *       ENGINEERING alias, legacy module versions, parser/schema code-owned
 *       release, and content digest.</li>
 * </ol>
 *
 * <p>Any validation failure throws {@link ExecutionBindingResolutionException}
 * with the appropriate stable reason.  No fallback, no {@code first()}, no
 * in-memory default is ever used.
 */
final class ExecutionBindingCatalog {

    /** Legacy alias: ENGINEERING → ENGINEERING_PROCUREMENT → v20260705.1 */
    private static final String LEGACY_CONTRACT_TYPE_CODE = "ENGINEERING";
    private static final String LEGACY_PROFILE_CODE = "ENGINEERING_PROCUREMENT";
    private static final String LEGACY_MODULE_VERSION = "v20260705.1";

    private static final String LEGACY_BUDGET_PROFILE_CODE = "STANDARD";
    private static final String DEMO_SCOPE = "DEMO";
    private static final String MOCK_PROVIDER = "MOCK";
    private static final String READY_STATUS = "READY";

    private static final JsonMapper DIGEST_MAPPER = new JsonMapper();

    private final JdbcExecutionBindingRepository repository;
    private final Clock clock;

    ExecutionBindingCatalog(JdbcExecutionBindingRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Resolve the single valid execution binding for the given selector.
     *
     * @param purpose           e.g. {@code MVP_DEMO}
     * @param deploymentScope   e.g. {@code DEMO}
     * @param contractTypeCode  e.g. {@code ENGINEERING}
     * @return the validated binding
     * @throws ExecutionBindingResolutionException if resolution fails for any reason
     */
    ExecutionBindingRelease resolveDefault(String purpose, String deploymentScope, String contractTypeCode) {
        List<JdbcExecutionBindingRepository.BindingCandidate> candidates =
                repository.findBySelector(purpose, deploymentScope, contractTypeCode);

        // 1. No raw rows at all
        if (candidates.isEmpty()) {
            throw new ExecutionBindingResolutionException(NOT_FOUND,
                    "No execution_binding_release rows for selector: "
                            + "purpose=" + purpose + ", deploymentScope=" + deploymentScope
                            + ", contractTypeCode=" + contractTypeCode);
        }

        // 2. Filter to enabled + effective
        var now = clock.instant();
        var effective = candidates.stream()
                .filter(c -> c.binding().enabled()
                        && c.binding().effectiveFrom() != null
                        && !c.binding().effectiveFrom().isAfter(now))
                .toList();

        if (effective.isEmpty()) {
            throw new ExecutionBindingResolutionException(INACTIVE_OR_NOT_EFFECTIVE,
                    "No enabled and effective binding for selector after filtering "
                            + candidates.size() + " raw rows");
        }

        if (effective.size() > 1) {
            throw new ExecutionBindingResolutionException(AMBIGUOUS,
                    "Multiple (" + effective.size() + ") enabled and effective bindings for selector");
        }

        var candidate = effective.getFirst();
        var binding = candidate.binding();

        // 3. Domain guard — only MVP_DEMO / DEMO / ENGINEERING is supported in this task.
        //     If a misconfigured binding with a different selector somehow reaches the
        //     resolver (e.g. via DB error or manual INSERT with wrong domain fields),
        //     it must be rejected.  This ensures the DEMO MOCK profile is never returned
        //     for a non-MVP_DEMO, non-DEMO, or non-ENGINEERING selector.
        if (!"MVP_DEMO".equals(binding.purpose())
                || !"DEMO".equals(binding.deploymentScope())
                || !"ENGINEERING".equals(binding.contractTypeCode())) {
            throw new ExecutionBindingResolutionException(REFERENCE_MISMATCH,
                    "Binding domain not supported: purpose=" + binding.purpose()
                            + ", deploymentScope=" + binding.deploymentScope()
                            + ", contractTypeCode=" + binding.contractTypeCode());
        }

        // 4. Budget reference validation
        if (candidate.budget() == null
                || !LEGACY_BUDGET_PROFILE_CODE.equals(candidate.budget().profileCode())
                || !candidate.budget().enabled()) {
            throw new ExecutionBindingResolutionException(REFERENCE_MISMATCH,
                    "Budget profile not STANDARD or not enabled for binding " + binding.bindingVersion());
        }

        // 5. Model config reference validation
        var model = candidate.model();
        if (model == null) {
            throw new ExecutionBindingResolutionException(REFERENCE_MISMATCH,
                    "Referenced model_profile_config_version row not found for binding "
                            + binding.bindingVersion());
        }

        // 6. Model enabled / scope / default checks (PROFILE_NOT_READY)
        if (!model.enabled()) {
            throw new ExecutionBindingResolutionException(PROFILE_NOT_READY,
                    "Model profile not enabled for binding " + binding.bindingVersion());
        }
        if (!DEMO_SCOPE.equals(model.usageScope())) {
            throw new ExecutionBindingResolutionException(PROFILE_NOT_READY,
                    "Model usageScope is " + model.usageScope() + " but DEMO is required");
        }
        if (!model.isDefaultForNewTask()) {
            throw new ExecutionBindingResolutionException(PROFILE_NOT_READY,
                    "Model profile is not the default for new tasks");
        }
        if (!READY_STATUS.equals(model.readinessStatus())) {
            throw new ExecutionBindingResolutionException(PROFILE_NOT_READY,
                    "Model readinessStatus is " + model.readinessStatus() + " but READY is required");
        }

        // 7. Provider readiness matrix
        if (MOCK_PROVIDER.equals(binding.providerType())) {
            if (model.secretRequired()) {
                throw new ExecutionBindingResolutionException(PROFILE_NOT_READY,
                        "MOCK provider must have secretRequired=false");
            }
        } else {
            // LOCAL / PUBLIC_OPENAI_COMPATIBLE — no authoritative readiness source in this task
            throw new ExecutionBindingResolutionException(UNSUPPORTED_PROVIDER,
                    "Provider type " + binding.providerType() + " is not supported in this task");
        }

        // 8. Copied field consistency (REFERENCE_MISMATCH)
        checkFieldMatch(binding.providerType(), model.providerType(), "providerType", binding);
        checkFieldMatch(binding.modelName(), model.modelName(), "modelName", binding);
        checkFieldMatch(binding.endpointAlias(), model.endpointAlias(), "endpointAlias", binding);

        // 9. ENGINEERING legacy alias (REFERENCE_MISMATCH)
        // Always checked unconditionally; the domain guard above guarantees
        // contractTypeCode == ENGINEERING when this code is reached, so there
        // is no way to skip it via a different contractTypeCode.
        if (!LEGACY_PROFILE_CODE.equals(binding.contractTypeProfileCode())) {
            throw new ExecutionBindingResolutionException(REFERENCE_MISMATCH,
                    "ENGINEERING binding must have contractTypeProfileCode="
                            + LEGACY_PROFILE_CODE + " but got " + binding.contractTypeProfileCode());
        }
        checkVersionMatch(binding.contractTypeProfileVersion(), LEGACY_MODULE_VERSION,
                "contractTypeProfileVersion", binding);

        // 10. Legacy six module version consistency (REFERENCE_MISMATCH)
        checkVersionMatch(binding.ruleSetVersion(), LEGACY_MODULE_VERSION, "ruleSetVersion", binding);
        checkVersionMatch(binding.promptVersion(), LEGACY_MODULE_VERSION, "promptVersion", binding);
        checkVersionMatch(binding.patternLibraryVersion(), LEGACY_MODULE_VERSION, "patternLibraryVersion", binding);
        checkVersionMatch(binding.fieldLexiconVersion(), LEGACY_MODULE_VERSION, "fieldLexiconVersion", binding);
        checkVersionMatch(binding.evidenceSelectorVersion(), LEGACY_MODULE_VERSION, "evidenceSelectorVersion", binding);

        // 11. Parser/schema code-owned release (RUNTIME_VERSION_MISMATCH)
        if (!RuntimeArtifactVersions.PARSER_VERSION.equals(binding.parserVersion())) {
            throw new ExecutionBindingResolutionException(RUNTIME_VERSION_MISMATCH,
                    "Parser version mismatch: binding=" + binding.parserVersion()
                            + ", expected=" + RuntimeArtifactVersions.PARSER_VERSION);
        }
        if (!RuntimeArtifactVersions.SCHEMA_VERSION.equals(binding.schemaVersion())) {
            throw new ExecutionBindingResolutionException(RUNTIME_VERSION_MISMATCH,
                    "Schema version mismatch: binding=" + binding.schemaVersion()
                            + ", expected=" + RuntimeArtifactVersions.SCHEMA_VERSION);
        }

        // 12. Content digest verification (CONTENT_DIGEST_MISMATCH)
        var computedDigest = computeDigest(binding);
        if (!binding.contentDigest().equals(computedDigest)) {
            throw new ExecutionBindingResolutionException(CONTENT_DIGEST_MISMATCH,
                    "Content digest mismatch: stored=" + binding.contentDigest()
                            + ", computed=" + computedDigest);
        }

        return binding;
    }

    private static void checkFieldMatch(String actual, String expected, String fieldName,
                                        ExecutionBindingRelease binding) {
        if (!Objects.equals(actual, expected)) {
            throw new ExecutionBindingResolutionException(REFERENCE_MISMATCH,
                    fieldName + " mismatch: binding=" + actual + ", model=" + expected
                            + " for binding " + binding.bindingVersion());
        }
    }

    private static void checkVersionMatch(String actual, String expected, String fieldName,
                                          ExecutionBindingRelease binding) {
        if (!expected.equals(actual)) {
            throw new ExecutionBindingResolutionException(REFERENCE_MISMATCH,
                    fieldName + " mismatch: binding=" + actual + ", expected=" + expected
                            + " for binding " + binding.bindingVersion());
        }
    }

    /**
     * Re-compute the SHA-256 content digest per ADR-017 fixed algorithm.
     *
     * <ol>
     *   <li>Collect the 19 digest-input strings in fixed order.</li>
     *   <li>Serialize as a compact (no whitespace) JSON string array using a
     *       dedicated Jackson {@link JsonMapper} with default configuration,
     *       independent of any application-wide {@code ObjectMapper} settings.</li>
     *   <li>Encode the JSON as UTF-8 bytes.</li>
     *   <li>Compute SHA-256 and format as lowercase 64-char hex.</li>
     * </ol>
     */
    static String computeDigest(ExecutionBindingRelease binding) {
        try {
            var inputs = binding.digestInputs();
            var jsonBytes = DIGEST_MAPPER.writeValueAsBytes(inputs);
            var digest = MessageDigest.getInstance("SHA-256").digest(jsonBytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java 21 runtime has SHA-256
            throw new RuntimeException("SHA-256 not available", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute content digest", e);
        }
    }
}
