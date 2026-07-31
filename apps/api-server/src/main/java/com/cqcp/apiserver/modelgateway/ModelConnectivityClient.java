package com.cqcp.apiserver.modelgateway;

import static com.cqcp.apiserver.modelgateway.ModelProfileAdminModels.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

interface ModelConnectivityClient {

    ConnectivityOutcome test(
            URI endpoint,
            String modelName,
            String secret,
            Duration timeout);
}

@FunctionalInterface
interface ModelNetworkAttemptObserver {

    ModelNetworkAttemptObserver NO_OP = () -> {};

    void beforeNetworkAttempt();
}

@FunctionalInterface
interface EndpointAddressResolver {
    InetAddress[] resolveAndValidate(URI endpoint) throws IOException;
}

@FunctionalInterface
interface PinnedModelHttpTransport {
    PinnedHttpResponse get(
            URI endpoint,
            String secret,
            Duration timeout,
            InetAddress[] pinnedAddresses) throws IOException, InterruptedException;
}

record PinnedHttpResponse(int statusCode, byte[] body) {
    PinnedHttpResponse {
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}

/**
 * Connectivity client with DNS-to-connection pinning.
 *
 * <p>The allowlist resolves and validates every address once. The Apache
 * transport receives that exact array through a request-scoped DnsResolver,
 * so the connection cannot perform a second system lookup and rebind to a
 * private address.
 */
@Component
final class JdkModelConnectivityClient implements ModelConnectivityClient {

    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final EndpointAddressResolver addressResolver;
    private final PinnedModelHttpTransport transport;
    private final ModelNetworkAttemptObserver networkAttemptObserver;

    @Autowired
    JdkModelConnectivityClient(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this(
                objectMapper,
                ModelEndpointAllowlist::resolveValidatedAddresses,
                new ApachePinnedModelHttpTransport(),
                metricObserver(meterRegistry));
    }

    JdkModelConnectivityClient(
            ObjectMapper objectMapper,
            EndpointAddressResolver addressResolver,
            PinnedModelHttpTransport transport) {
        this(
                objectMapper,
                addressResolver,
                transport,
                ModelNetworkAttemptObserver.NO_OP);
    }

    JdkModelConnectivityClient(
            ObjectMapper objectMapper,
            EndpointAddressResolver addressResolver,
            PinnedModelHttpTransport transport,
            ModelNetworkAttemptObserver networkAttemptObserver) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.addressResolver = Objects.requireNonNull(addressResolver, "addressResolver");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.networkAttemptObserver = Objects.requireNonNull(
                networkAttemptObserver,
                "networkAttemptObserver");
    }

    private static ModelNetworkAttemptObserver metricObserver(MeterRegistry meterRegistry) {
        var counter = Objects.requireNonNull(meterRegistry, "meterRegistry")
                .counter("cqcp.model.connectivity.network.attempts");
        return counter::increment;
    }

