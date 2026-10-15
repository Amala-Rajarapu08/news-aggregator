package com.pulseai;
import static org.assertj.core.api.Assertions.assertThat;
import com.pulseai.ingestion.NewsIngestionService;
import com.pulseai.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties={"app.news.rss-feeds=science|https://www.nasa.gov/feed/","app.news.retention.max-articles=2000"})
@EnabledIfEnvironmentVariable(named="PULSE_LIVE_NEWS_TEST",matches="true")
class LiveNewsSmokeTest {
    @Autowired NewsIngestionService ingestion;
    @Autowired ArticleRepository articles;
    @Test void liveNasaFeedPersistsRecentNewsAndDeduplicates() {
        var first=ingestion.ingest(); assertThat(first.failures()).as("Live NASA fetch failures").isZero();
        assertThat(first.fetched()).isPositive(); assertThat(first.inserted()).as("Recent NASA articles within retention").isPositive();
        long count=articles.count(); var second=ingestion.ingest();
        assertThat(second.failures()).isZero(); assertThat(second.duplicatesSkipped()).isPositive(); assertThat(articles.count()).isEqualTo(count);
        long imageCount=articles.findAll().stream().filter(a->a.getImageUrl()!=null).count();
        // Missing images are permitted metadata; publisher image availability varies with cache expiry.
        System.out.println("LIVE_RSS_VERIFIED fetched="+first.fetched()+" inserted="+first.inserted()+" duplicates="+second.duplicatesSkipped()+" invalidOrOutsideRetention="+first.invalidSkipped()+" retainedImageUrls="+imageCount);
    }
}
