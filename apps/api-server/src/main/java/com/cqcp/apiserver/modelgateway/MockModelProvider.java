package com.cqcp.apiserver.modelgateway;

import org.springframework.stereotype.Component;

@Component
public class MockModelProvider implements ModelProvider {

    @Override
    public ModelProviderType providerType() {
        return ModelProviderType.MOCK;
    }

    @Override
    public ModelProviderResponse invoke(ModelProfile profile, ModelCallIntent intent, String prompt) {
        return switch (prompt) {
            case "MOCK_TIMEOUT" -> throw new ModelProviderException(
                    ModelDiagnosticCode.SYS_MODEL_TIMEOUT, "Mock timeout");
            case "MOCK_UNAVAILABLE" -> throw new ModelProviderException(
                    ModelDiagnosticCode.SYS_MODEL_UNAVAILABLE, "Mock unavailable");
            case "MOCK_CONFLICT" -> throw new ModelProviderException(
                    ModelDiagnosticCode.SYS_MODEL_CONFLICT, "Mock conflict");
            case "MOCK_SCHEMA_INVALID" -> new ModelProviderResponse("""
                    {"artifactId":"","coveredRoles":"partyA","outputSchemaValidationStatus":"INVALID"}
                    """);
            default -> new ModelProviderResponse("""
                    {
                      "artifactId":"artifact-mock-001",
                      "coveredRoles":["partyAName"],
                      "uncoveredRoles":["partyBName"],
                      "outputSchemaValidationStatus":"VALID"
                    }
                    """);
        };
    }
}
