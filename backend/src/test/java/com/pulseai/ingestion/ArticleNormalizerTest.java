package com.pulseai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ArticleNormalizerTest {

    private final ArticleNormalizer normalizer = new ArticleNormalizer();

    @Test
    void canonicalizesTrackingParametersFragmentsDefaultPortsAndTrailingSlashes() {
        assertThat(normalizer.normalizeUrl(
                "HTTPS://Example.COM:443/world/story/?utm_source=mail&id=42&fbclid=track#section"))
                .isEqualTo("https://example.com/world/story?id=42");
    }

    @Test
    void preservesNonTrackingQueryParametersAndRejectsUnsafeSchemes() {
        assertThat(normalizer.normalizeUrl("https://example.com/story?edition=us"))
                .isEqualTo("https://example.com/story?edition=us");
        assertThat(normalizer.normalizeUrl("javascript:alert(1)")).isNull();
        assertThat(normalizer.normalizeUrl("https://user:pass@example.com/story")).isNull();
    }

    @Test
    void normalizesProviderMetadataWithoutTrustingMarkupOrInvalidImages() {
        NormalizedArticle article = normalizer.normalize(new ProviderArticle(
                "external-1",
                " <b>Important</b>   reporting ",
                "<p>Source-provided &amp; verified <script>text</script> summary.&#8230;</p>",
                "https://EXAMPLE.com/story/?utm_medium=rss",
                "javascript:alert(1)",
                " Reporter ",
                "Mon, 05 Oct 2026 08:30:00 GMT",
                "Example Journal",
                "https://example.com/news",
                "Tech",
                "EN-us"));

        assertThat(article.title()).isEqualTo("Important reporting");
        assertThat(article.description()).doesNotContain("<", ">", "script", "text", "&#", "&amp;");
        assertThat(article.description()).contains("& verified");
        assertThat(article.url()).isEqualTo("https://EXAMPLE.com/story/?utm_medium=rss");
        assertThat(article.canonicalUrl()).isEqualTo("https://example.com/story");
        assertThat(article.imageUrl()).isNull();
        assertThat(article.sourceDomain()).isEqualTo("example.com");
        assertThat(article.language()).isEqualTo("en-us");
        assertThat(article.publishedAt().toString()).isEqualTo("2026-10-05T08:30:00Z");
    }

    @Test
    void rejectsEntriesWithoutAValidPublicationTimestampInsteadOfInventingOne() {
        ProviderArticle missingTimestamp = new ProviderArticle(
                null, "Headline", null, "https://example.com/story", null, null,
                null, "Example", null, null, "en");
        assertThatThrownBy(() -> normalizer.normalize(missingTimestamp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void preservesPublisherDescriptionsUpToTheDatabaseColumnLimit() {
        String description = "a".repeat(1500);
        NormalizedArticle article = normalizer.normalize(new ProviderArticle(
                null, "Headline", description, "https://example.com/story", null, null,
                "2026-10-13T08:00:00Z", "Example", "https://example.com/", "Technology", "en"));

        assertThat(article.description()).isEqualTo(description);
    }

    @Test
    void normalizesProtocolRelativeAndHtmlEscapedImageUrls() {
        NormalizedArticle protocolRelative = normalizer.normalize(new ProviderArticle(
                null, "Headline", null, "https://example.com/story", "//cdn.example.com/photo.jpg?width=900&amp;crop=1", null,
                "2026-10-13T08:00:00Z", "Example", "https://example.com/", "Business", "en"));

        assertThat(protocolRelative.imageUrl()).isEqualTo("https://cdn.example.com/photo.jpg?width=900&crop=1");
    }

    @Test
    void parsesCommonNorthAmericanRssTimezoneAbbreviations() {
        NormalizedArticle article = normalizer.normalize(new ProviderArticle(
                null, "Headline", null, "https://example.com/story", null, null,
                "Tue, 13 Oct 2026 00:17:38 EST", "Example", "https://example.com/", "Sports", "en"));

        assertThat(article.publishedAt().toString()).isEqualTo("2026-10-13T05:17:38Z");
    }
}
