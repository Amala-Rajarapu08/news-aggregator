package com.pulseai.service;
import com.pulseai.dto.article.ArticleResponse;
import com.pulseai.dto.common.PageResponse;
import com.pulseai.repository.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class NewsIntelligenceService {
    private final ArticleCatalogService catalog;
    private final UserPreferenceRepository preferences;
    private final JdbcTemplate jdbc;
    public NewsIntelligenceService(ArticleCatalogService catalog, UserPreferenceRepository preferences, JdbcTemplate jdbc) { this.catalog=catalog;this.preferences=preferences;this.jdbc=jdbc; }
    @Transactional(readOnly=true) public List<ArticleResponse> related(UUID id) {
        var article=catalog.article(id);
        Set<String> words=terms(article.title());
        return catalog.articles(null,null,null,null,null,0,100).items().stream()
            .filter(a->!a.id().equals(id)).map(a->new Match(a,terms(a.title()).stream().filter(words::contains).count()))
            .filter(m->m.shared>=2).sorted(Comparator.comparingLong(Match::shared).reversed()).limit(6).map(Match::article).toList();
    }
    private Set<String> terms(String title) {
        return Arrays.stream(title.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
            .filter(s->s.length()>3 && !Set.of("with","from","that","this","after","over","news","says","have","will","their","into","what").contains(s)).collect(Collectors.toSet());
    }
    private record Match(ArticleResponse article,long shared) {}
    @Transactional(readOnly=true) public List<Recommendation> recommendations(UUID userId) {
        var preference=preferences.findByUserId(userId).orElseThrow();
        if(!preference.isRecommendationsEnabled() || preference.getCategorySlugs().isEmpty()) return List.of();
        var found=new HashMap<UUID,Recommendation>();
        for(String slug:preference.getCategorySlugs()) for(var article:catalog.articles(null,slug,null,null,null,0,20).items())
            found.put(article.id(),new Recommendation(article,"You selected " + article.category() + " in your preferences."));
        return found.values().stream().sorted(Comparator.comparing((Recommendation r)->r.article().publishedAt()).reversed()).limit(20).toList();
    }
    public record Recommendation(ArticleResponse article,String reason) {}
    public Coverage coverage() {
        Instant end=Instant.now(), middle=end.minus(Duration.ofHours(12)), start=middle.minus(Duration.ofHours(12));
        var rows=jdbc.query("SELECT COALESCE(c.name, 'Uncategorized') name, COUNT(*) total, SUM(CASE WHEN a.published_at >= ? THEN 1 ELSE 0 END) current_count, SUM(CASE WHEN a.published_at < ? THEN 1 ELSE 0 END) previous_count FROM articles a LEFT JOIN categories c ON c.id=a.category_id WHERE a.published_at >= ? AND a.published_at <= ? GROUP BY c.name ORDER BY total DESC",
            (rs,n)->new CategoryCount(rs.getString("name"),rs.getLong("total"),rs.getLong("current_count"),rs.getLong("previous_count")), Timestamp.from(middle),Timestamp.from(middle),Timestamp.from(start),Timestamp.from(end));
        return new Coverage(start,middle,end,rows);
    }
    public record Coverage(Instant from,Instant midpoint,Instant to,List<CategoryCount> categories) {}
    public record CategoryCount(String category,long total,long currentCount,long previousCount) {}
    public Activity activity(UUID userId) {
        Instant cutoff=Instant.now().minus(Duration.ofDays(30));
        long openings=jdbc.queryForObject("SELECT COUNT(*) FROM reading_history WHERE user_id=? AND opened_at>=?",Long.class,userId,Timestamp.from(cutoff));
        var categories=jdbc.query("SELECT COALESCE(c.name, 'Uncategorized') name, COUNT(*) total FROM reading_history h JOIN articles a ON a.id=h.article_id LEFT JOIN categories c ON c.id=a.category_id WHERE h.user_id=? AND h.opened_at>=? GROUP BY c.name ORDER BY total DESC",(rs,n)->new Distribution(rs.getString("name"),rs.getLong("total")),userId,Timestamp.from(cutoff));
        var sources=jdbc.query("SELECT s.name, COUNT(*) total FROM reading_history h JOIN articles a ON a.id=h.article_id JOIN sources s ON s.id=a.source_id WHERE h.user_id=? AND h.opened_at>=? GROUP BY s.name ORDER BY total DESC",(rs,n)->new Distribution(rs.getString("name"),rs.getLong("total")),userId,Timestamp.from(cutoff));
        return new Activity(cutoff,Instant.now(),openings,categories,sources);
    }
    public record Distribution(String name,long count) {}
    public record Activity(Instant from,Instant to,long articleOpenings,List<Distribution> categories,List<Distribution> sources) {}
}
