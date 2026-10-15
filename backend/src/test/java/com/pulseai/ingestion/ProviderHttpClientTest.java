package com.pulseai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProviderHttpClientTest {
    @Test void classifiesGNewsQuotaAndSubscriptionErrorsWithoutLoggingProviderText() {
        var client = new ProviderHttpClient();
        URI gnews = URI.create("https://gnews.io/api/v4/top-headlines");

        assertThat(client.failureReason(gnews, 403,
                "{\"errors\":[\"You have reached your request limit for today\"]}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEqualTo("quota-exhausted");
        assertThat(client.failureReason(gnews, 403,
                "{\"errors\":[\"Your subscription has expired\"]}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEqualTo("subscription-expired");
        assertThat(client.failureReason(gnews, 403, new byte[0])).isEqualTo("forbidden");
    }

    @Test void honorsRetryAfterWithoutRepeatedRequests() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/feed", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Retry-After", "120");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new ProviderHttpClient();
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/feed");
            assertThatThrownBy(() -> client.fetch(uri, "application/xml", null))
                    .isInstanceOfSatisfying(ProviderRequestException.class, exception -> {
                        assertThat(exception.statusCode()).isEqualTo(429);
                        assertThat(exception.reason()).isEqualTo("http-status");
                    });
            assertThatThrownBy(() -> client.fetch(uri, "application/xml", null)).isInstanceOf(IOException.class).hasMessageContaining("cooldown");
            assertThat(requests).hasValue(1);
        } finally { server.stop(0); }
    }

    @Test void retriesTransientServerFailuresOnceWithBoundedBackoff() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/feed", exchange -> {
            if (requests.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(502, -1);
            } else {
                byte[] body = "<rss/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            }
            exchange.close();
        });
        server.start();
        try {
            var client = new ProviderHttpClient();
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/feed");

            assertThat(new String(client.fetch(uri, "application/xml", null), java.nio.charset.StandardCharsets.UTF_8))
                    .isEqualTo("<rss/>");
            assertThat(requests).hasValue(2);
        } finally { server.stop(0); }
    }

    @Test void exposesPublisherRedirectLocationWithoutAutomaticallyFollowingIt() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/story", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Location", "/latest-story");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new ProviderHttpClient();
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/story");

            ProviderHttpResponse response = client.fetchOnceWithStatus(uri, "text/html");

            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(response.redirectLocation()).isEqualTo(URI.create("/latest-story"));
            assertThat(requests).hasValue(1);
        } finally { server.stop(0); }
    }

    @Test void reportsHttpStatusWithoutReturningProviderCredentials() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/feed", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new ProviderHttpClient();
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/feed");

            assertThatThrownBy(() -> client.fetch(uri, "application/json", "test-secret"))
                    .isInstanceOfSatisfying(ProviderRequestException.class, exception -> {
                        assertThat(exception.statusCode()).isEqualTo(403);
                        assertThat(exception.getMessage()).contains("403").doesNotContain("test-secret");
                    });
        } finally { server.stop(0); }
    }

    @Test void rejectsOversizedResponseDuringStreaming() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/feed", exchange -> {
            exchange.sendResponseHeaders(200, 6 * 1024 * 1024);
            try (var output = exchange.getResponseBody()) {
                byte[] chunk = new byte[64 * 1024];
                for (int i = 0; i < 96; i++) output.write(chunk);
            } catch (IOException cancelled) { /* The bounded client closes the stream. */ }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var client = new ProviderHttpClient();
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/feed");
            assertThatThrownBy(() -> client.fetch(uri, "application/xml", null)).isInstanceOf(IOException.class);
        } finally { server.stop(0); }
    }
}
