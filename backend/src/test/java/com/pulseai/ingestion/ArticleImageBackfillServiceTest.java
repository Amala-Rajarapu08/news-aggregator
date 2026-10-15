package com.pulseai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pulseai.entity.Article;
import com.pulseai.entity.Source;
import com.pulseai.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

class ArticleImageBackfillServiceTest {

    @Test
    void backfillsOnlyAConfiguredBoundAndContinuesFromItsCursor() {
        ArticleRepository repository = mock(ArticleRepository.class);
        ArticlePageImageResolver resolver = mock(ArticlePageImageResolver.class);
        ArticleImageValidator validator = mock(ArticleImageValidator.class);
        ArticlePersistenceService persistence = mock(ArticlePersistenceService.class);
        var service = new ArticleImageBackfillService(repository, resolver, validator, persistence, 6, 8, 32);
        List<Article> firstBatch = java.util.stream.IntStream.range(1, 9)
                .mapToObj(index -> article(new UUID(0, index)))
                .toList();
        when(repository.findImageAuditCandidates(any())).thenReturn(new SliceImpl<>(firstBatch, PageRequest.of(0, 32), false));
        when(resolver.hasCachedResult(any())).thenReturn(false);
        when(resolver.resolve(any())).thenAnswer(invocation ->
                invocation.<NormalizedArticle>getArgument(0).withImageUrl("https://images.publisher.example/photo.jpg"));
        when(persistence.backfillImageByIdIfMissing(any(), any(), any())).thenReturn(true);

        var result = service.backfillBatch();

        assertThat(result.scanned()).isEqualTo(6);
        assertThat(result.lookups()).isEqualTo(6);
        assertThat(result.enriched()).isEqualTo(6);
        verify(persistence, times(6)).backfillImageByIdIfMissing(any(), eq("https://images.publisher.example/photo.jpg"), any());

        var resumed = service.backfillBatch();

        assertThat(resumed.scanned()).isEqualTo(2);
        assertThat(resumed.lookups()).isEqualTo(2);
        verify(repository, times(2)).findImageAuditCandidates(any());
    }

    @Test
    void skipsRecentNegativeCacheResultsAndDoesNotUpdateNullImages() {
        ArticleRepository repository = mock(ArticleRepository.class);
        ArticlePageImageResolver resolver = mock(ArticlePageImageResolver.class);
        ArticleImageValidator validator = mock(ArticleImageValidator.class);
        ArticlePersistenceService persistence = mock(ArticlePersistenceService.class);
        var service = new ArticleImageBackfillService(repository, resolver, validator, persistence, 6, 8, 32);
        Article candidate = article(new UUID(0, 1));
        when(repository.findImageAuditCandidates(any()))
                .thenReturn(new SliceImpl<>(List.of(candidate), PageRequest.of(0, 32), false));
        when(resolver.hasCachedResult(any())).thenReturn(true);

        var result = service.backfillBatch();

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.lookups()).isZero();
        assertThat(result.enriched()).isZero();
        verify(persistence, times(0)).backfillImageByIdIfMissing(any(), any(), any());
    }

    @Test
    void skipsArticlesCheckedWithinTheDurableSixHourWindow() {
        ArticleRepository repository = mock(ArticleRepository.class);
        ArticlePageImageResolver resolver = mock(ArticlePageImageResolver.class);
        ArticleImageValidator validator = mock(ArticleImageValidator.class);
        ArticlePersistenceService persistence = mock(ArticlePersistenceService.class);
        var service = new ArticleImageBackfillService(repository, resolver, validator, persistence, 6, 8, 32);
        Article recentlyChecked = article(new UUID(0, 2));
        when(recentlyChecked.getImageCheckedAt()).thenReturn(Instant.now());
        when(repository.findImageAuditCandidates(any()))
                .thenReturn(new SliceImpl<>(List.of(recentlyChecked), PageRequest.of(0, 32), false));

        var result = service.backfillBatch();

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.validations()).isZero();
        org.mockito.Mockito.verifyNoInteractions(resolver, validator, persistence);
    }

    @Test
    void replacesAConfirmedBrokenUrlWithValidatedPublisherMetadata() {
        ArticleRepository repository = mock(ArticleRepository.class);
        ArticlePageImageResolver resolver = mock(ArticlePageImageResolver.class);
        ArticleImageValidator validator = mock(ArticleImageValidator.class);
        ArticlePersistenceService persistence = mock(ArticlePersistenceService.class);
        var service = new ArticleImageBackfillService(repository, resolver, validator, persistence, 6, 8, 32);
        Article broken = article(new UUID(0, 3));
        String brokenUrl = "https://images.publisher.example/dead.jpg";
        String recoveredUrl = "https://images.publisher.example/current.jpg";
        when(broken.getImageUrl()).thenReturn(brokenUrl);
        when(repository.findImageAuditCandidates(any()))
                .thenReturn(new SliceImpl<>(List.of(broken), PageRequest.of(0, 32), false));
        when(validator.hasCachedResult(brokenUrl)).thenReturn(false);
        when(validator.validate(brokenUrl)).thenReturn(ArticleImageValidator.ValidationResult.INVALID);
        when(resolver.hasCachedResult(any())).thenReturn(false);
        when(resolver.resolve(any())).thenAnswer(invocation ->
                invocation.<NormalizedArticle>getArgument(0).withImageUrl(recoveredUrl));
        when(persistence.replaceImageByIdIfMatches(eq(broken.getId()), eq(brokenUrl), eq(recoveredUrl), any())).thenReturn(true);

        var result = service.backfillBatch();

        assertThat(result.validations()).isEqualTo(1);
        assertThat(result.invalidUrls()).isEqualTo(1);
        assertThat(result.enriched()).isEqualTo(1);
        verify(persistence).replaceImageByIdIfMatches(eq(broken.getId()), eq(brokenUrl), eq(recoveredUrl), any());
    }

    private Article article(UUID id) {
        Article article = mock(Article.class);
        when(article.getId()).thenReturn(id);
        when(article.getTitle()).thenReturn("Publisher supplied report");
        when(article.getCanonicalUrl()).thenReturn("https://publisher.example/story-" + id);
        when(article.getOriginalUrl()).thenReturn("https://publisher.example/story-" + id);
        when(article.getPublishedAt()).thenReturn(Instant.parse("2026-10-13T08:00:00Z"));
        when(article.getLanguage()).thenReturn("en");
        when(article.getSource()).thenReturn(new Source("Publisher", "publisher.example", "https://publisher.example/"));
        return article;
    }
}
