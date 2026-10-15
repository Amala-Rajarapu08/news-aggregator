package com.pulseai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class ArticlePageImageResolverTest {

    private final ProviderHttpClient client = org.mockito.Mockito.mock(ProviderHttpClient.class);
    private final ArticleImageValidator imageValidator = org.mockito.Mockito.mock(ArticleImageValidator.class);
    private final ArticlePageImageResolver resolver = new ArticlePageImageResolver(
            client, new ArticleNormalizer(), imageValidator, true);

    @Test
    void resolvesOpenGraphImageOnlyWhenProviderHasNoImage() throws Exception {
        when(client.fetchOnceWithStatus(URI.create("https://93.184.216.34/story"), "text/html,application/xhtml+xml;q=0.9"))
                .thenReturn(html("<meta content='//cdn.publisher.example/story.jpg?width=1200' property='og:image'>"));
        when(imageValidator.validate("https://cdn.publisher.example/story.jpg?width=1200"))
                .thenReturn(ArticleImageValidator.ValidationResult.VALID);

        NormalizedArticle resolved = resolver.resolve(article("93.184.216.34", null));

        assertThat(resolved.imageUrl()).isEqualTo("https://cdn.publisher.example/story.jpg?width=1200");
        verify(client).fetchOnceWithStatus(URI.create("https://93.184.216.34/story"), "text/html,application/xhtml+xml;q=0.9");
    }

    @Test
    void resolvesTwitterCardImageAndUnescapesPublisherMetadata() throws Exception {
        URI page = URI.create("https://93.184.216.37/story");
        when(client.fetchOnceWithStatus(page, "text/html,application/xhtml+xml;q=0.9"))
                .thenReturn(html("<meta content='/assets/card.jpg?size=1200&amp;crop=1' name='twitter:image'>"));
        when(imageValidator.validate("https://93.184.216.37/assets/card.jpg?size=1200&crop=1"))
                .thenReturn(ArticleImageValidator.ValidationResult.VALID);

        assertThat(resolver.resolve(article("93.184.216.37", null)).imageUrl())
                .isEqualTo("https://93.184.216.37/assets/card.jpg?size=1200&crop=1");
    }

    @Test
    void leavesValidProviderImageUntouchedAndDoesNotFetchMetadata() {
        NormalizedArticle original = article("93.184.216.34", "https://images.publisher.example/provider.jpg");

        assertThat(resolver.resolve(original)).isSameAs(original);
        verifyNoInteractions(client);
    }

    @Test
    void cachesResolvedMetadataSoRepeatedFeedItemsDoNotRefetchThePublisherPage() throws Exception {
        URI page = URI.create("https://93.184.216.35/story");
        when(client.fetchOnceWithStatus(page, "text/html,application/xhtml+xml;q=0.9"))
                .thenReturn(html("<meta property='og:image' content='/media/story.jpg'>"));
        when(imageValidator.validate("https://93.184.216.35/media/story.jpg"))
                .thenReturn(ArticleImageValidator.ValidationResult.VALID);
        NormalizedArticle candidate = article("93.184.216.35", null);

        assertThat(resolver.resolve(candidate).imageUrl()).isEqualTo("https://93.184.216.35/media/story.jpg");
        assertThat(resolver.resolve(candidate).imageUrl()).isEqualTo("https://93.184.216.35/media/story.jpg");

        verify(client, times(1)).fetchOnceWithStatus(page, "text/html,application/xhtml+xml;q=0.9");
    }

    @Test
    void cachesMissingImageMetadataToAvoidRepeatingUnsuccessfulPublisherRequests() throws Exception {
        URI page = URI.create("https://93.184.216.36/story");
        when(client.fetchOnceWithStatus(page, "text/html,application/xhtml+xml;q=0.9"))
                .thenReturn(html("<html><head><title>Publisher page</title></head></html>"));
        NormalizedArticle candidate = article("93.184.216.36", null);

        assertThat(resolver.resolve(candidate).imageUrl()).isNull();
        assertThat(resolver.resolve(candidate).imageUrl()).isNull();

        verify(client, times(1)).fetchOnceWithStatus(page, "text/html,application/xhtml+xml;q=0.9");
    }

    @Test
    void followsBoundedSamePublisherRedirectsBeforeResolvingRelativeMetadata() throws Exception {
        URI original = URI.create("https://93.184.216.38/story");
        URI redirected = URI.create("https://93.184.216.38/news/story");
        when(client.fetchOnceWithStatus(original, "text/html,application/xhtml+xml;q=0.9"))
                .thenReturn(new ProviderHttpResponse(302, new byte[0], URI.create("/news/story")));
        when(client.fetchOnceWithStatus(redirected, "text/html,application/xhtml+xml;q=0.9"))
                .thenReturn(html("<meta property='og:image' content='../images/story.jpg'>"));
        when(imageValidator.validate("https://93.184.216.38/images/story.jpg"))
                .thenReturn(ArticleImageValidator.ValidationResult.VALID);

        NormalizedArticle resolved = resolver.resolve(article("93.184.216.38", null));

        assertThat(resolved.imageUrl()).isEqualTo("https://93.184.216.38/images/story.jpg");
        verify(client).fetchOnceWithStatus(original, "text/html,application/xhtml+xml;q=0.9");
        verify(client).fetchOnceWithStatus(redirected, "text/html,application/xhtml+xml;q=0.9");
    }

    @Test
    void refusesRedirectsOutsideThePublicPublisherHost() throws Exception {
        URI page = URI.create("https://93.184.216.39/story");
        when(client.fetchOnceWithStatus(page, "text/html,application/xhtml+xml;q=0.9"))
                .thenReturn(new ProviderHttpResponse(302, new byte[0], URI.create("http://127.0.0.1/private")));

        assertThat(resolver.resolve(article("93.184.216.39", null)).imageUrl()).isNull();

        verify(client, times(1)).fetchOnceWithStatus(page, "text/html,application/xhtml+xml;q=0.9");
    }

    @Test
    void rejectsPublisherMetadataThatDoesNotResolveToAValidImageResponse() throws Exception {
        URI page = URI.create("https://93.184.216.40/story");
        when(client.fetchOnceWithStatus(page, "text/html,application/xhtml+xml;q=0.9"))
                .thenReturn(html("<meta property='og:image' content='/assets/expired.jpg'>"));
        when(imageValidator.validate("https://93.184.216.40/assets/expired.jpg"))
                .thenReturn(ArticleImageValidator.ValidationResult.INVALID);

        assertThat(resolver.resolve(article("93.184.216.40", null)).imageUrl()).isNull();
    }

    @Test
    void doesNotFetchPagesOutsideThePublisherOrOnPrivateHosts() {
        assertThat(resolver.resolve(article("127.0.0.1", null)).imageUrl()).isNull();
        assertThat(resolver.resolve(article("93.184.216.34", "other-publisher.example", null)).imageUrl()).isNull();
        verifyNoInteractions(client);
    }

    private NormalizedArticle article(String host, String imageUrl) {
        return article(host, host, imageUrl);
    }

    private NormalizedArticle article(String host, String sourceDomain, String imageUrl) {
        return new NormalizedArticle(null, "Publisher headline", "Excerpt", "https://" + host + "/story",
                "https://" + host + "/story", imageUrl, null, Instant.parse("2026-10-13T08:00:00Z"),
                "Publisher", sourceDomain, "https://" + sourceDomain + "/", "Business", "en");
    }

    private ProviderHttpResponse html(String value) {
        return new ProviderHttpResponse(200, value.getBytes(StandardCharsets.UTF_8));
    }
}
