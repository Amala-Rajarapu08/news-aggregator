package com.pulseai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GNewsProviderTest {

    private static final String ARTICLE = """
            {"articles":[{"id":"story-1","title":"New science report","description":"Publisher excerpt",
            "url":"https://publisher.example/story-1","image":"https://publisher.example/photo.jpg",
            "publishedAt":"2026-10-12T08:00:00Z","lang":"en",
            "source":{"name":"Example News","url":"https://publisher.example"}}]}
            """;

    @Test
    void requestsConfiguredCategoriesAndCarriesTheirClassificationAndImages() throws Exception {
        ProviderHttpClient http = mock(ProviderHttpClient.class);
        when(http.fetch(any(URI.class), eq("application/json"), eq("secret")))
                .thenReturn(ARTICLE.getBytes(StandardCharsets.UTF_8));
        var provider = new GNewsProvider(http, new ObjectMapper(), "secret", "general", "science, business, science");

        ProviderFetchResult result = provider.fetch();

        assertThat(provider.isConfigured()).isTrue();
        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.failures()).isZero();
        assertThat(result.articles()).extracting(ProviderArticle::category).containsExactly("science", "business");
        assertThat(result.articles()).allSatisfy(article -> {
            assertThat(article.externalId()).isEqualTo("story-1");
            assertThat(article.imageUrl()).isEqualTo("https://publisher.example/photo.jpg");
        });
        verify(http).fetch(URI.create("https://gnews.io/api/v4/top-headlines?lang=en&max=10&category=science"), "application/json", "secret");
        verify(http).fetch(URI.create("https://gnews.io/api/v4/top-headlines?lang=en&max=10&category=business"), "application/json", "secret");
    }

    @Test
    void recordsPartialCategoryFailuresAndContinuesTheRemainingRequests() throws Exception {
        ProviderHttpClient http = mock(ProviderHttpClient.class);
        when(http.fetch(URI.create("https://gnews.io/api/v4/top-headlines?lang=en&max=10&category=world"), "application/json", "secret"))
                .thenThrow(new IOException("provider unavailable"));
        when(http.fetch(URI.create("https://gnews.io/api/v4/top-headlines?lang=en&max=10&category=health"), "application/json", "secret"))
                .thenReturn(ARTICLE.getBytes(StandardCharsets.UTF_8));
        var provider = new GNewsProvider(http, new ObjectMapper(), "secret", "general", "world,health");

        ProviderFetchResult result = provider.fetch();

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.failures()).isEqualTo(1);
        assertThat(result.articles()).singleElement().satisfies(article -> assertThat(article.category()).isEqualTo("health"));
    }

    @Test
    void recordsAProviderWideQuotaFailureOnceAndSkipsTheRemainingCategories() throws Exception {
        ProviderHttpClient http = mock(ProviderHttpClient.class);
        URI world = URI.create("https://gnews.io/api/v4/top-headlines?lang=en&max=10&category=world");
        when(http.fetch(world, "application/json", "secret"))
                .thenThrow(ProviderRequestException.httpStatus(403));
        var provider = new GNewsProvider(http, new ObjectMapper(), "secret", "general", "world,business,technology");

        ProviderFetchResult result = provider.fetch();

        assertThat(result.failures()).isEqualTo(1);
        assertThat(result.articles()).isEmpty();
        verify(http).fetch(world, "application/json", "secret");
        verifyNoMoreInteractions(http);
    }

    @Test
    void remainsUnconfiguredWithoutAnApiKeyAndRejectsUnsupportedCategories() {
        var noKey = new GNewsProvider(mock(ProviderHttpClient.class), new ObjectMapper(), "", "general", "");
        assertThat(noKey.isConfigured()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new GNewsProvider(mock(ProviderHttpClient.class), new ObjectMapper(), "secret", "general", "politics"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported GNews category");
    }

    @Test
    void sendsGNewsKeysInTheDocumentedAuthenticationHeader() throws Exception {
        ProviderHttpClient http = mock(ProviderHttpClient.class);
        when(http.fetch(any(URI.class), eq("application/json"), eq("secret")))
                .thenReturn(ARTICLE.getBytes(StandardCharsets.UTF_8));
        var provider = new GNewsProvider(http, new ObjectMapper(), "secret", "general", "world");

        provider.fetch();

        verify(http).fetch(URI.create("https://gnews.io/api/v4/top-headlines?lang=en&max=10&category=world"), "application/json", "secret");
    }
}
