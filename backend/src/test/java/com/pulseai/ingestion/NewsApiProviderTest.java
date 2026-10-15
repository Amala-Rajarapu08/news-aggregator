package com.pulseai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NewsApiProviderTest {

    private static final String RESPONSE = """
            {"articles":[{"title":"Technology story","description":"Publisher summary",
            "url":"https://publisher.example/story","urlToImage":"https://publisher.example/photo.jpg",
            "author":"Reporter","publishedAt":"2026-10-12T08:00:00Z",
            "source":{"id":"example","name":"Example News"}}]}
            """;

    @Test
    void requestsConfiguredCategoriesWithHeaderAuthenticationAndPreservesImages() throws Exception {
        ProviderHttpClient http = mock(ProviderHttpClient.class);
        when(http.fetch(any(URI.class), eq("application/json"), eq("secret")))
                .thenReturn(RESPONSE.getBytes(StandardCharsets.UTF_8));
        var provider = new NewsApiProvider(
                new ObjectMapper(), http, "secret", "https://newsapi.org/v2/top-headlines?country=in", "technology,business", "us");

        ProviderFetchResult result = provider.fetch();

        assertThat(provider.isConfigured()).isTrue();
        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.failures()).isZero();
        assertThat(result.articles()).extracting(ProviderArticle::category).containsExactly("technology", "business");
        assertThat(result.articles()).allSatisfy(article -> {
            assertThat(article.imageUrl()).isEqualTo("https://publisher.example/photo.jpg");
            assertThat(article.author()).isEqualTo("Reporter");
        });
        verify(http).fetch(URI.create("https://newsapi.org/v2/top-headlines?country=in&category=technology&pageSize=100"), "application/json", "secret");
        verify(http).fetch(URI.create("https://newsapi.org/v2/top-headlines?country=in&category=business&pageSize=100"), "application/json", "secret");
    }

    @Test
    void usesCategoryFromAnExistingEndpointWhenNoCategoryListIsConfigured() throws Exception {
        ProviderHttpClient http = mock(ProviderHttpClient.class);
        when(http.fetch(any(URI.class), eq("application/json"), eq("secret")))
                .thenReturn(RESPONSE.getBytes(StandardCharsets.UTF_8));
        var provider = new NewsApiProvider(
                new ObjectMapper(), http, "secret", "https://newsapi.org/v2/top-headlines?country=in&category=health", "", "us");

        ProviderFetchResult result = provider.fetch();

        assertThat(result.failures()).isZero();
        assertThat(result.articles()).singleElement().satisfies(article -> assertThat(article.category()).isEqualTo("health"));
        verify(http).fetch(URI.create("https://newsapi.org/v2/top-headlines?country=in&category=health&pageSize=100"), "application/json", "secret");
    }

    @Test
    void preservesGeneralCategoryForCatalogMapping() throws Exception {
        ProviderHttpClient http = mock(ProviderHttpClient.class);
        when(http.fetch(any(URI.class), eq("application/json"), eq("secret")))
                .thenReturn(RESPONSE.getBytes(StandardCharsets.UTF_8));
        var provider = new NewsApiProvider(
                new ObjectMapper(), http, "secret", "", "general", "us");

        ProviderFetchResult result = provider.fetch();

        assertThat(result.failures()).isZero();
        assertThat(result.articles()).singleElement().satisfies(article -> assertThat(article.category()).isEqualTo("general"));
        verify(http).fetch(URI.create("https://newsapi.org/v2/top-headlines?category=general&country=us&pageSize=100"), "application/json", "secret");
    }

    @Test
    void usesOfficialEndpointWhenOnlyAnApiKeyIsConfigured() throws Exception {
        ProviderHttpClient http = mock(ProviderHttpClient.class);
        when(http.fetch(any(URI.class), eq("application/json"), eq("secret")))
                .thenReturn(RESPONSE.getBytes(StandardCharsets.UTF_8));
        var provider = new NewsApiProvider(new ObjectMapper(), http, "secret", "", "business", "us");

        ProviderFetchResult result = provider.fetch();

        assertThat(provider.isConfigured()).isTrue();
        assertThat(result.failures()).isZero();
        verify(http).fetch(URI.create("https://newsapi.org/v2/top-headlines?category=business&country=us&pageSize=100"), "application/json", "secret");
    }

    @Test
    void recordsAProviderWideAuthenticationFailureOnceAndSkipsTheRemainingCategories() throws Exception {
        ProviderHttpClient http = mock(ProviderHttpClient.class);
        URI business = URI.create("https://newsapi.org/v2/top-headlines?country=us&category=business&pageSize=100");
        org.mockito.Mockito.when(http.fetch(business, "application/json", "secret"))
                .thenThrow(ProviderRequestException.httpStatus(401));
        var provider = new NewsApiProvider(
                new ObjectMapper(), http, "secret", "https://newsapi.org/v2/top-headlines?country=us", "business,technology,health", "us");

        ProviderFetchResult result = provider.fetch();

        assertThat(result.failures()).isEqualTo(1);
        assertThat(result.articles()).isEmpty();
        verify(http).fetch(business, "application/json", "secret");
        verifyNoMoreInteractions(http);
    }
}
