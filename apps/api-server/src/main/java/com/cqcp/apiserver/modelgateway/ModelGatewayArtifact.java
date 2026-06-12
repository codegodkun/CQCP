package com.cqcp.apiserver.modelgateway;

import java.util.List;

public record ModelGatewayArtifact(
        String artifactId,
        List<String> coveredRoles,
        List<String> uncoveredRoles,
        String outputSchemaValidationStatus) {
}
