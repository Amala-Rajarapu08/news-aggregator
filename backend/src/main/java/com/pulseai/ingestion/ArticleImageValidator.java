package com.pulseai.ingestion;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Checks image URLs once, with bounded network and cache limits, before storing recovered metadata. */
@Component
public class ArticleImageValidator {

    private static final Duration VALID_CACHE_TTL = Duration.ofHours(6);
    private static final Duration UNKNOWN_CACHE_TTL = Duration.ofMinutes(2);
    private static final int MAX_CACHE_ENTRIES = 2048;
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_SNIFF_BYTES = 1024;

    private final ProviderHttpClient client;
    private final ArticleNormalizer normalizer;
    private final Map<String, CachedValidation> cache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedValidation> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    public ArticleImageValidator(ProviderHttpClient client, ArticleNormalizer normalizer) {
        this.client = client;
        this.normalizer = normalizer;
    }

    public synchronized ValidationResult validate(String rawUrl) {
        String url = normalizer.normalizeUrl(rawUrl);
        if (url == null) return ValidationResult.INVALID;
        CachedValidation cached = cached(url);
        if (cached != null) return cached.result();

        ValidationResult result = fetchAndValidate(URI.create(url));
        cache(url, result);
        return result;
    }

    public boolean hasCachedResult(String rawUrl) {
        String url = normalizer.normalizeUrl(rawUrl);
        return url != null && cached(url) != null;
    }

    private ValidationResult fetchAndValidate(URI uri) {
        try {
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                if (!isPublicHttpUri(uri)) return ValidationResult.INVALID;
                ProviderHttpResponse response = client.fetchOnceWithStatus(
                        uri, "image/avif,image/webp,image/apng,image/svg+xml,image/*;q=0.9,*/*;q=0.5");
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return isImageBody(response.contentType(), response.body())
                            ? ValidationResult.VALID : ValidationResult.INVALID;
                }
                if (response.statusCode() >= 300 && response.statusCode() < 400
                        && response.redirectLocation() != null && redirect < MAX_REDIRECTS) {
                    uri = uri.resolve(response.redirectLocation());
                    continue;
                }
                if (response.statusCode() == 400 || response.statusCode() == 401
                        || response.statusCode() == 403 || response.statusCode() == 404
                        || response.statusCode() == 410 || response.statusCode() == 415) {
                    return ValidationResult.INVALID;
                }
                return ValidationResult.UNAVAILABLE;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | IllegalArgumentException exception) {
            // A temporary network or publisher failure is not proof that a stored URL is invalid.
        }
        return ValidationResult.UNAVAILABLE;
    }

    private boolean isPublicHttpUri(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal") || host.endsWith(".test") || host.endsWith(".example")
                || host.endsWith(".invalid")) return false;
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) return false;
            for (InetAddress address : addresses) {
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
            boolean globalUnicast = first >= 0x20 && first <= 0x3f;
            boolean documentation = first == 0x20 && second == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8;
            return globalUnicast && !documentation;
        }
        return false;
    }

    private boolean isImageBody(String rawContentType, byte[] body) {
        if (rawContentType == null || body == null || body.length == 0) return false;
        String contentType = rawContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) return false;
        if (startsWith(body, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return true;
        if (startsWith(body, new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) return true;
        if (asciiPrefix(body, "GIF87a") || asciiPrefix(body, "GIF89a")) return true;
        if (body.length >= 12 && asciiPrefix(body, "RIFF") && asciiAt(body, 8, "WEBP")) return true;
        if (body.length >= 12 && asciiAt(body, 4, "ftyp")
                && (asciiAt(body, 8, "avif") || asciiAt(body, 8, "avis"))) return true;
        if (asciiPrefix(body, "BM")) return true;
        if ("image/svg+xml".equals(contentType)) return isSvg(body);
        return false;
    }

    private boolean isSvg(byte[] body) {
        int length = Math.min(body.length, MAX_SNIFF_BYTES);
        String prefix = new String(body, 0, length, StandardCharsets.UTF_8)
                .replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
        if (prefix.startsWith("<!doctype html") || prefix.startsWith("<html")
                || prefix.startsWith("<script")) return false;
        return prefix.startsWith("<svg") || prefix.startsWith("<?xml") && prefix.contains("<svg");
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) return false;
        }
        return true;
    }

    private boolean asciiPrefix(byte[] value, String prefix) {
        return asciiAt(value, 0, prefix);
    }

    private boolean asciiAt(byte[] value, int offset, String text) {
        if (offset < 0 || value.length < offset + text.length()) return false;
        for (int index = 0; index < text.length(); index++) {
            if (Character.toUpperCase((char) Byte.toUnsignedInt(value[offset + index]))
                    != Character.toUpperCase(text.charAt(index))) return false;
        }
        return true;
    }

    private synchronized CachedValidation cached(String url) {
        CachedValidation cached = cache.get(url);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached;
        cache.remove(url);
        return null;
    }

    private synchronized void cache(String url, ValidationResult result) {
        Duration ttl = result == ValidationResult.UNAVAILABLE ? UNKNOWN_CACHE_TTL : VALID_CACHE_TTL;
        cache.put(url, new CachedValidation(result, Instant.now().plus(ttl)));
    }

    private record CachedValidation(ValidationResult result, Instant expiresAt) {}

    public enum ValidationResult {
        VALID,
        INVALID,
        UNAVAILABLE
    }
}
