package com.pulseai.ingestion;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ArticleNormalizer {

    private static final DateTimeFormatter RSS_NORTH_AMERICAN_LOCAL_TIME = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
    private static final Map<String, ZoneOffset> RSS_TIMEZONE_OFFSETS = Map.ofEntries(
            Map.entry("EST", ZoneOffset.ofHours(-5)), Map.entry("EDT", ZoneOffset.ofHours(-4)),
            Map.entry("CST", ZoneOffset.ofHours(-6)), Map.entry("CDT", ZoneOffset.ofHours(-5)),
            Map.entry("MST", ZoneOffset.ofHours(-7)), Map.entry("MDT", ZoneOffset.ofHours(-6)),
            Map.entry("PST", ZoneOffset.ofHours(-8)), Map.entry("PDT", ZoneOffset.ofHours(-7)),
            Map.entry("AKST", ZoneOffset.ofHours(-9)), Map.entry("AKDT", ZoneOffset.ofHours(-8)),
            Map.entry("HST", ZoneOffset.ofHours(-10)));

    private static final List<String> TRACKING_KEYS = List.of(
            "fbclid", "gclid", "dclid", "mc_cid", "mc_eid", "ref_src", "ref_url");

    public NormalizedArticle normalize(ProviderArticle providerArticle) {
        String title = clean(providerArticle.title(), 500);
        if (title == null) {
            throw new IllegalArgumentException("Article title is missing.");
        }

        String originalUrl = preserveOriginalUrl(providerArticle.url());
        String canonicalUrl = normalizeUrl(originalUrl);
        if (canonicalUrl == null) {
            throw new IllegalArgumentException("Article URL is missing or invalid.");
        }
        Instant publishedAt = parsePublishedAt(providerArticle.publishedAt());

        URI articleUri = URI.create(canonicalUrl);
        String articleHost = normalizeHost(articleUri.getHost());
        String sourceHomeUrl = normalizedHomeUrl(providerArticle.sourceHomeUrl(), articleUri);
        URI sourceHomeUri = URI.create(sourceHomeUrl);
        String sourceDomain = normalizeHost(sourceHomeUri.getHost());
        String sourceName = clean(providerArticle.sourceName(), 120);
        if (sourceName == null) {
            sourceName = sourceDomain;
        }

        return new NormalizedArticle(
                clean(providerArticle.externalId(), 500),
                title,
                clean(providerArticle.description(), 2000),
                originalUrl,
                canonicalUrl,
                normalizeOptionalUrl(providerArticle.imageUrl()),
                clean(providerArticle.author(), 200),
                publishedAt,
                sourceName,
                sourceDomain == null ? articleHost : sourceDomain,
                sourceHomeUrl,
                clean(providerArticle.category(), 120),
                normalizeLanguage(providerArticle.language()));
    }

    public String normalizeUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI input = new URI(value.trim()).normalize();
            String scheme = input.getScheme() == null ? "" : input.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("http") && !scheme.equals("https"))
                    || input.getHost() == null
                    || input.getUserInfo() != null) {
                return null;
            }

            String host = normalizeHost(input.getHost());
            int port = input.getPort();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
                port = -1;
            }
            String path = input.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            while (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String query = removeTrackingParameters(input.getRawQuery());
            String authorityHost = host.contains(":") ? "[" + host + "]" : host;
            String normalized = scheme + "://" + authorityHost + (port < 0 ? "" : ":" + port) + path
                    + (query == null || query.isEmpty() ? "" : "?" + query);
            if (normalized.length() > 2048) {
                return null;
            }
            return new URI(normalized).toASCIIString();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizeOptionalUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String candidate = org.springframework.web.util.HtmlUtils.htmlUnescape(value.trim());
        if (candidate.startsWith("//")) candidate = "https:" + candidate;
        return normalizeUrl(candidate);
    }

    private String preserveOriginalUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String original = value.trim();
        if (original.length() > 2048) return null;
        try {
            URI uri = new URI(original);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("http") && !scheme.equals("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null) return null;
            return original;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private String normalizedHomeUrl(String value, URI articleUri) {
        String normalized = normalizeUrl(value);
        if (normalized == null) {
            String host = normalizeHost(articleUri.getHost());
            return articleUri.getScheme().toLowerCase(Locale.ROOT) + "://" + host + "/";
        }
        URI uri = URI.create(normalized);
        String host = uri.getHost();
        if (host == null) {
            return articleUri.getScheme().toLowerCase(Locale.ROOT) + "://" + normalizeHost(articleUri.getHost()) + "/";
        }
        String authority = host.contains(":") ? "[" + host + "]" : host;
        return uri.getScheme() + "://" + authority + (uri.getPort() < 0 ? "" : ":" + uri.getPort()) + "/";
    }

    private String removeTrackingParameters(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return rawQuery;
        }
        List<String> retained = new ArrayList<>();
        for (String parameter : rawQuery.split("&")) {
            String key = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
            if (!key.startsWith("utm_") && !TRACKING_KEYS.contains(key)) {
                retained.add(parameter);
            }
        }
        return String.join("&", retained);
    }

    private String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.contains(":") ? lower : IDN.toASCII(lower);
    }

    private String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = org.springframework.web.util.HtmlUtils.htmlUnescape(value)
                .replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", " ")
                .replaceAll("(?is)<[^>]*>", " ")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength).trim();
    }

    private String normalizeLanguage(String language) {
        String cleaned = clean(language, 12);
        if (cleaned == null) {
            return "und";
        }
        return cleaned.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private Instant parsePublishedAt(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Publication timestamp is missing.");
        }
        String timestamp = value.trim();
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(timestamp).toInstant();
            } catch (DateTimeParseException ignoredOffset) {
                try {
                    return ZonedDateTime.parse(timestamp, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                } catch (DateTimeParseException ignoredRfc822) {
                    try {
                        Instant northAmericanTimestamp = parseNorthAmericanRssTimestamp(timestamp);
                        if (northAmericanTimestamp != null) return northAmericanTimestamp;
                        throw new DateTimeParseException("Unsupported timezone abbreviation", timestamp, 0);
                    } catch (DateTimeParseException exception) {
                        throw new IllegalArgumentException("Publication timestamp is invalid.", exception);
                    }
                }
            }
        }
    }

    private Instant parseNorthAmericanRssTimestamp(String timestamp) {
        int zoneSeparator = timestamp.lastIndexOf(' ');
        if (zoneSeparator < 0) return null;
        ZoneOffset offset = RSS_TIMEZONE_OFFSETS.get(timestamp.substring(zoneSeparator + 1).toUpperCase(Locale.ROOT));
        if (offset == null) return null;
        LocalDateTime localDateTime = LocalDateTime.parse(
                timestamp.substring(0, zoneSeparator), RSS_NORTH_AMERICAN_LOCAL_TIME);
        return localDateTime.toInstant(offset);
    }
}
