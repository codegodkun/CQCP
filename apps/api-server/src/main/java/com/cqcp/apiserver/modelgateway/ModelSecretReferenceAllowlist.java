package com.cqcp.apiserver.modelgateway;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 将 provider endpoint alias 绑定到部署侧冻结的 Secret Reference。
 *
 * <p>Admin 请求只能选择与 endpoint 匹配的精确引用，不能把数据库密码等任意进程
 * 环境变量或无关 Secret 文件变成 Provider Bearer token。
 */
@Component
final class ModelSecretReferenceAllowlist {

    private static final Set<String> DEEPSEEK_ALLOWED_REFERENCES = Set.of(
            "env:CQCP_MODEL_DEEPSEEK_API_KEY",
            "file:/run/secrets/cqcp-model-deepseek-api-key");

    private final Map<String, String> references;

    @Autowired
    ModelSecretReferenceAllowlist(
            @Value("${cqcp.model-gateway.secret-refs.deepseek-official}")
            String deepSeekOfficialReference) {
        this(Map.of("deepseek-official", deepSeekOfficialReference));
    }

    ModelSecretReferenceAllowlist(Map<String, String> references) {
        this.references = Map.copyOf(Objects.requireNonNull(references, "references"));
        var configured = this.references.get("deepseek-official");
        if (!DEEPSEEK_ALLOWED_REFERENCES.contains(configured)) {
            throw new IllegalArgumentException(
                    "deepseek-official must use a frozen provider-specific Secret Reference");
        }
    }

    Optional<String> resolve(String endpointAlias) {
        return Optional.ofNullable(references.get(endpointAlias));
    }

    boolean permits(String endpointAlias, String secretRef) {
        return resolve(endpointAlias).filter(secretRef::equals).isPresent();
    }
}
