package com.pulseai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.pulseai.entity.Article;
import com.pulseai.repository.ArticleRepository;
import com.pulseai.repository.CategoryRepository;
import com.pulseai.repository.SourceRepository;
import org.junit.jupiter.api.Test;

class ArticlePersistenceServiceImageBackfillTest {

    @Test
    void fillsOnlyMissingImagesAndNeverReplacesAnExistingPublisherUrl() {
        ArticleRepository articles = mock(ArticleRepository.class);
        var service = new ArticlePersistenceService(articles, mock(SourceRepository.class),
                mock(CategoryRepository.class), mock(CategoryMapper.class));
        UUID missingId = UUID.randomUUID();
        Article missing = mock(Article.class);
        when(articles.findById(missingId)).thenReturn(Optional.of(missing));
        when(missing.getImageUrl()).thenReturn(null);

        assertThat(service.backfillImageByIdIfMissing(missingId, "https://images.publisher.example/story.jpg"))
                .isTrue();
        verify(missing).setImageUrl("https://images.publisher.example/story.jpg");

        UUID existingId = UUID.randomUUID();
        Article existing = mock(Article.class);
        when(articles.findById(existingId)).thenReturn(Optional.of(existing));
        when(existing.getImageUrl()).thenReturn("https://images.publisher.example/original.jpg");

        assertThat(service.backfillImageByIdIfMissing(existingId, "https://images.publisher.example/new.jpg"))
                .isFalse();
        verify(existing, never()).setImageUrl("https://images.publisher.example/new.jpg");
    }

    @Test
    void skipsNullImageResultsWithoutQueryingTheDatabase() {
        ArticleRepository articles = mock(ArticleRepository.class);
        var service = new ArticlePersistenceService(articles, mock(SourceRepository.class),
                mock(CategoryRepository.class), mock(CategoryMapper.class));

        assertThat(service.backfillImageByIdIfMissing(UUID.randomUUID(), null)).isFalse();
        verify(articles, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void replacesOnlyTheImageUrlThatWasValidatedAsInvalid() {
        ArticleRepository articles = mock(ArticleRepository.class);
        var service = new ArticlePersistenceService(articles, mock(SourceRepository.class),
                mock(CategoryRepository.class), mock(CategoryMapper.class));
        UUID id = UUID.randomUUID();
        Article article = mock(Article.class);
        String invalidUrl = "https://publisher.example/expired.jpg";
        when(articles.findById(id)).thenReturn(Optional.of(article));
        when(article.getImageUrl()).thenReturn(invalidUrl);

        assertThat(service.replaceImageByIdIfMatches(id, invalidUrl, "https://cdn.publisher.example/recovered.jpg"))
                .isTrue();
        verify(article).setImageUrl("https://cdn.publisher.example/recovered.jpg");
        assertThat(service.replaceImageByIdIfMatches(id, "https://publisher.example/old.jpg", null))
                .isFalse();
        verify(article, never()).setImageUrl(null);
    }
}
