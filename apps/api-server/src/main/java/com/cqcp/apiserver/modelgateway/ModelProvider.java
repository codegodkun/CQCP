package com.cqcp.apiserver.modelgateway;

public interface ModelProvider {

    ModelProviderType providerType();

    ModelProviderResponse invoke(ModelProfile profile, ModelCallIntent intent, String prompt);
}
