package com.cqcp.apiserver.modelgateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelEndpointAllowlistTest {

    @Test
    void resolvesOnlyConfiguredAlias() {
        var allowlist = new ModelEndpointAllowlist(
                Map.of("deepseek-official", URI.create("https://api.deepseek.com")));

        assertThat(allowlist.resolve("deepseek-official")).isPresent();
        assertThat(allowlist.resolve("https://attacker.invalid")).isEmpty();
    }

    @Test
    void rejectsHttpPrivateLoopbackAndUriCredentials() {
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("bad", URI.create("http://api.deepseek.com"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("bad", URI.create("https://127.0.0.1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("bad", URI.create("https://10.0.0.8"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("bad", URI.create("https://169.254.10.20"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("bad", URI.create("https://100.64.0.1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("bad", URI.create("https://192.0.2.1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("bad", URI.create("https://[::1]"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("bad", URI.create("https://[fc00::1]"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("bad", URI.create("https://[2001:db8::1]"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("bad", URI.create("https://user:pass@api.deepseek.com"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deepSeekAliasIsBoundToFrozenCanonicalOrigin() {
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("deepseek-official", URI.create("https://example.com"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("deepseek-official", URI.create("https://api.deepseek.com:8443"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelEndpointAllowlist(
                Map.of("deepseek-official", URI.create("https://api.deepseek.com/v1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oneForbiddenAddressRejectsTheEntireDnsAnswer() throws Exception {
        var endpoint = URI.create("https://api.deepseek.com");

        assertThatThrownBy(() -> ModelEndpointAllowlist.resolveValidatedAddresses(
                endpoint,
                host -> new InetAddress[] {
                        InetAddress.getByName("8.8.8.8"),
                        InetAddress.getByName("127.0.0.1")
                }))
                .isInstanceOf(java.net.UnknownHostException.class);

        assertThat(ModelEndpointAllowlist.resolveValidatedAddresses(
                endpoint,
                host -> new InetAddress[] {
                        InetAddress.getByName("8.8.8.8"),
                        InetAddress.getByName("1.1.1.1")
                }))
                .extracting(InetAddress::getHostAddress)
                .containsExactly("8.8.8.8", "1.1.1.1");
    }

    @Test
    void rejectsIpv6TranslationTransitionAndSpecialUseAddresses() throws Exception {
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("64:ff9b::c0a8:1")))
                .isTrue();
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("64:ff9b:1::a00:1")))
                .isTrue();
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("2001::a00:1")))
                .isTrue();
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("2002:a00:1::")))
                .isTrue();
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("3fff::1")))
                .isTrue();
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("2001:4860:4860::8888")))
                .isFalse();
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("2606:4700:4700::1111")))
                .isFalse();
    }

    @Test
    void ipv4SpecialUseMasksRejectOnlyTheRegisteredRanges() throws Exception {
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("192.0.0.8")))
                .isTrue();
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("192.0.2.8")))
                .isTrue();
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("192.88.99.2")))
                .isTrue();
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("198.51.100.8")))
                .isTrue();

        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("192.0.1.8")))
                .isFalse();
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("198.51.99.8")))
                .isFalse();
        assertThat(ModelEndpointAllowlist.isForbidden(
                        InetAddress.getByName("198.51.101.8")))
                .isFalse();
    }
}
