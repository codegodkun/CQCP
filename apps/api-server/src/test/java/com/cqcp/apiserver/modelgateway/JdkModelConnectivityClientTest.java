package com.cqcp.apiserver.modelgateway;

import static com.cqcp.apiserver.modelgateway.ModelProfileAdminModels.ConnectivityStatus;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdkModelConnectivityClientTest {

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicLong delayMillis = new AtomicLong();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> body =
            new AtomicReference<>("{\"data\":[{\"id\":\"deepseek-v4-pro\"}]}");
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models", exchange -> {
            requestCount.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                if (delayMillis.get() > 0) {
                    Thread.sleep(delayMillis.get());
                }
                var bytes = body.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (status.get() == 429 || status.get() == 503) {
                    exchange.getResponseHeaders().set("Retry-After", "0");
                }
                exchange.sendResponseHeaders(status.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void successRequiresSavedModelToAppearInModelsResponse() {
        var outcome = client().test(endpoint(), "deepseek-v4-pro", "SECRET_SENTINEL", Duration.ofSeconds(2));

        assertThat(outcome.status()).isEqualTo(ConnectivityStatus.SUCCEEDED);
        assertThat(outcome.modelAvailable()).isTrue();
        assertThat(outcome.httpStatusClass()).isEqualTo("2XX");
        assertThat(authorization.get()).isEqualTo("Bearer SECRET_SENTINEL");
    }

    @Test
    void missingModelFailsClosed() {
        var outcome = client().test(endpoint(), "deepseek-v4-flash", "secret", Duration.ofSeconds(2));

        assertThat(outcome.status()).isEqualTo(ConnectivityStatus.MODEL_NOT_FOUND);
        assertThat(outcome.modelAvailable()).isFalse();
    }

    @Test
    void malformedJsonFailsClosed() {
        body.set("not-json");

        var outcome = client().test(endpoint(), "deepseek-v4-pro", "secret", Duration.ofSeconds(2));

        assertThat(outcome.status()).isEqualTo(ConnectivityStatus.MALFORMED_RESPONSE);
        assertThat(outcome.modelAvailable()).isFalse();
    }

    @Test
    void stableStatusCategoriesDoNotExposeUpstreamBodies() {
        assertStatus(401, "upstream-secret-one", ConnectivityStatus.AUTHENTICATION_FAILED);
        assertStatus(403, "upstream-secret-two", ConnectivityStatus.AUTHENTICATION_FAILED);
        assertStatus(429, "upstream-secret-three", ConnectivityStatus.RATE_LIMITED);
        assertStatus(503, "upstream-secret-four", ConnectivityStatus.UPSTREAM_5XX);
        assertStatus(302, "upstream-secret-five", ConnectivityStatus.REDIRECT_REJECTED);
    }

    @Test
    void timeoutFailsClosed() {
        delayMillis.set(500);

        var outcome = client().test(endpoint(), "deepseek-v4-pro", "secret", Duration.ofMillis(50));

        assertThat(outcome.status()).isEqualTo(ConnectivityStatus.TIMEOUT);
        assertThat(outcome.modelAvailable()).isFalse();
    }

    @Test
    void forbiddenResolvedAddressStopsBeforeTransport() {
        var transportCalls = new AtomicInteger();
        var client = new JdkModelConnectivityClient(
                new ObjectMapper(),
                endpoint -> ModelEndpointAllowlist.resolveValidatedAddresses(
                        endpoint,
                        host -> new InetAddress[] {
                                InetAddress.getByName("127.0.0.1")
                        }),
                (endpoint, secret, timeout, pinned) -> {
                    transportCalls.incrementAndGet();
                    return new PinnedHttpResponse(200, body.get().getBytes(StandardCharsets.UTF_8));
                });

        var outcome = client.test(
                URI.create("https://api.deepseek.com"),
                "deepseek-v4-pro",
                "secret",
                Duration.ofSeconds(1));

        assertThat(outcome.status()).isEqualTo(ConnectivityStatus.ENDPOINT_NOT_ALLOWED);
        assertThat(transportCalls).hasValue(0);
    }

    @Test
    void observerCountsEachConnectivityNetworkAttempt() {
        var observedAttempts = new AtomicInteger();
        var client = new JdkModelConnectivityClient(
                new ObjectMapper(),
                endpoint -> {
                    throw new IOException("blocked for test");
                },
                (endpoint, secret, timeout, pinned) -> {
                    throw new AssertionError("transport must not be reached");
                },
                observedAttempts::incrementAndGet);

        var outcome = client.test(
                URI.create("https://api.deepseek.com"),
                "deepseek-v4-pro",
                "secret",
                Duration.ofSeconds(1));

        assertThat(outcome.status()).isEqualTo(ConnectivityStatus.NETWORK_ERROR);
        assertThat(observedAttempts).hasValue(1);
    }

    private void assertStatus(int httpStatus, String upstreamBody, ConnectivityStatus expected) {
        status.set(httpStatus);
        body.set(upstreamBody);
        requestCount.set(0);

        var outcome = client().test(endpoint(), "deepseek-v4-pro", "secret", Duration.ofSeconds(2));

        assertThat(outcome.status()).isEqualTo(expected);
        assertThat(outcome.httpStatusClass()).isEqualTo((httpStatus / 100) + "XX");
        assertThat(outcome.toString()).doesNotContain(upstreamBody);
        assertThat(requestCount)
                .as("connectivity test must never automatically retry /models")
                .hasValue(1);
    }

    private JdkModelConnectivityClient client() {
        return new JdkModelConnectivityClient(
                new ObjectMapper(),
                endpoint -> {
                    try {
                        return new InetAddress[] {InetAddress.getByName("127.0.0.1")};
                    } catch (UnknownHostException exception) {
                        throw new IOException(exception);
                    }
                },
                new JdkModelConnectivityClient.ApachePinnedModelHttpTransport());
    }

    private URI endpoint() {
        return URI.create("http://dns-rebind.invalid:" + server.getAddress().getPort());
    }
}
