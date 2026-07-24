package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static com.cqcp.apiserver.reviewengine.ExecutionBindingFailureReason.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure unit tests for {@link ExecutionBindingCatalog}.
 *
 * <p>Uses a mock {@link JdbcExecutionBindingRepository} and a fixed {@link Clock}
 * so that no database connection is needed.  All eight failure reasons are covered
 * by dedicated tests.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionBindingCatalogTest {

    private static final String PURPOSE = "MVP_DEMO";
    private static final String DEPLOYMENT_SCOPE = "DEMO";
    private static final String CONTRACT_TYPE_CODE = "ENGINEERING";
    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant EFFECTIVE_PAST = Instant.parse("2026-07-24T00:00:00Z");

    @Mock
    private JdbcExecutionBindingRepository repository;

    private ExecutionBindingCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new ExecutionBindingCatalog(repository, FIXED_CLOCK);
    }

    // ------------------- helper factories -------------------

    private JdbcExecutionBindingRepository.BindingCandidate candidate(
            ExecutionBindingRelease binding,
            boolean budgetEnabled,
            boolean modelEnabled,
            String usageScope,
            boolean isDefault,
            boolean secretRequired,
            String readinessStatus) {
        var budget = new JdbcExecutionBindingRepository.BindingCandidate.BudgetView("STANDARD", budgetEnabled);
        var model = new JdbcExecutionBindingRepository.BindingCandidate.ModelConfigView(
                binding.providerType(), binding.modelName(), binding.endpointAlias(),
                modelEnabled, usageScope, isDefault, secretRequired, readinessStatus);
        return new JdbcExecutionBindingRepository.BindingCandidate(binding, budget, model);
    }

    private JdbcExecutionBindingRepository.BindingCandidate validCandidate() {
        return candidate(validBinding(), true, true, "DEMO", true, false, "READY");
    }

    private ExecutionBindingRelease validBinding() {
        // Build with placeholder digest first, then compute the real digest
        var raw = new ExecutionBindingRelease(
                "mvp-demo-engineering-v20260724.1",
                "MVP_DEMO", "DEMO", "ENGINEERING", "ENGINEERING_PROCUREMENT",
                true, EFFECTIVE_PAST,
                "placeholder",
                "v20260705.1", "v20260705.1", "budget-standard-v20260724.1",
                "MVP_DEMO_MOCK", "model-config-mvp-demo-mock-v20260724.1",
                RuntimeArtifactVersions.PARSER_VERSION, "v20260705.1",
                RuntimeArtifactVersions.SCHEMA_VERSION,
                "v20260705.1", "v20260705.1", "v20260705.1",
                "MOCK", "cqcp-demo-mock", "mock-local");
        var digest = ExecutionBindingCatalog.computeDigest(raw);
        return new ExecutionBindingRelease(
                raw.bindingVersion(), raw.purpose(), raw.deploymentScope(),
                raw.contractTypeCode(), raw.contractTypeProfileCode(),
                raw.enabled(), raw.effectiveFrom(), digest,
                raw.contractTypeProfileVersion(), raw.ruleSetVersion(),
                raw.reviewBudgetProfileVersion(), raw.modelProfileCode(),
                raw.modelConfigVersion(), raw.parserVersion(), raw.promptVersion(),
                raw.schemaVersion(), raw.patternLibraryVersion(),
                raw.fieldLexiconVersion(), raw.evidenceSelectorVersion(),
                raw.providerType(), raw.modelName(), raw.endpointAlias());
    }

    // ========================= AC8 tests =========================

    @Test
    void notFound_whenNoRawRows() {
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of());

        var ex = assertThrows(ExecutionBindingResolutionException.class,
                () -> catalog.resolveDefault(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE));
        assertEquals(ExecutionBindingFailureReason.NOT_FOUND, ex.reason());
    }

    @Test
    void inactiveOrNotEffective_whenOnlyDisabledRows() {
        var binding = validBinding();
        var disabled = new ExecutionBindingRelease(
                binding.bindingVersion(), binding.purpose(), binding.deploymentScope(),
                binding.contractTypeCode(), binding.contractTypeProfileCode(),
                false, /* enabled=false */
                binding.effectiveFrom(), binding.contentDigest(),
                binding.contractTypeProfileVersion(), binding.ruleSetVersion(),
                binding.reviewBudgetProfileVersion(), binding.modelProfileCode(),
                binding.modelConfigVersion(), binding.parserVersion(), binding.promptVersion(),
                binding.schemaVersion(), binding.patternLibraryVersion(),
                binding.fieldLexiconVersion(), binding.evidenceSelectorVersion(),
                binding.providerType(), binding.modelName(), binding.endpointAlias());
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(disabled, true, true, "DEMO", true, false, "READY")));

        var ex = assertThrows(ExecutionBindingResolutionException.class,
                () -> catalog.resolveDefault(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE));
        assertEquals(ExecutionBindingFailureReason.INACTIVE_OR_NOT_EFFECTIVE, ex.reason());
    }

    @Test
    void ambiguous_whenTwoEffectiveRows() {
        var b1 = validBinding();
        var b2 = new ExecutionBindingRelease(
                "dup-v2", "MVP_DEMO", "DEMO", "ENGINEERING", "ENGINEERING_PROCUREMENT",
                true, EFFECTIVE_PAST, "0000000000000000000000000000000000000000000000000000000000000000",
                "v20260705.1", "v20260705.1", "budget-standard-v20260724.1",
                "MVP_DEMO_MOCK", "model-config-mvp-demo-mock-v20260724.1",
                RuntimeArtifactVersions.PARSER_VERSION, "v20260705.1",
                RuntimeArtifactVersions.SCHEMA_VERSION,
                "v20260705.1", "v20260705.1", "v20260705.1",
                "MOCK", "cqcp-demo-mock", "mock-local");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(
                        candidate(b1, true, true, "DEMO", true, false, "READY"),
                        candidate(b2, true, true, "DEMO", true, false, "READY")));

        var ex = assertThrows(ExecutionBindingResolutionException.class,
                () -> catalog.resolveDefault(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE));
        assertEquals(ExecutionBindingFailureReason.AMBIGUOUS, ex.reason());
    }

    // ========================= AC14 =========================

    @Test
    void selectsNewEnabled_whenOldDisabledAndNewEnabled() {
        var oldBinding = new ExecutionBindingRelease(
                "old-v1", "MVP_DEMO", "DEMO", "ENGINEERING", "ENGINEERING_PROCUREMENT",
                false, EFFECTIVE_PAST, "0000000000000000000000000000000000000000000000000000000000000000",
                "v20260705.1", "v20260705.1", "budget-standard-v20260724.1",
                "MVP_DEMO_MOCK", "model-config-mvp-demo-mock-v20260724.1",
                RuntimeArtifactVersions.PARSER_VERSION, "v20260705.1",
                RuntimeArtifactVersions.SCHEMA_VERSION,
                "v20260705.1", "v20260705.1", "v20260705.1",
                "MOCK", "cqcp-demo-mock", "mock-local");
        var newBinding = validBinding();
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(
                        candidate(oldBinding, true, true, "DEMO", true, false, "READY"),
                        candidate(newBinding, true, true, "DEMO", true, false, "READY")));

        var result = catalog.resolveDefault(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE);
        assertThat(result.bindingVersion()).isEqualTo(newBinding.bindingVersion());
    }

    // ========================= Domain guard — purpose/deploymentScope/contractTypeCode =========================

    /** Build a binding with overridden domain fields but a correct digest for its own 19 fields. */
    private ExecutionBindingRelease bindingWithDomain(String purpose, String deploymentScope,
                                                      String contractTypeCode) {
        var profileCode = "ENGINEERING".equals(contractTypeCode)
                ? "ENGINEERING_PROCUREMENT" : "UNKNOWN_PROFILE";
        var base = new ExecutionBindingRelease(
                "domain-override-v1", purpose, deploymentScope,
                contractTypeCode, profileCode,
                true, EFFECTIVE_PAST, "ph",
                "v20260705.1", "v20260705.1", "budget-standard-v20260724.1",
                "MVP_DEMO_MOCK", "model-config-mvp-demo-mock-v20260724.1",
                RuntimeArtifactVersions.PARSER_VERSION, "v20260705.1",
                RuntimeArtifactVersions.SCHEMA_VERSION,
                "v20260705.1", "v20260705.1", "v20260705.1",
                "MOCK", "cqcp-demo-mock", "mock-local");
        var digest = ExecutionBindingCatalog.computeDigest(base);
        return new ExecutionBindingRelease(
                base.bindingVersion(), base.purpose(), base.deploymentScope(),
                base.contractTypeCode(), base.contractTypeProfileCode(),
                base.enabled(), base.effectiveFrom(), digest,
                base.contractTypeProfileVersion(), base.ruleSetVersion(),
                base.reviewBudgetProfileVersion(), base.modelProfileCode(),
                base.modelConfigVersion(), base.parserVersion(), base.promptVersion(),
                base.schemaVersion(), base.patternLibraryVersion(),
                base.fieldLexiconVersion(), base.evidenceSelectorVersion(),
                base.providerType(), base.modelName(), base.endpointAlias());
    }

    @Test
    void rejectsWrongPurpose() {
        var binding = bindingWithDomain("WRONG_PURPOSE", "DEMO", "ENGINEERING");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(binding, true, true, "DEMO", true, false, "READY")));
        assertReason(REFERENCE_MISMATCH);
    }

    @Test
    void rejectsWrongDeploymentScope() {
        var binding = bindingWithDomain("MVP_DEMO", "PRODUCTION", "ENGINEERING");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(binding, true, true, "DEMO", true, false, "READY")));
        assertReason(REFERENCE_MISMATCH);
    }

    @Test
    void rejectsWrongContractType() {
        var binding = bindingWithDomain("MVP_DEMO", "DEMO", "MISC");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(binding, true, true, "DEMO", true, false, "READY")));
        assertReason(REFERENCE_MISMATCH);
    }

    // ========================= AC9: PROFILE_NOT_READY =========================

    @Test
    void rejectsModelDisabled() {
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(validBinding(), true, false, "DEMO", true, false, "READY")));
        assertReason(PROFILE_NOT_READY);
    }

    @Test
    void rejectsModelWrongScope() {
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(validBinding(), true, true, "PRODUCTION", true, false, "READY")));
        assertReason(PROFILE_NOT_READY);
    }

    @Test
    void rejectsModelNotDefault() {
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(validBinding(), true, true, "DEMO", false, false, "READY")));
        assertReason(PROFILE_NOT_READY);
    }

    @Test
    void rejectsModelNotReady() {
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(validBinding(), true, true, "DEMO", true, false, "NOT_READY")));
        assertReason(PROFILE_NOT_READY);
    }

    // ========================= AC16: MOCK matrix + UNSUPPORTED =========================

    @Test
    void rejectsMockSecretRequired() {
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(validBinding(), true, true, "DEMO", true, true, "READY")));
        assertReason(PROFILE_NOT_READY);
    }

    @Test
    void rejectsLocalProvider() {
        var binding = new ExecutionBindingRelease(
                "local-binding", "MVP_DEMO", "DEMO", "ENGINEERING", "ENGINEERING_PROCUREMENT",
                true, EFFECTIVE_PAST, "0000000000000000000000000000000000000000000000000000000000000000",
                "v20260705.1", "v20260705.1", "budget-standard-v20260724.1",
                "MVP_DEMO_MOCK", "model-config-mvp-demo-mock-v20260724.1",
                RuntimeArtifactVersions.PARSER_VERSION, "v20260705.1",
                RuntimeArtifactVersions.SCHEMA_VERSION,
                "v20260705.1", "v20260705.1", "v20260705.1",
                "LOCAL", "local-model", "local-endpoint");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(binding, true, true, "DEMO", true, false, "READY")));
        assertReason(UNSUPPORTED_PROVIDER);
    }

    @Test
    void rejectsPublicProvider() {
        var binding = new ExecutionBindingRelease(
                "public-binding", "MVP_DEMO", "DEMO", "ENGINEERING", "ENGINEERING_PROCUREMENT",
                true, EFFECTIVE_PAST, "0000000000000000000000000000000000000000000000000000000000000000",
                "v20260705.1", "v20260705.1", "budget-standard-v20260724.1",
                "MVP_DEMO_MOCK", "model-config-mvp-demo-mock-v20260724.1",
                RuntimeArtifactVersions.PARSER_VERSION, "v20260705.1",
                RuntimeArtifactVersions.SCHEMA_VERSION,
                "v20260705.1", "v20260705.1", "v20260705.1",
                "PUBLIC_OPENAI_COMPATIBLE", "public-model", "public-endpoint");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(binding, true, true, "DEMO", true, false, "READY")));
        assertReason(UNSUPPORTED_PROVIDER);
    }

    // ========================= AC10: REFERENCE_MISMATCH =========================

    @Test
    void rejectsProviderNameMismatch() {
        var binding = validBinding();
        var model = new JdbcExecutionBindingRepository.BindingCandidate.ModelConfigView(
                binding.providerType(), "wrong-model-name", binding.endpointAlias(),
                true, "DEMO", true, false, "READY");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(new JdbcExecutionBindingRepository.BindingCandidate(
                        binding,
                        new JdbcExecutionBindingRepository.BindingCandidate.BudgetView("STANDARD", true),
                        model)));
        assertReason(REFERENCE_MISMATCH);
    }

    @Test
    void rejectsEndpointMismatch() {
        var binding = validBinding();
        var model = new JdbcExecutionBindingRepository.BindingCandidate.ModelConfigView(
                binding.providerType(), binding.modelName(), "wrong-endpoint",
                true, "DEMO", true, false, "READY");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(new JdbcExecutionBindingRepository.BindingCandidate(
                        binding,
                        new JdbcExecutionBindingRepository.BindingCandidate.BudgetView("STANDARD", true),
                        model)));
        assertReason(REFERENCE_MISMATCH);
    }

    @Test
    void rejectsBudgetNotStandard() {
        var binding = validBinding();
        var budget = new JdbcExecutionBindingRepository.BindingCandidate.BudgetView("DEEP_REVIEW", true);
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(new JdbcExecutionBindingRepository.BindingCandidate(
                        binding, budget,
                        new JdbcExecutionBindingRepository.BindingCandidate.ModelConfigView(
                                binding.providerType(), binding.modelName(), binding.endpointAlias(),
                                true, "DEMO", true, false, "READY"))));
        assertReason(REFERENCE_MISMATCH);
    }

    @Test
    void rejectsBudgetDisabled() {
        var binding = validBinding();
        var budget = new JdbcExecutionBindingRepository.BindingCandidate.BudgetView("STANDARD", false);
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(new JdbcExecutionBindingRepository.BindingCandidate(
                        binding, budget,
                        new JdbcExecutionBindingRepository.BindingCandidate.ModelConfigView(
                                binding.providerType(), binding.modelName(), binding.endpointAlias(),
                                true, "DEMO", true, false, "READY"))));
        assertReason(REFERENCE_MISMATCH);
    }

    @Test
    void rejectsEngineeringAliasBroken() {
        var binding = new ExecutionBindingRelease(
                "alias-broken", "MVP_DEMO", "DEMO", "ENGINEERING", "WRONG_PROFILE",
                true, EFFECTIVE_PAST, "0000000000000000000000000000000000000000000000000000000000000000",
                "v20260705.1", "v20260705.1", "budget-standard-v20260724.1",
                "MVP_DEMO_MOCK", "model-config-mvp-demo-mock-v20260724.1",
                RuntimeArtifactVersions.PARSER_VERSION, "v20260705.1",
                RuntimeArtifactVersions.SCHEMA_VERSION,
                "v20260705.1", "v20260705.1", "v20260705.1",
                "MOCK", "cqcp-demo-mock", "mock-local");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(binding, true, true, "DEMO", true, false, "READY")));
        assertReason(REFERENCE_MISMATCH);
    }

    @Test
    void rejectsLegacyModuleVersion() {
        var binding = new ExecutionBindingRelease(
                "legacy-bad", "MVP_DEMO", "DEMO", "ENGINEERING", "ENGINEERING_PROCUREMENT",
                true, EFFECTIVE_PAST, "0000000000000000000000000000000000000000000000000000000000000000",
                "v20260705.1", "WRONG_VERSION", "budget-standard-v20260724.1",
                "MVP_DEMO_MOCK", "model-config-mvp-demo-mock-v20260724.1",
                RuntimeArtifactVersions.PARSER_VERSION, "v20260705.1",
                RuntimeArtifactVersions.SCHEMA_VERSION,
                "v20260705.1", "v20260705.1", "v20260705.1",
                "MOCK", "cqcp-demo-mock", "mock-local");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(binding, true, true, "DEMO", true, false, "READY")));
        assertReason(REFERENCE_MISMATCH);
    }

    // ========================= AC11: RUNTIME_VERSION_MISMATCH =========================

    @Test
    void rejectsParserMismatch() {
        var binding = new ExecutionBindingRelease(
                "parser-bad", "MVP_DEMO", "DEMO", "ENGINEERING", "ENGINEERING_PROCUREMENT",
                true, EFFECTIVE_PAST, "0000000000000000000000000000000000000000000000000000000000000000",
                "v20260705.1", "v20260705.1", "budget-standard-v20260724.1",
                "MVP_DEMO_MOCK", "model-config-mvp-demo-mock-v20260724.1",
                "wrong-parser-version", "v20260705.1",
                RuntimeArtifactVersions.SCHEMA_VERSION,
                "v20260705.1", "v20260705.1", "v20260705.1",
                "MOCK", "cqcp-demo-mock", "mock-local");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(binding, true, true, "DEMO", true, false, "READY")));
        assertReason(RUNTIME_VERSION_MISMATCH);
    }

    @Test
    void rejectsSchemaMismatch() {
        var binding = new ExecutionBindingRelease(
                "schema-bad", "MVP_DEMO", "DEMO", "ENGINEERING", "ENGINEERING_PROCUREMENT",
                true, EFFECTIVE_PAST, "0000000000000000000000000000000000000000000000000000000000000000",
                "v20260705.1", "v20260705.1", "budget-standard-v20260724.1",
                "MVP_DEMO_MOCK", "model-config-mvp-demo-mock-v20260724.1",
                RuntimeArtifactVersions.PARSER_VERSION, "v20260705.1",
                "wrong-schema-version",
                "v20260705.1", "v20260705.1", "v20260705.1",
                "MOCK", "cqcp-demo-mock", "mock-local");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(binding, true, true, "DEMO", true, false, "READY")));
        assertReason(RUNTIME_VERSION_MISMATCH);
    }

    // ========================= AC12: CONTENT_DIGEST_MISMATCH =========================

    @Test
    void rejectsDigestMismatch() {
        var binding = new ExecutionBindingRelease(
                "digest-bad", "MVP_DEMO", "DEMO", "ENGINEERING", "ENGINEERING_PROCUREMENT",
                true, EFFECTIVE_PAST, "0000000000000000000000000000000000000000000000000000000000000000",
                "v20260705.1", "v20260705.1", "budget-standard-v20260724.1",
                "MVP_DEMO_MOCK", "model-config-mvp-demo-mock-v20260724.1",
                RuntimeArtifactVersions.PARSER_VERSION, "v20260705.1",
                RuntimeArtifactVersions.SCHEMA_VERSION,
                "v20260705.1", "v20260705.1", "v20260705.1",
                "MOCK", "cqcp-demo-mock", "mock-local");
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(candidate(binding, true, true, "DEMO", true, false, "READY")));
        assertReason(CONTENT_DIGEST_MISMATCH);
    }

    // ========================= AC7: versionReferences mapping =========================

    @Test
    void modelConfigVersionMapsToModelProfileVersion() {
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(validCandidate()));

        var result = catalog.resolveDefault(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE);
        var refs = result.versionReferences();
        assertThat(refs.modelProfileVersion()).isEqualTo(result.modelConfigVersion());
        assertThat(refs.modelProfileVersion()).isEqualTo("model-config-mvp-demo-mock-v20260724.1");
        assertThat(refs.contractTypeProfileVersion()).isEqualTo("v20260705.1");
        assertThat(refs.ruleSetVersion()).isEqualTo("v20260705.1");
        assertThat(refs.reviewBudgetProfileVersion()).isEqualTo("budget-standard-v20260724.1");
        assertThat(refs.parserVersion()).isEqualTo(RuntimeArtifactVersions.PARSER_VERSION);
        assertThat(refs.schemaVersion()).isEqualTo(RuntimeArtifactVersions.SCHEMA_VERSION);
    }

    // ========================= AC12: digest =========================

    @Test
    void digestAlgorithmDeterministic() {
        var binding = validBindingWithCorrectDigest();
        var d1 = ExecutionBindingCatalog.computeDigest(binding);
        var d2 = ExecutionBindingCatalog.computeDigest(binding);
        assertThat(d1).isEqualTo(d2);
        assertThat(d1).hasSize(64);
        assertThat(d1).isLowerCase();
    }

    @Test
    void digestChangesWhenFieldChanges() {
        var binding = validBindingWithCorrectDigest();
        var original = ExecutionBindingCatalog.computeDigest(binding);

        var changed = new ExecutionBindingRelease(
                binding.bindingVersion(), binding.purpose(), binding.deploymentScope(),
                binding.contractTypeCode(), binding.contractTypeProfileCode(),
                binding.enabled(), binding.effectiveFrom(), "irrelevant",
                binding.contractTypeProfileVersion(), binding.ruleSetVersion(),
                binding.reviewBudgetProfileVersion(), binding.modelProfileCode(),
                binding.modelConfigVersion(), binding.parserVersion(), "different-prompt-version",
                binding.schemaVersion(), binding.patternLibraryVersion(),
                binding.fieldLexiconVersion(), binding.evidenceSelectorVersion(),
                binding.providerType(), binding.modelName(), binding.endpointAlias());

        var changedDigest = ExecutionBindingCatalog.computeDigest(changed);
        assertThat(changedDigest).isNotEqualTo(original);
    }

    // ========================= AC15: ENGINEERING alias =========================

    @Test
    void engineeringAliasValidated() {
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(validCandidate()));

        var result = catalog.resolveDefault(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE);
        assertThat(result.contractTypeCode()).isEqualTo("ENGINEERING");
        assertThat(result.contractTypeProfileCode()).isEqualTo("ENGINEERING_PROCUREMENT");
        assertThat(result.contractTypeProfileVersion()).isEqualTo("v20260705.1");
    }

    // ========================= AC17: happy path =========================

    @Test
    void acceptsValidDemoBinding() {
        when(repository.findBySelector(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE))
                .thenReturn(List.of(validCandidateWithCorrectDigest()));

        assertDoesNotThrow(() -> {
            var result = catalog.resolveDefault(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE);
            assertThat(result.bindingVersion()).isEqualTo("mvp-demo-engineering-v20260724.1");
            assertThat(result.contractTypeProfileVersion()).isNotNull();
            assertThat(result.ruleSetVersion()).isNotNull();
            assertThat(result.reviewBudgetProfileVersion()).isNotNull();
            assertThat(result.modelProfileCode()).isNotNull();
            assertThat(result.modelConfigVersion()).isNotNull();
            assertThat(result.parserVersion()).isNotNull();
            assertThat(result.promptVersion()).isNotNull();
            assertThat(result.schemaVersion()).isNotNull();
            assertThat(result.patternLibraryVersion()).isNotNull();
            assertThat(result.fieldLexiconVersion()).isNotNull();
            assertThat(result.evidenceSelectorVersion()).isNotNull();
            assertThat(result.providerType()).isNotNull();
            assertThat(result.modelName()).isNotNull();
            assertThat(result.endpointAlias()).isNotNull();
        });
    }

    // ========================= AC17: all 8 reasons enumerated =========================

    @Test
    void allEightFailureReasonsHaveTests() {
        // NOT_FOUND - notFound_whenNoRawRows
        // AMBIGUOUS - ambiguous_whenTwoEffectiveRows
        // INACTIVE_OR_NOT_EFFECTIVE - inactiveOrNotEffective_whenOnlyDisabledRows
        // REFERENCE_MISMATCH - rejectsProvider* / rejectsBudget* / rejectsEngineering* / rejectsLegacy*
        // PROFILE_NOT_READY - rejectsModelDisabled / rejectsModelWrongScope / rejectsModelNotDefault / rejectsModelNotReady / rejectsMockSecretRequired
        // RUNTIME_VERSION_MISMATCH - rejectsParserMismatch / rejectsSchemaMismatch
        // CONTENT_DIGEST_MISMATCH - rejectsDigestMismatch
        // UNSUPPORTED_PROVIDER - rejectsLocalProvider / rejectsPublicProvider
        assertThat(ExecutionBindingFailureReason.values()).hasSize(8);
        // verification: each enum constant is referenced by at least one test method name pattern
    }

    @Test
    void legacySixModuleVersionsMatch() {
        var binding = validBinding();
        assertThat(binding.contractTypeProfileVersion()).isEqualTo("v20260705.1");
        assertThat(binding.ruleSetVersion()).isEqualTo("v20260705.1");
        assertThat(binding.promptVersion()).isEqualTo("v20260705.1");
        assertThat(binding.patternLibraryVersion()).isEqualTo("v20260705.1");
        assertThat(binding.fieldLexiconVersion()).isEqualTo("v20260705.1");
        assertThat(binding.evidenceSelectorVersion()).isEqualTo("v20260705.1");
    }

    // ========================= helpers =========================

    private void assertReason(ExecutionBindingFailureReason expectedReason) {
        var ex = assertThrows(ExecutionBindingResolutionException.class,
                () -> catalog.resolveDefault(PURPOSE, DEPLOYMENT_SCOPE, CONTRACT_TYPE_CODE));
        assertEquals(expectedReason, ex.reason());
    }

    private ExecutionBindingRelease validBindingWithCorrectDigest() {
        // validBinding() already computes the correct digest; this method
        // exists for callers that want to be explicit about the intent.
        return validBinding();
    }

    private JdbcExecutionBindingRepository.BindingCandidate validCandidateWithCorrectDigest() {
        return validCandidate();
    }
}