    @Override
    public ConnectivityOutcome test(
            URI endpoint,
            String modelName,
            String secret,
            Duration timeout) {
        var started = System.nanoTime();
        try {
            networkAttemptObserver.beforeNetworkAttempt();
            var pinnedAddresses = addressResolver.resolveAndValidate(endpoint);
            var response = transport.get(endpoint, secret, timeout, pinnedAddresses);
            var status = response.statusCode();
            if (status >= 300 && status < 400) {
                return outcome(ConnectivityStatus.REDIRECT_REJECTED, statusClass(status), false, started);
            }
            if (status == 401 || status == 403) {
                return outcome(ConnectivityStatus.AUTHENTICATION_FAILED, statusClass(status), false, started);
            }
            if (status == 429) {
                return outcome(ConnectivityStatus.RATE_LIMITED, statusClass(status), false, started);
            }
            if (status >= 500) {
                return outcome(ConnectivityStatus.UPSTREAM_5XX, statusClass(status), false, started);
            }
            if (status < 200 || status >= 300) {
                return outcome(ConnectivityStatus.NETWORK_ERROR, statusClass(status), false, started);
            }
            var bytes = response.body();
            if (bytes.length > MAX_RESPONSE_BYTES) {
                return outcome(ConnectivityStatus.MALFORMED_RESPONSE, statusClass(status), false, started);
            }
            final com.fasterxml.jackson.databind.JsonNode root;
            try {
                root = objectMapper.readTree(bytes);
            } catch (IOException | RuntimeException exception) {
                return outcome(
                        ConnectivityStatus.MALFORMED_RESPONSE,
                        statusClass(status),
                        false,
                        started);
            }
            var data = root == null ? null : root.get("data");
            if (data == null || !data.isArray()) {
                return outcome(ConnectivityStatus.MALFORMED_RESPONSE, statusClass(status), false, started);
            }
            var modelAvailable = java.util.stream.StreamSupport.stream(data.spliterator(), false)
                    .map(node -> node.get("id"))
                    .anyMatch(id -> id != null && id.isTextual() && modelName.equals(id.asText()));
            return outcome(
                    modelAvailable ? ConnectivityStatus.SUCCEEDED : ConnectivityStatus.MODEL_NOT_FOUND,
                    statusClass(status),
                    modelAvailable,
                    started);
        } catch (ForbiddenEndpointAddressException exception) {
            return outcome(ConnectivityStatus.ENDPOINT_NOT_ALLOWED, null, false, started);
        } catch (InterruptedIOException exception) {
            return outcome(ConnectivityStatus.TIMEOUT, null, false, started);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return outcome(ConnectivityStatus.NETWORK_ERROR, null, false, started);
        } catch (IOException | RuntimeException exception) {
            return outcome(ConnectivityStatus.NETWORK_ERROR, null, false, started);
        }
    }

    private static ConnectivityOutcome outcome(
            ConnectivityStatus status,
            String httpStatusClass,
            boolean modelAvailable,
            long startedNanos) {
        var duration = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
        return new ConnectivityOutcome(status, httpStatusClass, modelAvailable, duration);
    }

    private static String statusClass(int status) {
        return (status / 100) + "XX";
    }

    static final class ApachePinnedModelHttpTransport implements PinnedModelHttpTransport {

        @Override
        public PinnedHttpResponse get(
                URI endpoint,
                String secret,
                Duration timeout,
                InetAddress[] pinnedAddresses) throws IOException {
            var expectedHost = endpoint.getHost();
            var pinned = pinnedAddresses.clone();
            DnsResolver dnsResolver = new DnsResolver() {
                @Override
                public InetAddress[] resolve(String host) throws java.net.UnknownHostException {
                    requireExpectedHost(host);
                    return pinned.clone();
                }

                @Override
                public String resolveCanonicalHostname(String host) throws java.net.UnknownHostException {
                    requireExpectedHost(host);
                    return expectedHost;
                }

                private void requireExpectedHost(String host) throws java.net.UnknownHostException {
                    if (!expectedHost.equalsIgnoreCase(host)) {
                        throw new java.net.UnknownHostException("Unexpected transport host");
                    }
                }
            };
            var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setDnsResolver(dnsResolver)
                    .build();
            var requestTimeout = Timeout.ofMilliseconds(timeout.toMillis());
            var requestConfig = RequestConfig.custom()
                    .setConnectTimeout(requestTimeout)
                    .setConnectionRequestTimeout(requestTimeout)
                    .setResponseTimeout(requestTimeout)
                    .build();
            try (var client = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setDefaultRequestConfig(requestConfig)
                    .disableRedirectHandling()
                    .disableAutomaticRetries()
                    .build()) {
                var request = new HttpGet(endpoint.resolve("/models"));
                request.setHeader("Accept", "application/json");
                request.setHeader("Authorization", "Bearer " + secret);
                return client.execute(request, response -> {
                    var entity = response.getEntity();
                    var bytes = entity == null
                            ? new byte[0]
                            : entity.getContent().readNBytes(MAX_RESPONSE_BYTES + 1);
                    return new PinnedHttpResponse(response.getCode(), bytes);
                });
            }
        }
    }
}
