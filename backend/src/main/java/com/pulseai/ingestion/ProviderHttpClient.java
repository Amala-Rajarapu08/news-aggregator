package com.pulseai.ingestion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

@Component
public class ProviderHttpClient {

    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MS = 200;
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final Map<String, Cooldown> cooldowns = new ConcurrentHashMap<>();

    public byte[] fetch(URI uri, String accept, String apiKey) throws IOException, InterruptedException {
        ProviderHttpResponse response = fetchWithStatus(uri, accept, apiKey);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw ProviderRequestException.httpStatus(
                    response.statusCode(), failureReason(uri, response.statusCode(), response.body()));
        }
        return response.body();
    }

    String failureReason(URI uri, int statusCode, byte[] body) {
        if (statusCode == 403 && "gnews.io".equalsIgnoreCase(uri.getHost())) {
            String responseText = new String(body, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (responseText.contains("request limit") || responseText.contains("daily quota")
                    || responseText.contains("quota limit")) return "quota-exhausted";
            if (responseText.contains("subscription") && responseText.contains("expired")) return "subscription-expired";
            return "forbidden";
        }
        return "http-status";
    }

    public ProviderHttpResponse fetchWithStatus(URI uri, String accept, String apiKey)
            throws IOException, InterruptedException {
        if (uri.getHost() == null || uri.getUserInfo() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IOException("Invalid provider URL.");
        }

        String cooldownKey = uri.getHost().toLowerCase(Locale.ROOT);
        Cooldown activeCooldown = cooldowns.get(cooldownKey);
        if (activeCooldown != null && activeCooldown.until().isAfter(Instant.now())) {
            throw ProviderRequestException.cooldown(activeCooldown.statusCode());
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", accept)
                .header("User-Agent", "PulseAI-NewsIngestion/1.0");
        if (apiKey != null) {
            builder.header("X-Api-Key", apiKey);
        }
        HttpRequest request = builder.GET().build();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpResponse<byte[]> response;
            CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, info -> new LimitedBody());
            try {
                response = future.get(15, TimeUnit.SECONDS);
            } catch (TimeoutException exception) {
                future.cancel(true);
                if (attempt < MAX_ATTEMPTS) {
                    waitBeforeRetry(attempt);
                    continue;
                }
                throw ProviderRequestException.failure("timeout", "Provider deadline exceeded.", exception);
            } catch (ExecutionException exception) {
                future.cancel(true);
                Throwable cause = exception.getCause();
                if (cause instanceof IOException ioException
                        && ioException.getMessage() != null
                        && ioException.getMessage().contains("exceeds 5 MiB")) {
                    throw ProviderRequestException.failure("response-too-large", "Provider body exceeds 5 MiB.", ioException);
                }
                if (attempt < MAX_ATTEMPTS) {
                    waitBeforeRetry(attempt);
                    continue;
                }
                throw ProviderRequestException.failure("transport-error", "Provider transport failed.", exception);
            } catch (InterruptedException exception) {
                future.cancel(true);
                throw exception;
            }

            if (isRateLimited(uri, response)) {
                cooldowns.put(cooldownKey, cooldown(uri, response));
                return providerResponse(response);
            }
            if (isRetryable(response.statusCode()) && attempt < MAX_ATTEMPTS) {
                waitBeforeRetry(attempt);
                continue;
            }
            return providerResponse(response);
        }

        throw ProviderRequestException.failure("transport-error", "Provider request failed.", null);
    }

    /** A single bounded request for a publisher metadata page; redirects are handled by the caller. */
    ProviderHttpResponse fetchOnceWithStatus(URI uri, String accept)
            throws IOException, InterruptedException {
        if (uri.getHost() == null || uri.getUserInfo() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IOException("Invalid provider URL.");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", accept)
                .header("User-Agent", "PulseAI-NewsIngestion/1.0")
                .GET()
                .build();
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, info -> new LimitedBody());
        try {
            HttpResponse<byte[]> response = future.get(10, TimeUnit.SECONDS);
            return providerResponse(response);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new IOException("Publisher metadata request timed out.", exception);
        } catch (ExecutionException exception) {
            future.cancel(true);
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Publisher metadata request failed.", cause);
        } catch (InterruptedException exception) {
            future.cancel(true);
            throw exception;
        }
    }

    private ProviderHttpResponse providerResponse(HttpResponse<byte[]> response) {
        URI location = null;
        String rawLocation = response.headers().firstValue("Location").orElse(null);
        if (rawLocation != null) {
            try {
                location = URI.create(rawLocation);
            } catch (IllegalArgumentException ignored) {
                // The resolver treats malformed redirect targets as unusable metadata.
            }
        }
        return new ProviderHttpResponse(response.statusCode(), response.body(), location,
                response.headers().firstValue("Content-Type").orElse(null));
    }

    private boolean isRateLimited(URI uri, HttpResponse<?> response) {
        return (response.statusCode() == 403 && "gnews.io".equalsIgnoreCase(uri.getHost()))
                || response.statusCode() == 429
                || response.statusCode() == 503;
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 500 || statusCode == 502 || statusCode == 504;
    }

    private Cooldown cooldown(URI uri, HttpResponse<?> response) {
        if (response.statusCode() == 403 && "gnews.io".equalsIgnoreCase(uri.getHost())) {
            Instant reset = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            return new Cooldown(reset, response.statusCode());
        }

        long seconds = 3600;
        String retryAfter = response.headers().firstValue("Retry-After").orElse("");
        try {
            seconds = Long.parseLong(retryAfter);
        } catch (NumberFormatException exception) {
            try {
                seconds = Duration.between(
                        Instant.now(), ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                        .toSeconds();
            } catch (RuntimeException ignored) {
                // Use the bounded one-hour default when Retry-After is absent or malformed.
            }
        }
        return new Cooldown(Instant.now().plusSeconds(Math.max(60, Math.min(86400, seconds))), response.statusCode());
    }

    private void waitBeforeRetry(int attempt) throws InterruptedException {
        long backoff = RETRY_BACKOFF_MS * (1L << Math.max(0, attempt - 1));
        Thread.sleep(backoff);
    }

    private record Cooldown(Instant until, int statusCode) {
    }

    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (bytes.size() + buffer.remaining() > MAX_RESPONSE_BYTES) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Provider body exceeds 5 MiB."));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable error) {
            body.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
