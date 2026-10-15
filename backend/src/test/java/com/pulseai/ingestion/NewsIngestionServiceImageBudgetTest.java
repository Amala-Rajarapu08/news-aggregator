package com.pulseai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulseai.service.ArticleRetentionService;
import com.pulseai.service.IngestionStatusService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NewsIngestionServiceImageBudgetTest {

    @Test
    void sharesTheMetadataLookupBudgetAcrossProvidersAndRssFeeds() {
        NewsProvider newsApi = provider("news-api", articles("news-api", List.of("newsapi.org"), 10));
        List<String> feedDomains = List.of(
                "nasa.gov", "sec.gov", "nist.gov", "cisa.gov", "pbs.org",
                "espn.com", "techcrunch.com", "bbc.co.uk", "apnews.com");
        NewsProvider rss = provider("rss", articles("rss", feedDomains, feedDomains.size()));

        ArticleDeduplicationService deduplication = mock(ArticleDeduplicationService.class);
        ArticlePersistenceService persistence = mock(ArticlePersistenceService.class);
        ArticleRetentionService retention = mock(ArticleRetentionService.class);
        IngestionStatusService status = mock(IngestionStatusService.class);
        ArticlePageImageResolver imageResolver = mock(ArticlePageImageResolver.class);

        when(deduplication.isDuplicate(any())).thenReturn(false);
        when(persistence.persist(any())).thenReturn(true);
        when(retention.cutoff()).thenReturn(Instant.now().minusSeconds(86_400));
        when(imageResolver.hasCachedResult(any())).thenReturn(false);
        when(imageResolver.resolve(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NewsIngestionService service = new NewsIngestionService(
                List.of(newsApi, rss), new ArticleNormalizer(), deduplication, persistence,
                retention, status, new CategoryMapper(), imageResolver);

        var report = service.ingest();

        ArgumentCaptor<NormalizedArticle> lookedUp = ArgumentCaptor.forClass(NormalizedArticle.class);
        verify(imageResolver, times(19)).resolve(lookedUp.capture());
        List<String> lookedUpDomains = lookedUp.getAllValues().stream()
                .map(NormalizedArticle::sourceDomain)
                .toList();
        assertThat(lookedUpDomains.stream().filter("newsapi.org"::equals)).hasSize(10);
        assertThat(lookedUpDomains).contains("espn.com", "techcrunch.com");
        assertThat(report.inserted()).isEqualTo(19);
    }

    private NewsProvider provider(String name, List<ProviderArticle> articles) {
        NewsProvider provider = mock(NewsProvider.class);
        when(provider.name()).thenReturn(name);
        when(provider.isConfigured()).thenReturn(true);
        when(provider.fetch()).thenReturn(new ProviderFetchResult(articles, articles.size(), 0));
        return provider;
    }

    private List<ProviderArticle> articles(String provider, List<String> domains, int count) {
        List<ProviderArticle> result = new ArrayList<>();
        String publishedAt = Instant.now().minusSeconds(60).toString();
        for (int index = 0; index < count; index++) {
            String domain = domains.get(index % domains.size());
            String url = "https://" + domain + "/story-" + provider + "-" + index;
            result.add(new ProviderArticle(
                    provider + "-" + index, "News report " + index, "Publisher excerpt", url, null,
                    null, publishedAt, domain, "https://" + domain + "/", "Science", "en",
                    provider.equals("rss") ? "science@" + domain : null));
        }
        return result;
    }
}
