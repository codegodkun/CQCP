package com.cqcp.apiserver.modelgateway;

public class ModelProviderException extends RuntimeException {

    private final ModelDiagnosticCode diagnosticCode;

    public ModelProviderException(ModelDiagnosticCode diagnosticCode, String message) {
        super(message);
        this.diagnosticCode = diagnosticCode;
    }

    public ModelDiagnosticCode diagnosticCode() {
        return diagnosticCode;
    }
}
