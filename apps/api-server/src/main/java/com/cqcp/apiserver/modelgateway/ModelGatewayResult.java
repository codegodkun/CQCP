package com.cqcp.apiserver.modelgateway;

public record ModelGatewayResult(
        ModelCallIntent intent,
        ModelProfile profile,
        boolean success,
        ModelGatewayArtifact artifact,
        ModelDiagnosticCode diagnosticCode,
        NotConcludedReason notConcludedReason) {

    public static ModelGatewayResult success(
            ModelCallIntent intent,
            ModelProfile profile,
            ModelGatewayArtifact artifact) {
        return new ModelGatewayResult(intent, profile, true, artifact, null, null);
    }

    public static ModelGatewayResult failure(
            ModelCallIntent intent,
            ModelProfile profile,
            ModelDiagnosticCode diagnosticCode,
            NotConcludedReason notConcludedReason) {
        return new ModelGatewayResult(intent, profile, false, null, diagnosticCode, notConcludedReason);
    }
}
