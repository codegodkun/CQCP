package com.cqcp.apiserver.modelgateway;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class ModelEndpointAllowlist {

    private static final URI DEEPSEEK_CANONICAL_ORIGIN =
            URI.create("https://api.deepseek.com");

    private final Map<String, URI> endpoints;

    @Autowired
    ModelEndpointAllowlist(
            @Value("${cqcp.model-gateway.endpoints.deepseek-official}") URI deepSeekOfficial) {
        this(Map.of("deepseek-official", deepSeekOfficial));
    }

    ModelEndpointAllowlist(Map<String, URI> endpoints) {
        this.endpoints = Map.copyOf(endpoints);
        this.endpoints.forEach(ModelEndpointAllowlist::validateConfiguredEndpoint);
    }

    Optional<URI> resolve(String alias) {
        return Optional.ofNullable(endpoints.get(alias));
    }

    static InetAddress[] resolveValidatedAddresses(URI uri) throws UnknownHostException {
        return resolveValidatedAddresses(uri, InetAddress::getAllByName);
    }

    static InetAddress[] resolveValidatedAddresses(
            URI uri,
            HostAddressResolver resolver) throws UnknownHostException {
        var addresses = resolver.resolve(uri.getHost());
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException("Endpoint did not resolve");
        }
        for (var address : addresses) {
            if (isForbidden(address)) {
                throw new ForbiddenEndpointAddressException();
            }
        }
        return addresses.clone();
    }

    private static void validateConfiguredEndpoint(String alias, URI uri) {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(uri, "uri");
        if (alias.isBlank()
                || !"https".equalsIgnoreCase(uri.getScheme())
                || !uri.isAbsolute()
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Invalid model endpoint allowlist entry");
        }
        if ("deepseek-official".equals(alias)
                && (!DEEPSEEK_CANONICAL_ORIGIN.getHost().equalsIgnoreCase(uri.getHost())
                        || uri.getPort() != -1
                        || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))) {
            throw new IllegalArgumentException(
                    "deepseek-official must use the frozen canonical origin");
        }
        var host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if ("localhost".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || isLiteralAddress(host) && isForbidden(parseLiteral(host))) {
            throw new IllegalArgumentException("Forbidden model endpoint allowlist entry");
        }
    }

    private static boolean isLiteralAddress(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+");
    }

    private static InetAddress parseLiteral(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Invalid literal endpoint address");
        }
    }

    static boolean isForbidden(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        var bytes = address.getAddress();
        if (bytes.length == 4) {
            return isForbiddenIpv4(bytes);
        }
        if (bytes.length == 16) {
            if (isIpv4Mapped(bytes)) {
                return isForbiddenIpv4(new byte[] {
                        bytes[12], bytes[13], bytes[14], bytes[15]
                });
            }
            var first = Byte.toUnsignedInt(bytes[0]);
            var second = Byte.toUnsignedInt(bytes[1]);
            var third = Byte.toUnsignedInt(bytes[2]);
            var fourth = Byte.toUnsignedInt(bytes[3]);
            // Fail closed：只接纳 2000::/3 global unicast，并拒绝其中的 IETF special、
            // documentation 与 transition ranges。这样 NAT64、IPv4-compatible、
            // ULA 等所有非全局地址默认拒绝，不依赖 InetAddress 的平台分类差异。
            return (first & 0xe0) != 0x20
                    || (first == 0x20 && second == 0x01 && (third & 0xfe) == 0)
                    || (first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8)
                    || (first == 0x20 && second == 0x02)
                    || (first == 0x3f && (second & 0xf0) == 0xf0);
        }
        return true;
    }

    private static boolean isForbiddenIpv4(byte[] bytes) {
        var first = Byte.toUnsignedInt(bytes[0]);
        var second = Byte.toUnsignedInt(bytes[1]);
        var third = Byte.toUnsignedInt(bytes[2]);
        return first == 0
                || first == 10
                || first == 127
                || first >= 224
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 0 && (third == 0 || third == 2))
                || (first == 192 && second == 88 && third == 99)
                || (first == 192 && second == 168)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113);
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (var index = 0; index < 10; index++) {
            if (bytes[index] != 0) return false;
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }
}

@FunctionalInterface
interface HostAddressResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
}

final class ForbiddenEndpointAddressException extends UnknownHostException {

    ForbiddenEndpointAddressException() {
        super("Endpoint resolves to a forbidden address");
    }
}
