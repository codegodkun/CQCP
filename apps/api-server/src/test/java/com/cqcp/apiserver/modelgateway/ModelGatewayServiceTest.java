package com.cqcp.apiserver.modelgateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelGatewayServiceTest {

    private ModelGatewayService service;

    @BeforeEach
    void setUp() {
        service = new ModelGatewayService(new ObjectMapper(), List.of(new MockModelProvider()));
    }

    @Test
    void returnsArtifactForMockSuccessPath() {
        var profile = new ModelProfile("mock-standard", ModelProviderType.MOCK, "mock-model", true);
        var intent = sampleIntent();

        var result = service.invoke(profile, intent, "MOCK_SUCCESS");

        assertThat(result.success()).isTrue();
        assertThat(result.intent()).isEqualTo(intent);
        assertThat(result.profile()).isEqualTo(profile);
        assertThat(result.artifact()).isNotNull();
        assertThat(result.artifact().artifactId()).isEqualTo("artifact-mock-001");
        assertThat(result.artifact().coveredRoles()).containsExactly("partyAName");
        assertThat(result.artifact().uncoveredRoles()).containsExactly("partyBName");
        assertThat(result.artifact().outputSchemaValidationStatus()).isEqualTo("VALID");
        assertThat(result.diagnosticCode()).isNull();
        assertThat(result.notConcludedReason()).isNull();
    }

    @Test
    void mapsSchemaInvalidToSysModelOutputIncomplete() {
        var result = service.invoke(
                new ModelProfile("mock-standard", ModelProviderType.MOCK, "mock-model", true),
                sampleIntent(),
                "MOCK_SCHEMA_INVALID");

        assertThat(result.success()).isFalse();
        assertThat(result.artifact()).isNull();
        assertThat(result.diagnosticCode()).isEqualTo(ModelDiagnosticCode.SYS_MODEL_OUTPUT_INCOMPLETE);
        assertThat(result.notConcludedReason()).isEqualTo(NotConcludedReason.MODEL_UNAVAILABLE);
    }

    @Test
    void mapsTimeoutToSysModelTimeout() {
        var result = service.invoke(
                new ModelProfile("mock-standard", ModelProviderType.MOCK, "mock-model", true),
                sampleIntent(),
                "MOCK_TIMEOUT");

        assertThat(result.success()).isFalse();
        assertThat(result.diagnosticCode()).isEqualTo(ModelDiagnosticCode.SYS_MODEL_TIMEOUT);
        assertThat(result.notConcludedReason()).isEqualTo(NotConcludedReason.MODEL_UNAVAILABLE);
    }

    @Test
    void mapsUnavailableToSysModelUnavailable() {
        var result = service.invoke(
                new ModelProfile("mock-standard", ModelProviderType.MOCK, "mock-model", true),
                sampleIntent(),
                "MOCK_UNAVAILABLE");

        assertThat(result.success()).isFalse();
        assertThat(result.diagnosticCode()).isEqualTo(ModelDiagnosticCode.SYS_MODEL_UNAVAILABLE);
        assertThat(result.notConcludedReason()).isEqualTo(NotConcludedReason.MODEL_UNAVAILABLE);
    }

    @Test
    void mapsConflictToSysModelConflict() {
        var result = service.invoke(
                new ModelProfile("mock-standard", ModelProviderType.MOCK, "mock-model", true),
                sampleIntent(),
                "MOCK_CONFLICT");

        assertThat(result.success()).isFalse();
        assertThat(result.diagnosticCode()).isEqualTo(ModelDiagnosticCode.SYS_MODEL_CONFLICT);
        assertThat(result.notConcludedReason()).isEqualTo(NotConcludedReason.MODEL_UNAVAILABLE);
    }

    @Test
    void returnsUnavailableWhenProfileDisabled() {
        var result = service.invoke(
                new ModelProfile("mock-disabled", ModelProviderType.MOCK, "mock-model", false),
                sampleIntent(),
                "MOCK_SUCCESS");

        assertThat(result.success()).isFalse();
        assertThat(result.diagnosticCode()).isEqualTo(ModelDiagnosticCode.SYS_MODEL_UNAVAILABLE);
        assertThat(result.notConcludedReason()).isEqualTo(NotConcludedReason.MODEL_UNAVAILABLE);
    }

    private ModelCallIntent sampleIntent() {
        return new ModelCallIntent(
                "task-001",
                "execution-001",
                "PARTY_FIELDS",
                "PARTY_A_NAME_CONSISTENCY",
                List.of("block-1", "block-2"),
                "hash-001",
                "prompt-v1",
                "schema-v1",
                "model-v1",
                1);
    }
}
