package com.cqcp.apiserver.modelgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ModelGatewayService {

    private final ObjectMapper objectMapper;
    private final List<ModelProvider> providers;

    public ModelGatewayService(ObjectMapper objectMapper, List<ModelProvider> providers) {
        this.objectMapper = objectMapper;
        this.providers = providers;
    }

    public ModelGatewayResult invoke(ModelProfile profile, ModelCallIntent intent, String prompt) {
        if (!profile.enabled()) {
            return ModelGatewayResult.failure(
                    intent,
                    profile,
                    ModelDiagnosticCode.SYS_MODEL_UNAVAILABLE,
                    NotConcludedReason.MODEL_UNAVAILABLE);
        }

        var provider = providers.stream()
                .filter(candidate -> candidate.providerType() == profile.providerType())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No provider for " + profile.providerType()));

        try {
            var response = provider.invoke(profile, intent, prompt);
            var artifact = parseArtifact(response.rawJson());
            return ModelGatewayResult.success(intent, profile, artifact);
        } catch (ModelProviderException exception) {
            return ModelGatewayResult.failure(
                    intent,
                    profile,
                    exception.diagnosticCode(),
                    NotConcludedReason.MODEL_UNAVAILABLE);
        } catch (Exception exception) {
            return ModelGatewayResult.failure(
                    intent,
                    profile,
                    ModelDiagnosticCode.SYS_MODEL_OUTPUT_INCOMPLETE,
                    NotConcludedReason.MODEL_UNAVAILABLE);
        }
    }

    private ModelGatewayArtifact parseArtifact(String rawJson) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);
        var artifactId = requiredText(root, "artifactId");
        var coveredRoles = requiredStringArray(root, "coveredRoles");
        var uncoveredRoles = requiredStringArray(root, "uncoveredRoles");
        var validationStatus = requiredText(root, "outputSchemaValidationStatus");

        return new ModelGatewayArtifact(artifactId, coveredRoles, uncoveredRoles, validationStatus);
    }

    private String requiredText(JsonNode root, String fieldName) {
        var node = root.get(fieldName);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException("Missing text field: " + fieldName);
        }
        return node.asText();
    }

    private List<String> requiredStringArray(JsonNode root, String fieldName) {
        var node = root.get(fieldName);
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("Missing array field: " + fieldName);
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }
}
