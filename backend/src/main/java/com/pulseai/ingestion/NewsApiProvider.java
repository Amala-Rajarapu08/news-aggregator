package com.pulseai.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NewsApiProvider implements NewsProvider {

    private static final String DEFAULT_ENDPOINT = "https://newsapi.org/v2/top-headlines";
    private static final long MINIMUM_REQUEST_INTERVAL_MS = 1_000;

    private static final Logger logger = LoggerFactory.getLogger(NewsApiProvider.class);
    private static final Set<String> SUPPORTED_CATEGORIES = Set.of(
            "business", "entertainment", "general", "health", "science", "sports", "technology");

    private final ObjectMapper objectMapper;
    private final ProviderHttpClient transport;
    private final String apiKey;
    private final String endpoint;
    private final String country;
    private final List<String> categories;

    public NewsApiProvider(
            ObjectMapper objectMapper,
            ProviderHttpClient transport,
            @Value("${app.news.api-key:}") String apiKey,
            @Value("${app.news.api-base-url:}") String endpoint,
            @Value("${app.news.api-categories:}") String configuredCategories,
            @Value("${app.news.api-country:us}") String country) {
        this.objectMapper = objectMapper;
        this.transport = transport;
        this.apiKey = apiKey.trim();
        this.endpoint = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint.trim();
        this.country = country == null || !country.trim().matches("(?i)[a-z]{2}")
                ? "us" : country.trim().toLowerCase(Locale.ROOT);
        this.categories = parseCategories(configuredCategories);
    }

    @Override
    public String name() {
        return "news-api";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiKey.startsWith("REPLACE_") && !endpoint.isBlank();
    }

    @Override
    public ProviderFetchResult fetch() {
        List<ProviderArticle> articles = new ArrayList<>();
        List<String> requestCategories = categories.isEmpty()
                ? List.of(categoryFromEndpoint() == null ? "" : categoryFromEndpoint())
                : categories;
        int failures = 0;

        for (int index = 0; index < requestCategories.size(); index++) {
            String requestedCategory = requestCategories.get(index);
            try {
                if (index > 0) Thread.sleep(MINIMUM_REQUEST_INTERVAL_MS);
                URI uri = requestUri(requestedCategory.isBlank() ? null : requestedCategory);
                JsonNode root = objectMapper.readTree(transport.fetch(uri, "application/json", apiKey));
                JsonNode rows = root.path("articles");
                if (!rows.isArray()) {
                    failures++;
                    logger.warn("News API response was invalid provider={} category={} reason=missing-articles-array", name(), requestedCategory);
                    continue;
                }
                String articleCategory = effectiveCategory(requestedCategory);
                for (JsonNode row : rows) {
                    JsonNode source = row.path("source");
                    articles.add(new ProviderArticle(
                            text(row, "url"),
                            text(row, "title"),
                            text(row, "description"),
                            text(row, "url"),
                            text(row, "urlToImage"),
                            text(row, "author"),
                            text(row, "publishedAt"),
                            text(source, "name"),
                            null,
                            articleCategory,
                            "en"));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                failures++;
                break;
            } catch (IOException | IllegalArgumentException exception) {
                failures++;
                int httpStatus = exception instanceof ProviderRequestException providerFailure
                        ? providerFailure.statusCode()
                        : 0;
                String reason = exception instanceof ProviderRequestException providerFailure
                        ? providerFailure.reason()
                        : exception.getClass().getSimpleName();
                logger.warn(
                        "News API request failed provider={} category={} httpStatus={} reason={}",
                        name(),
                        requestedCategory.isBlank() ? "configured-endpoint" : requestedCategory,
                        httpStatus,
                        reason);
                if (exception instanceof ProviderRequestException providerFailure
                        && (httpStatus == 401 || httpStatus == 403 || httpStatus == 429
                        || "rate-limit-cooldown".equals(providerFailure.reason()))) {
                    // Credentials and quota are provider-wide; later categories would repeat the same failure.
                    break;
                }
            }
        }
        return new ProviderFetchResult(articles, articles.size(), failures);
    }

    private URI requestUri(String category) throws IOException {
        URI base;
        try {
            base = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new IOException("News API endpoint is invalid.");
        }
        if ((!"https".equalsIgnoreCase(base.getScheme()) && !"http".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null
                || base.getUserInfo() != null) {
            throw new IOException("News API endpoint must be an HTTP(S) URL without user information.");
        }

        String request = category == null ? endpoint : replaceQueryParameter(endpoint, "category", category);
        if (!hasAnyQueryParameter(request, "country", "sources", "q")) {
            request = addQueryParameterIfMissing(request, "country", country);
        }
        request = addQueryParameterIfMissing(request, "pageSize", "100");
        try {
            return URI.create(request);
        } catch (IllegalArgumentException exception) {
            throw new IOException("News API endpoint is invalid.");
        }
    }

    private boolean hasAnyQueryParameter(String url, String... names) {
        URI uri = URI.create(url);
        if (uri.getRawQuery() == null) return false;
        for (String parameter : uri.getRawQuery().split("&")) {
            String name = parameterName(parameter);
            for (String expected : names) {
                if (name.equalsIgnoreCase(expected)) return true;
            }
        }
        return false;
    }

    private String replaceQueryParameter(String url, String name, String value) {
        String[] fragmentParts = url.split("#", 2);
        String fragment = fragmentParts.length == 2 ? "#" + fragmentParts[1] : "";
        String withoutFragment = fragmentParts[0];
        int queryStart = withoutFragment.indexOf('?');
        String base = queryStart < 0 ? withoutFragment : withoutFragment.substring(0, queryStart);
        List<String> parameters = new ArrayList<>();
        if (queryStart >= 0 && queryStart + 1 < withoutFragment.length()) {
            for (String parameter : withoutFragment.substring(queryStart + 1).split("&")) {
                if (!parameterName(parameter).equalsIgnoreCase(name)) {
                    parameters.add(parameter);
                }
            }
        }
        parameters.add(encode(name) + "=" + encode(value));
        return base + "?" + String.join("&", parameters) + fragment;
    }

    private String addQueryParameterIfMissing(String url, String name, String value) {
        URI uri = URI.create(url);
        if (uri.getRawQuery() != null) {
            for (String parameter : uri.getRawQuery().split("&")) {
                if (parameterName(parameter).equalsIgnoreCase(name)) {
                    return url;
                }
            }
        }
        String separator = uri.getRawQuery() == null ? "?" : "&";
        int fragmentStart = url.indexOf('#');
        String fragment = fragmentStart < 0 ? "" : url.substring(fragmentStart);
        String withoutFragment = fragmentStart < 0 ? url : url.substring(0, fragmentStart);
        return withoutFragment + separator + encode(name) + "=" + encode(value) + fragment;
    }

    private String categoryFromEndpoint() {
        try {
            URI uri = URI.create(endpoint);
            if (uri.getRawQuery() == null) {
                return null;
            }
            for (String parameter : uri.getRawQuery().split("&")) {
                if (parameterName(parameter).equalsIgnoreCase("category")) {
                    String[] pair = parameter.split("=", 2);
                    return pair.length == 2
                            ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT)
                            : null;
                }
            }
        } catch (IllegalArgumentException exception) {
            logger.warn("News API endpoint could not be parsed provider={}", name());
        }
        return null;
    }

    private String parameterName(String parameter) {
        String[] pair = parameter.split("=", 2);
        try {
            return URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return pair[0];
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String effectiveCategory(String requestedCategory) {
        String category = requestedCategory.isBlank() ? categoryFromEndpoint() : requestedCategory;
        return category;
    }

    private static List<String> parseCategories(String configured) {
        if (configured == null || configured.isBlank()) {
            return List.of();
        }
        List<String> categories = Arrays.stream(configured.split(","))
                .map(category -> category.trim().toLowerCase(Locale.ROOT))
                .filter(category -> !category.isBlank())
                .distinct()
                .toList();
        if (categories.isEmpty()) {
            throw new IllegalStateException("NEWS_API_CATEGORIES must contain at least one category when set.");
        }
        for (String category : categories) {
            if (!SUPPORTED_CATEGORIES.contains(category)) {
                throw new IllegalStateException("Unsupported NewsAPI category: " + category);
            }
        }
        return categories;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.isMissingNode() ? null : value.asText();
    }
}
