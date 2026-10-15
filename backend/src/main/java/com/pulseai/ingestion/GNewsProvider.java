package com.pulseai.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GNewsProvider implements NewsProvider {

    private static final Logger logger = LoggerFactory.getLogger(GNewsProvider.class);
    private static final Set<String> SUPPORTED_CATEGORIES = Set.of(
            "general", "world", "nation", "business", "technology", "entertainment", "sports", "science", "health");
    private static final long MINIMUM_REQUEST_INTERVAL_MS = 1_000;

    private final String key;
    private final List<String> categories;
    private final ProviderHttpClient client;
    private final ObjectMapper json;

    public GNewsProvider(
            ProviderHttpClient client,
            ObjectMapper json,
            @Value("${app.news.gnews-key:}") String key,
            @Value("${app.news.gnews-category:general}") String legacyCategory,
            @Value("${app.news.gnews-categories:}") String configuredCategories) {
        this.client = client;
        this.json = json;
        this.key = key.trim();
        this.categories = parseCategories(configuredCategories, legacyCategory);
    }

    @Override
    public String name() {
        return "gnews";
    }

    @Override
    public boolean isConfigured() {
        return !key.isBlank() && !key.startsWith("REPLACE_");
    }

    @Override
    public ProviderFetchResult fetch() {
        List<ProviderArticle> articles = new ArrayList<>();
        int failures = 0;

        for (int index = 0; index < categories.size(); index++) {
            String category = categories.get(index);
            if (index > 0) {
                try {
                    Thread.sleep(MINIMUM_REQUEST_INTERVAL_MS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failures++;
                    break;
                }
            }

            try {
                URI uri = URI.create("https://gnews.io/api/v4/top-headlines?lang=en&max=10&category=" + category);
                // GNews accepts X-Api-Key authentication; keep credentials out of request URLs.
                var root = json.readTree(client.fetch(uri, "application/json", key));
                var rows = root.path("articles");
                if (!rows.isArray()) {
                    failures++;
                    logger.warn("News provider response was invalid provider={} category={} reason=missing-articles-array", name(), category);
                    continue;
                }
                for (var row : rows) {
                    var source = row.path("source");
                    articles.add(new ProviderArticle(
                            text(row, "id"),
                            text(row, "title"),
                            text(row, "description"),
                            text(row, "url"),
                            text(row, "image"),
                            null,
                            text(row, "publishedAt"),
                            text(source, "name"),
                            text(source, "url"),
                            category,
                            text(row, "lang")));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                failures++;
                break;
            } catch (IOException | RuntimeException exception) {
                failures++;
                int httpStatus = exception instanceof ProviderRequestException providerFailure
                        ? providerFailure.statusCode()
                        : 0;
                String reason = exception instanceof ProviderRequestException providerFailure
                        ? providerFailure.reason()
                        : exception instanceof IOException ? "transport-error" : "invalid-response";
                logger.warn(
                        "News provider request failed provider={} category={} httpStatus={} reason={}",
                        name(),
                        category,
                        httpStatus,
                        reason);
                if (exception instanceof ProviderRequestException providerFailure
                        && (httpStatus == 401 || httpStatus == 403 || httpStatus == 429
                        || "rate-limit-cooldown".equals(providerFailure.reason()))) {
                    // Authentication, plan quota, and provider cooldown apply across this provider's categories.
                    break;
                }
            }
        }
        return new ProviderFetchResult(articles, articles.size(), failures);
    }

    private static List<String> parseCategories(String configured, String legacyCategory) {
        String value = configured == null || configured.isBlank() ? legacyCategory : configured;
        List<String> parsed = Arrays.stream(value.split(","))
                .map(category -> category.trim().toLowerCase(Locale.ROOT))
                .filter(category -> !category.isBlank())
                .toList();
        if (parsed.isEmpty()) {
            throw new IllegalStateException("At least one GNews category must be configured.");
        }
        for (String category : parsed) {
            if (!SUPPORTED_CATEGORIES.contains(category)) {
                throw new IllegalStateException("Unsupported GNews category: " + category);
            }
        }
        return List.copyOf(new LinkedHashSet<>(parsed));
    }

    private String text(com.fasterxml.jackson.databind.JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() || value.isMissingNode() ? null : value.asText();
    }
}
