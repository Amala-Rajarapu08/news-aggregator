package com.pulseai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pulseai.service.BaselineEventClusterer;
import com.pulseai.service.BaselineEventClusterer.ArticleText;
import org.junit.jupiter.api.Test;

class BaselineEventClustererTest {

    private final BaselineEventClusterer clusterer = new BaselineEventClusterer();

    @Test
    void groupsSameEventHeadlinesWithASharedEntityAndRejectsSameCategoryUnrelatedArticles() {
        Instant now = Instant.parse("2026-10-14T10:00:00Z");
        var first = article("Six Flags rollercoaster incident leaves visitor dead", "Police say a visitor died after falling from a ride.", "World", "one", now);
        var second = article("Witnesses describe Six Flags rollercoaster accident", "A person died and others were injured at the Georgia park.", "World", "two", now.plusSeconds(3600));
        var unrelated = article("Zimbabwean author denies using AI to write award-winning story", "The author responded to an accusation.", "World", "three", now.plusSeconds(600));

        List<BaselineEventClusterer.Cluster> clusters = clusterer.cluster(List.of(first, second, unrelated));

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).articles()).containsExactly(first, second);
    }

    @Test
    void categoryAndPublicationWindowConstrainHeadlineMatches() {
        Instant now = Instant.parse("2026-10-14T10:00:00Z");
        var first = article("Six Flags rollercoaster incident leaves visitor dead", "Police investigate the incident.", "World", "one", now);
        var differentCategory = article("Witnesses describe Six Flags rollercoaster accident", "A person died at the park.", "Entertainment", "two", now.plusSeconds(3600));
        var tooLate = article("Witnesses describe Six Flags rollercoaster accident", "A person died at the park.", "World", "three", now.plusSeconds(73 * 3600));

        assertThat(clusterer.cluster(List.of(first, differentCategory, tooLate))).isEmpty();
    }

    private ArticleText article(String title, String description, String category, String source, Instant publishedAt) {
        UUID id = UUID.nameUUIDFromBytes((source + title).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID sourceId = UUID.nameUUIDFromBytes(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new ArticleText(id, title, description, category, sourceId, source,
                "https://" + source + ".example/story", publishedAt);
    }
}
