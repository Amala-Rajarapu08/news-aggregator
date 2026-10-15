package com.pulseai.ingestion;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Resolves publisher image metadata during ingestion, never during article page requests. */
@Component
public class ArticlePageImageResolver {

    private static final Logger logger = LoggerFactory.getLogger(ArticlePageImageResolver.class);
    private static final Pattern META_TAG = Pattern.compile("(?is)<meta\\b[^>]*>");
    private static final Pattern LINK_TAG = Pattern.compile("(?is)<link\\b[^>]*>");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?is)([a-z_:][-a-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final int MAX_REDIRECTS = 3;

    private final ProviderHttpClient client;
    private final ArticleNormalizer normalizer;
    private final ArticleImageValidator imageValidator;
    private final boolean enabled;
    private final Map<String, CachedImage> metadataCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedImage> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    public ArticlePageImageResolver(
            ProviderHttpClient client,
            ArticleNormalizer normalizer,
            ArticleImageValidator imageValidator,
            @Value("${app.news.image-metadata-enabled:true}") boolean enabled) {
        this.client = client;
        this.normalizer = normalizer;
        this.imageValidator = imageValidator;
        this.enabled = enabled;
    }

    public synchronized NormalizedArticle resolve(NormalizedArticle article) {
        if (!enabled || article.imageUrl() != null) return article;
        String cacheKey = article.url() + "|" + article.sourceDomain();
        CachedImage cached = cachedImage(cacheKey);
        if (cached != null) {
            return cached.imageUrl() == null ? article : article.withImageUrl(cached.imageUrl());
        }
        URI page;
        try {
            page = URI.create(article.url());
        } catch (RuntimeException exception) {
            return article;
        }
        if (!isPublisherPage(page, article.sourceDomain()) || !isPublicHttpHost(page)) return article;

        try {
            URI publisherPage = page;
            ProviderHttpResponse response = null;
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                if (!isPublisherPage(publisherPage, article.sourceDomain()) || !isPublicHttpHost(publisherPage)) {
                    cacheImage(cacheKey, null);
                    return article;
                }
                response = client.fetchOnceWithStatus(
                        publisherPage, "text/html,application/xhtml+xml;q=0.9");
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    break;
                }
                if (response.statusCode() < 300 || response.statusCode() >= 400
                        || response.redirectLocation() == null || redirect == MAX_REDIRECTS) {
                    cacheImage(cacheKey, null);
                    return article;
                }
                publisherPage = publisherPage.resolve(response.redirectLocation());
            }
            if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
                cacheImage(cacheKey, null);
                return article;
            }
            String html = new String(response.body(), StandardCharsets.UTF_8);
            String rawImage = extractImage(html);
            if (rawImage == null) {
                cacheImage(cacheKey, null);
                return article;
            }
            URI image = publisherPage.resolve(HtmlUtils.htmlUnescape(rawImage.trim()));
            String safeImageUrl = normalizer.normalizeUrl(image.toASCIIString());
            if (imageValidator.validate(safeImageUrl) != ArticleImageValidator.ValidationResult.VALID) {
                cacheImage(cacheKey, null);
                return article;
            }
            cacheImage(cacheKey, safeImageUrl);
            return safeImageUrl == null ? article : article.withImageUrl(safeImageUrl);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return article;
        } catch (IOException | IllegalArgumentException exception) {
            cacheImage(cacheKey, null);
            logger.debug("Publisher image metadata lookup failed exceptionType={}", exception.getClass().getSimpleName());
            return article;
        }
    }

    public boolean hasCachedResult(NormalizedArticle article) {
        return cachedImage(article.url() + "|" + article.sourceDomain()) != null;
    }

    private synchronized CachedImage cachedImage(String key) {
        CachedImage cached = metadataCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached;
        metadataCache.remove(key);
        return null;
    }

    private synchronized void cacheImage(String key, String imageUrl) {
        metadataCache.put(key, new CachedImage(imageUrl, Instant.now().plus(CACHE_TTL)));
    }

    private record CachedImage(String imageUrl, Instant expiresAt) {}

    private String extractImage(String html) {
        Map<String, String> values = new HashMap<>();
        Matcher tags = META_TAG.matcher(html);
        while (tags.find()) {
            Map<String, String> attributes = attributes(tags.group());
            String key = attributes.getOrDefault("property", attributes.getOrDefault("name", ""))
                    .toLowerCase(Locale.ROOT);
            String content = attributes.get("content");
            if (content != null && !content.isBlank()) values.putIfAbsent(key, content);
        }
        for (String key : List.of("og:image", "og:image:secure_url", "og:image:url", "twitter:image", "twitter:image:src")) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) return value;
        }

        tags = LINK_TAG.matcher(html);
        while (tags.find()) {
            Map<String, String> attributes = attributes(tags.group());
            if ("image_src".equalsIgnoreCase(attributes.get("rel"))) {
                String href = attributes.get("href");
                if (href != null && !href.isBlank()) return href;
            }
        }
        return null;
    }

    private Map<String, String> attributes(String tag) {
        Map<String, String> values = new HashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(tag);
        while (matcher.find()) {
            String value = matcher.group(2) != null ? matcher.group(2)
                    : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            values.putIfAbsent(matcher.group(1).toLowerCase(Locale.ROOT), value);
        }
        return values;
    }

    private boolean isPublisherPage(URI page, String sourceDomain) {
        if (page.getHost() == null || sourceDomain == null || sourceDomain.isBlank()
                || page.getUserInfo() != null || !isHttpScheme(page.getScheme())) return false;
        if (page.getPort() != -1 && page.getPort() != 80 && page.getPort() != 443) return false;
        String pageHost = page.getHost().toLowerCase(Locale.ROOT);
        String sourceHost = sourceDomain.toLowerCase(Locale.ROOT);
        return pageHost.equals(sourceHost) || pageHost.endsWith("." + sourceHost);
    }

    private boolean isPublicHttpHost(URI page) {
        String host = page.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal") || host.endsWith(".test") || host.endsWith(".example")
                || host.endsWith(".invalid")) return false;
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!isPublicAddress(address)) return false;
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && (second == 0 || second == 88 && third == 99 || second == 168))
                    && !(first == 198 && (second == 18 || second == 19 || second == 51 && third == 100))
                    && !(first == 203 && second == 0 && third == 113);
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            boolean uniqueLocal = (first & 0xfe) == 0xfc;
            boolean documentation = first == 0x20 && second == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8;
            return !uniqueLocal && !documentation;
        }
        return false;
    }

    private boolean isHttpScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }
}
