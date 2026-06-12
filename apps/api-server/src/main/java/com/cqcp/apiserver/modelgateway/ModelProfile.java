package com.cqcp.apiserver.modelgateway;

public record ModelProfile(
        String profileCode,
        ModelProviderType providerType,
        String modelName,
        boolean enabled) {
}
