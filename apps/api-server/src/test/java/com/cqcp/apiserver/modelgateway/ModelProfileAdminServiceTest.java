package com.cqcp.apiserver.modelgateway;

import static com.cqcp.apiserver.modelgateway.ModelProfileAdminModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ModelProfileAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");
    private static final String SECRET_SENTINEL = "CQCP_SECRET_SENTINEL_123";

    private final ModelProfileAdminRepository repository =
            Mockito.mock(ModelProfileAdminRepository.class);
    private final ModelConnectivityClient client =
            Mockito.mock(ModelConnectivityClient.class);
    private final ModelSecretResolver secrets =
            new ModelSecretResolver(Path.of("build/test-secrets"), name ->
                    "CQCP_MODEL_DEEPSEEK_API_KEY".equals(name) ? SECRET_SENTINEL : null);
    private final ModelEndpointAllowlist endpoints =
            new ModelEndpointAllowlist(
                    Map.of("deepseek-official", URI.create("https://api.deepseek.com")));
    private final ModelProfileAdminService service = new ModelProfileAdminService(
            repository,
            secrets,
            endpoints,
            client,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setUp() {
        when(repository.findLatestConnectivity(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void createsDisabledUnboundEvaluationProfileWithoutExposingSecret() throws Exception {
        when(repository.lockLatestProfile("DEEPSEEK_EVAL")).thenReturn(Optional.empty());

        var view = service.createProfile(request("DEEPSEEK_EVAL", "deepseek-v4-pro"));

        verify(repository).lockProfileScope("DEEPSEEK_EVAL");
        var inserted = ArgumentCaptor.forClass(ModelProfileConfigRow.class);
        verify(repository).insertProfile(inserted.capture());
        assertThat(inserted.getValue().enabled()).isFalse();
        assertThat(inserted.getValue().defaultForNewTask()).isFalse();
        assertThat(inserted.getValue().usageScope()).isEqualTo("EVALUATION");
        assertThat(inserted.getValue().secretRef())
                .isEqualTo("env:CQCP_MODEL_DEEPSEEK_API_KEY");
        assertThat(view.secretConfigured()).isTrue();
        assertThat(mapper.writeValueAsString(view)).doesNotContain(SECRET_SENTINEL);
        assertThat(mapper.writeValueAsString(view)).doesNotContain("secretRef");
    }

    @Test
    void mockReadinessDoesNotPretendThatASecretIsConfigured() {
        var mockProfile = new ModelProfileConfigRow(
                "MVP_DEMO_MOCK",
                "model-config-mvp-demo-mock-v1",
                "MVP Demo Mock",
                "MOCK",
                "mock-local",
                "cqcp-demo-mock",
                true,
                "DEMO",
                true,
                false,
                null,
                "READY",
                30,
                0,
                NOW,
                NOW);
        when(repository.findLatestProfiles()).thenReturn(java.util.List.of(mockProfile));

        var view = service.listProfiles().items().getFirst();

        assertThat(view.readiness()).isEqualTo(ProfileReadiness.READY);
        assertThat(view.secretConfigured()).isFalse();
    }

    @Test
    void newVersionDeactivatesLifecycleAndKeepsContentVersionDistinct() {
        var existing = row(
                "DEEPSEEK_EVAL",
                "model-config-old",
                "deepseek-v4-pro",
                "env:CQCP_MODEL_DEEPSEEK_API_KEY");
        when(repository.lockLatestProfile("DEEPSEEK_EVAL")).thenReturn(Optional.of(existing));

        var update = request("DEEPSEEK_EVAL", "deepseek-v4-flash");
        update.remove("profileCode");
        service.createNewVersion("DEEPSEEK_EVAL", update);

        verify(repository).lockProfileScope("DEEPSEEK_EVAL");
        verify(repository).deactivateProfile("DEEPSEEK_EVAL");
        var inserted = ArgumentCaptor.forClass(ModelProfileConfigRow.class);
        verify(repository).insertProfile(inserted.capture());
        assertThat(inserted.getValue().configVersion()).isNotEqualTo(existing.configVersion());
        assertThat(inserted.getValue().modelName()).isEqualTo("deepseek-v4-flash");
    }

    @Test
    void rejectsLegacyModelRawKeyUnknownEndpointAndProductionScope() {
        assertThatThrownBy(() -> service.createProfile(
                request("DEEPSEEK_EVAL", "deepseek-chat")))
                .isInstanceOf(ModelProfileAdminException.class);
        assertThatThrownBy(() -> service.createProfile(mapper.createObjectNode()
                .put("profileCode", "DEEPSEEK_EVAL")
                .put("displayName", "DeepSeek")
                .put("providerType", "PUBLIC_OPENAI_COMPATIBLE")
                .put("usageScope", "PRODUCTION_REVIEW")
                .put("endpointAlias", "deepseek-official")
                .put("modelName", "deepseek-v4-pro")
                .put("secretRef", "raw-key")
                .put("timeoutSeconds", 30)
                .put("retryCount", 0)))
                .isInstanceOf(ModelProfileAdminException.class);
        assertThatThrownBy(() -> service.createProfile(mapper.createObjectNode()
                .put("profileCode", "DEEPSEEK_EVAL")
                .put("displayName", "DeepSeek")
                .put("endpointAlias", "https://attacker.invalid")
                .put("modelName", "deepseek-v4-pro")
                .put("secretRef", "env:CQCP_MODEL_DEEPSEEK_API_KEY")
                .put("timeoutSeconds", 30)
                .put("retryCount", 0)))
                .isInstanceOf(ModelProfileAdminException.class);
        assertThatThrownBy(() -> service.createProfile(request(
                "DEEPSEEK_EVAL",
                "deepseek-v4-pro").put("apiKey", SECRET_SENTINEL)))
                .isInstanceOfSatisfying(ModelProfileAdminException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("UNKNOWN_FIELD");
                    assertThat(exception.getMessage()).doesNotContain(SECRET_SENTINEL);
                });
    }

    @Test
    void rejectsConfusedDeputySecretReferencesBeforePersistenceOrNetwork() {
        var databasePasswordReference = request(
                "DEEPSEEK_EVAL",
                "deepseek-v4-pro").put("secretRef", "env:CQCP_DB_PASSWORD");
        var unrelatedModelReference = request(
                "DEEPSEEK_EVAL",
                "deepseek-v4-pro").put("secretRef", "env:CQCP_MODEL_OTHER_SECRET");
        var unrelatedFileReference = request(
                "DEEPSEEK_EVAL",
                "deepseek-v4-pro").put(
                        "secretRef",
                        "file:" + Path.of("build/test-secrets/unrelated").toAbsolutePath());

        for (var malicious : java.util.List.of(
                databasePasswordReference,
                unrelatedModelReference,
                unrelatedFileReference)) {
            assertThatThrownBy(() -> service.createProfile(malicious))
                    .isInstanceOfSatisfying(ModelProfileAdminException.class, exception ->
                            assertThat(exception.code())
                                    .isEqualTo("SECRET_REFERENCE_NOT_ALLOWED"));
        }

        verify(repository, never()).lockProfileScope(any());
        verify(repository, never()).insertProfile(any());
        verify(client, never()).test(any(), any(), any(), any());
    }

    @Test
    void rejectsFractionalIntegerFieldsInsteadOfTruncating() {
        assertThatThrownBy(() -> service.createProfile(
                request("DEEPSEEK_EVAL", "deepseek-v4-pro")
                        .put("timeoutSeconds", 1.5)))
                .isInstanceOfSatisfying(ModelProfileAdminException.class, exception ->
                        assertThat(exception.code()).isEqualTo("INVALID_FIELD"));
        assertThatThrownBy(() -> service.createProfile(
                request("DEEPSEEK_EVAL", "deepseek-v4-pro")
                        .put("retryCount", 0.9)))
                .isInstanceOfSatisfying(ModelProfileAdminException.class, exception ->
                        assertThat(exception.code()).isEqualTo("INVALID_FIELD"));
    }

    @Test
    void missingSecretFailsClosedWithoutNetworkAndPersistsStableStatus() {
        var missingSecretRow = row(
                "DEEPSEEK_MISSING",
                "model-config-missing",
                "deepseek-v4-pro",
                "env:MISSING_SECRET");
        when(repository.lockLatestProfile("DEEPSEEK_MISSING"))
                .thenReturn(Optional.of(missingSecretRow));

        var result = service.testConnectivity("DEEPSEEK_MISSING");

        assertThat(result.status()).isEqualTo(ConnectivityStatus.SECRET_MISSING);
        verify(client, never()).test(any(), any(), any(), any());
        verify(repository).updateReadiness(
                "DEEPSEEK_MISSING",
                "model-config-missing",
                false);
    }

    @Test
    void connectivityPersistsSanitizedOutcomeAndReadiness() {
        var profile = row(
                "DEEPSEEK_EVAL",
                "model-config-current",
                "deepseek-v4-pro",
                "env:CQCP_MODEL_DEEPSEEK_API_KEY");
        when(repository.lockLatestProfile("DEEPSEEK_EVAL"))
                .thenReturn(Optional.of(profile));
        when(client.test(any(), eq("deepseek-v4-pro"), eq(SECRET_SENTINEL), any()))
                .thenReturn(new ConnectivityOutcome(
                        ConnectivityStatus.SUCCEEDED,
                        "2XX",
                        true,
                        15));

        var result = service.testConnectivity("DEEPSEEK_EVAL");

        assertThat(result.status()).isEqualTo(ConnectivityStatus.SUCCEEDED);
        var persisted = ArgumentCaptor.forClass(ConnectivityRow.class);
        verify(repository).insertConnectivity(persisted.capture());
        assertThat(persisted.getValue().httpStatusClass()).isEqualTo("2XX");
        verify(repository).updateReadiness(
                "DEEPSEEK_EVAL",
                "model-config-current",
                true);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode request(
            String profileCode,
            String modelName) {
        return mapper.createObjectNode()
                .put("profileCode", profileCode)
                .put("displayName", "DeepSeek Evaluation")
                .put("providerType", "PUBLIC_OPENAI_COMPATIBLE")
                .put("usageScope", "EVALUATION")
                .put("endpointAlias", "deepseek-official")
                .put("modelName", modelName)
                .put("secretRef", "env:CQCP_MODEL_DEEPSEEK_API_KEY")
                .put("timeoutSeconds", 30)
                .put("retryCount", 0);
    }

    private ModelProfileConfigRow row(
            String profileCode,
            String configVersion,
            String modelName,
            String secretRef) {
        return new ModelProfileConfigRow(
                profileCode,
                configVersion,
                "DeepSeek Evaluation",
                "PUBLIC_OPENAI_COMPATIBLE",
                "deepseek-official",
                modelName,
                false,
                "EVALUATION",
                false,
                true,
                secretRef,
                "NOT_READY",
                30,
                0,
                NOW,
                NOW);
    }
}
