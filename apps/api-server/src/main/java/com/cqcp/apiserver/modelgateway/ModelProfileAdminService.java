package com.cqcp.apiserver.modelgateway;

import static com.cqcp.apiserver.modelgateway.ModelProfileAdminModels.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ModelProfileAdminService {

    private static final Pattern PROFILE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{2,63}$");
    private static final Set<String> ALLOWED_MODELS =
            Set.of("deepseek-v4-pro", "deepseek-v4-flash");
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "profileCode",
            "displayName",
            "providerType",
            "endpointAlias",
            "modelName",
            "usageScope",
            "secretRef",
            "timeoutSeconds",
            "retryCount");
    private static final DateTimeFormatter VERSION_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                    .withZone(ZoneOffset.UTC);

    private final ModelProfileAdminRepository repository;
    private final ModelSecretResolver secretResolver;
    private final ModelSecretReferenceAllowlist secretReferenceAllowlist;
    private final ModelEndpointAllowlist endpointAllowlist;
    private final ModelConnectivityClient connectivityClient;
    private final Clock clock;

    @Autowired
    ModelProfileAdminService(
            ModelProfileAdminRepository repository,
            ModelSecretResolver secretResolver,
            ModelSecretReferenceAllowlist secretReferenceAllowlist,
            ModelEndpointAllowlist endpointAllowlist,
            ModelConnectivityClient connectivityClient) {
        this(
                repository,
                secretResolver,
                secretReferenceAllowlist,
                endpointAllowlist,
                connectivityClient,
                Clock.systemUTC());
    }

    ModelProfileAdminService(
            ModelProfileAdminRepository repository,
            ModelSecretResolver secretResolver,
            ModelEndpointAllowlist endpointAllowlist,
            ModelConnectivityClient connectivityClient,
            Clock clock) {
        this(
                repository,
                secretResolver,
                new ModelSecretReferenceAllowlist(
                        java.util.Map.of(
                                "deepseek-official",
                                "env:CQCP_MODEL_DEEPSEEK_API_KEY")),
                endpointAllowlist,
                connectivityClient,
                clock);
    }

    ModelProfileAdminService(
            ModelProfileAdminRepository repository,
            ModelSecretResolver secretResolver,
            ModelSecretReferenceAllowlist secretReferenceAllowlist,
            ModelEndpointAllowlist endpointAllowlist,
            ModelConnectivityClient connectivityClient,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.secretResolver = Objects.requireNonNull(secretResolver, "secretResolver");
        this.secretReferenceAllowlist =
                Objects.requireNonNull(secretReferenceAllowlist, "secretReferenceAllowlist");
        this.endpointAllowlist = Objects.requireNonNull(endpointAllowlist, "endpointAllowlist");
        this.connectivityClient = Objects.requireNonNull(connectivityClient, "connectivityClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    ModelProfileListResponse listProfiles() {
        var items = repository.findLatestProfiles().stream().map(this::toView).toList();
        return new ModelProfileListResponse(items);
    }

    @Transactional
    ModelProfileView createProfile(JsonNode request) {
        var input = parseRequest(request, null);
        repository.lockProfileScope(input.profileCode());
        if (repository.lockLatestProfile(input.profileCode()).isPresent()) {
            throw conflict("PROFILE_ALREADY_EXISTS", "profileCode 已存在");
        }
        var row = newRow(input, clock.instant());
        repository.insertProfile(row);
        return toView(row);
    }

    @Transactional
    ModelProfileView createNewVersion(String profileCode, JsonNode request) {
        requireProfileCode(profileCode);
        repository.lockProfileScope(profileCode);
        var existing = repository.lockLatestProfile(profileCode)
                .orElseThrow(() -> notFound("MODEL_PROFILE_NOT_FOUND", "Model Profile 不存在"));
        if (!PUBLIC_PROVIDER.equals(existing.providerType())) {
            throw conflict("CODE_OWNED_PROFILE", "该 profile 由部署版本管理，不允许通过 Admin API 修改");
        }
        var input = parseRequest(request, profileCode);
        repository.deactivateProfile(profileCode);
        var row = newRow(input, clock.instant());
        repository.insertProfile(row);
        return toView(row);
    }

    @Transactional
    ConnectivityView testConnectivity(String profileCode) {
        requireProfileCode(profileCode);
        var profile = repository.lockLatestProfile(profileCode)
                .orElseThrow(() -> notFound("MODEL_PROFILE_NOT_FOUND", "Model Profile 不存在"));
        if (!PUBLIC_PROVIDER.equals(profile.providerType())) {
            throw conflict("CONNECTIVITY_TEST_NOT_SUPPORTED", "该 provider 不支持公网连通测试");
        }

        ConnectivityOutcome outcome;
        var secret = secretResolver.resolve(profile.secretRef());
        var endpoint = endpointAllowlist.resolve(profile.endpointAlias());
        if (secret.isEmpty()) {
            outcome = new ConnectivityOutcome(ConnectivityStatus.SECRET_MISSING, null, false, 0);
        } else if (endpoint.isEmpty()) {
            outcome = new ConnectivityOutcome(ConnectivityStatus.ENDPOINT_NOT_ALLOWED, null, false, 0);
        } else {
            outcome = connectivityClient.test(
                    endpoint.orElseThrow(),
                    profile.modelName(),
                    secret.orElseThrow(),
                    Duration.ofSeconds(profile.timeoutSeconds()));
        }

        var now = clock.instant();
        var row = new ConnectivityRow(
                "MCT_" + uuidHex(),
                profile.profileCode(),
                profile.configVersion(),
                outcome.status(),
                outcome.httpStatusClass(),
                outcome.modelAvailable(),
                outcome.durationMs(),
                now);
        repository.insertConnectivity(row);
        repository.updateReadiness(
                profile.profileCode(),
                profile.configVersion(),
                outcome.status() == ConnectivityStatus.SUCCEEDED);
        return toConnectivityView(row);
    }

    private ModelProfileView toView(ModelProfileConfigRow row) {
        var connectivity = repository.findLatestConnectivity(
                row.profileCode(),
                row.configVersion());
        var secretConfigured = row.secretRequired()
                && secretResolver.resolve(row.secretRef()).isPresent();
        var readiness = readiness(row, secretConfigured, connectivity.orElse(null));
        return new ModelProfileView(
                row.profileCode(),
                row.configVersion(),
                row.displayName(),
                row.providerType(),
                row.endpointAlias(),
                row.modelName(),
                row.usageScope(),
                row.enabled(),
                row.defaultForNewTask(),
                secretConfigured,
                readiness,
                row.timeoutSeconds(),
                row.retryCount(),
                connectivity.map(ModelProfileAdminService::toConnectivityView).orElse(null));
    }

    private ProfileReadiness readiness(
            ModelProfileConfigRow row,
            boolean secretConfigured,
            ConnectivityRow connectivity) {
        if ("MOCK".equals(row.providerType())
                && "READY".equals(row.databaseReadinessStatus())) {
            return ProfileReadiness.READY;
        }
        if (!secretConfigured) return ProfileReadiness.SECRET_MISSING;
        if (connectivity == null) return ProfileReadiness.NOT_TESTED;
        return connectivity.status() == ConnectivityStatus.SUCCEEDED
                ? ProfileReadiness.READY_FOR_EVALUATION_CONFIG
                : ProfileReadiness.CONNECTIVITY_FAILED;
    }

    private ModelProfileConfigRow newRow(ProfileInput input, Instant now) {
        return new ModelProfileConfigRow(
                input.profileCode(),
                configVersion(input.profileCode(), now),
                input.displayName(),
                PUBLIC_PROVIDER,
                input.endpointAlias(),
                input.modelName(),
                false,
                EVALUATION_SCOPE,
                false,
                true,
                input.secretRef(),
                "NOT_READY",
                input.timeoutSeconds(),
                input.retryCount(),
                now,
                now);
    }

    private ProfileInput parseRequest(JsonNode root, String pathProfileCode) {
        if (root == null || !root.isObject()) {
            throw invalid("INVALID_BODY", "请求体必须为 JSON object");
        }
        root.fieldNames().forEachRemaining(name -> {
            if (!ALLOWED_FIELDS.contains(name)) {
                throw invalid("UNKNOWN_FIELD", "请求包含未声明字段");
            }
        });
        var profileCode = pathProfileCode == null
                ? requiredText(root, "profileCode")
                : pathProfileCode;
        if (pathProfileCode != null && root.has("profileCode")) {
            throw invalid(
                    "PROFILE_CODE_BODY_NOT_ALLOWED",
                    "PUT 的 profileCode 只允许来自 path");
        }
        requireProfileCode(profileCode);
        var displayName = requiredText(root, "displayName");
        if (displayName.length() > 255 || hasLineBreak(displayName)) {
            throw invalid("INVALID_DISPLAY_NAME", "displayName 无效");
        }
        var provider = optionalText(root, "providerType", PUBLIC_PROVIDER);
        if (!PUBLIC_PROVIDER.equals(provider)) {
            throw invalid("INVALID_PROVIDER_TYPE", "首期只允许 PUBLIC_OPENAI_COMPATIBLE");
        }
        var usageScope = optionalText(root, "usageScope", EVALUATION_SCOPE);
        if (!EVALUATION_SCOPE.equals(usageScope)) {
            throw invalid("INVALID_USAGE_SCOPE", "PUBLIC profile 首期只允许 EVALUATION");
        }
        var endpointAlias = requiredText(root, "endpointAlias");
        if (endpointAllowlist.resolve(endpointAlias).isEmpty()) {
            throw invalid("ENDPOINT_NOT_ALLOWED", "endpointAlias 不在服务端 allowlist");
        }
        var modelName = requiredText(root, "modelName");
        if (!ALLOWED_MODELS.contains(modelName)) {
            throw invalid("MODEL_NOT_ALLOWED", "modelName 不在首期 allowlist");
        }
        var secretRef = requiredText(root, "secretRef");
        if (!secretResolver.isValidReference(secretRef)
                || !secretReferenceAllowlist.permits(endpointAlias, secretRef)) {
            throw invalid(
                    "SECRET_REFERENCE_NOT_ALLOWED",
                    "secretRef 必须匹配服务端冻结的 provider-specific 引用");
        }
        var timeoutSeconds = requiredInt(root, "timeoutSeconds", 1, 120);
        var retryCount = requiredInt(root, "retryCount", 0, 3);
        return new ProfileInput(
                profileCode,
                displayName,
                endpointAlias,
                modelName,
                secretRef,
                timeoutSeconds,
                retryCount);
    }

    private static String requiredText(JsonNode root, String field) {
        var node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank() || hasLineBreak(node.asText())) {
            throw invalid("INVALID_FIELD", field + " 必须为非空字符串");
        }
        return node.asText();
    }

    private static String optionalText(JsonNode root, String field, String fallback) {
        return root.has(field) ? requiredText(root, field) : fallback;
    }

    private static int requiredInt(JsonNode root, String field, int minimum, int maximum) {
        var node = root.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid("INVALID_FIELD", field + " 必须为整数");
        }
        var value = node.intValue();
        if (value < minimum || value > maximum) {
            throw invalid("INVALID_FIELD", field + " 超出允许范围");
        }
        return value;
    }

    private static void requireProfileCode(String profileCode) {
        if (profileCode == null || !PROFILE_CODE.matcher(profileCode).matches()) {
            throw invalid("INVALID_PROFILE_CODE", "profileCode 格式无效");
        }
    }

    private static String configVersion(String profileCode, Instant now) {
        var slug = profileCode.toLowerCase(Locale.ROOT).replace('_', '-');
        return "model-config-" + slug + "-v" + VERSION_TIME.format(now) + "-" + uuidHex().substring(0, 8);
    }

    private static String uuidHex() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static ConnectivityView toConnectivityView(ConnectivityRow row) {
        return new ConnectivityView(
                row.connectivityTestId(),
                row.status(),
                row.httpStatusClass(),
                row.modelAvailable(),
                row.durationMs(),
                row.testedAt());
    }

    private static ModelProfileAdminException invalid(String code, String message) {
        return new ModelProfileAdminException(400, code, message);
    }

    private static ModelProfileAdminException notFound(String code, String message) {
        return new ModelProfileAdminException(404, code, message);
    }

    private static ModelProfileAdminException conflict(String code, String message) {
        return new ModelProfileAdminException(409, code, message);
    }

    private record ProfileInput(
            String profileCode,
            String displayName,
            String endpointAlias,
            String modelName,
            String secretRef,
            int timeoutSeconds,
            int retryCount) {
    }
}
