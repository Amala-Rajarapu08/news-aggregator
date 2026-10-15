package com.pulseai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.pulseai.entity.Article;
import com.pulseai.entity.Category;
import com.pulseai.entity.Source;
import com.pulseai.repository.ArticleRepository;
import com.pulseai.repository.CategoryRepository;
import com.pulseai.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ArticleImageBackfillRepositoryTest {

    @Autowired
    private ArticleRepository articles;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private CategoryRepository categories;

    @Test
    void returnsOnlyMissingImagesAndContinuesAfterTheLastScannedId() {
        Source source = sources.saveAndFlush(new Source("Image Backfill Test", "backfill.example", "https://backfill.example/"));
        articles.saveAndFlush(article(source, "Without image", null));
        articles.saveAndFlush(article(source, "Another image missing", null));
        articles.saveAndFlush(article(source, "With image", "https://images.example/story.jpg"));

        var firstBatch = articles.findMissingImages(PageRequest.of(0, 1));
        assertThat(firstBatch.getContent()).hasSize(1);
        assertThat(firstBatch.getContent().get(0).getImageUrl()).isNull();

        var remaining = articles.findMissingImagesAfter(firstBatch.getContent().get(0).getId(), PageRequest.of(0, 10));

        assertThat(remaining.getContent()).hasSize(1);
        assertThat(remaining.getContent()).allSatisfy(article -> {
            String imageUrl = article.getImageUrl();
            assertThat(imageUrl).isNull();
        });
    }

    @Test
    void auditsAllImagesWithSportsTechnologyAndEntertainmentFirst() {
        Source source = sources.saveAndFlush(new Source("Priority Test", "priority.example", "https://priority.example/"));
        Category entertainment = categories.findBySlugIgnoreCase("entertainment").orElseThrow();
        Category technology = categories.findBySlugIgnoreCase("technology").orElseThrow();
        Category sports = categories.findBySlugIgnoreCase("sports").orElseThrow();
        Category business = categories.findBySlugIgnoreCase("business").orElseThrow();
        articles.saveAndFlush(article(source, "Other", "https://images.example/other.jpg", business));
        articles.saveAndFlush(article(source, "Entertainment", "https://images.example/entertainment.jpg", entertainment));
        articles.saveAndFlush(article(source, "Technology", null, technology));
        articles.saveAndFlush(article(source, "Sports", null, sports));

        var result = articles.findImageAuditCandidates(PageRequest.of(0, 4));

        assertThat(result.getContent()).extracting(article -> article.getCategory().getSlug())
                .containsExactly("sports", "technology", "entertainment", "business");
    }

    private Article article(Source source, String title, String imageUrl) {
        return article(source, title, imageUrl, null);
    }

    private Article article(Source source, String title, String imageUrl, Category category) {
        String url = "https://backfill.example/" + title.toLowerCase().replace(' ', '-');
        return new Article(title, "Test story", url, url, imageUrl, null, Instant.parse("2026-10-13T08:00:00Z"),
                "en", source, category);
    }
}
