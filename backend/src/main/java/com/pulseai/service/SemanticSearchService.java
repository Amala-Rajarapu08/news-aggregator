package com.pulseai.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.pulseai.dto.article.ArticleResponse;
import com.pulseai.dto.search.SearchArticle;
import com.pulseai.dto.search.SemanticSearchResponse;
import com.pulseai.repository.PgVectorSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SemanticSearchService {

    private static final Logger logger = LoggerFactory.getLogger(SemanticSearchService.class);
    private final ArticleCatalogService catalog;
    private final ArticleEmbeddingService embeddings;
    private final AiEmbeddingClient embeddingClient;
    private final PgVectorSearchRepository vectorSearch;

    public SemanticSearchService(
            ArticleCatalogService catalog,
            ArticleEmbeddingService embeddings,
            AiEmbeddingClient embeddingClient,
            PgVectorSearchRepository vectorSearch) {
        this.catalog = catalog;
        this.embeddings = embeddings;
        this.embeddingClient = embeddingClient;
        this.vectorSearch = vectorSearch;
    }

    public SemanticSearchResponse search(
            String query,
            String category,
            UUID sourceId,
            Instant from,
            Instant to,
            int page,
            int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("The from date must not be after the to date.");
        }
        if (!embeddings.isReady()) return keywordFallback(query, category, sourceId, from, to, page, size, "pgvector-unavailable");
        long indexed = embeddings.indexedCount();
        if (indexed == 0) return keywordFallback(query, category, sourceId, from, to, page, size, "embedding-index-empty");

        try {
            var vector = embeddingClient.embed(List.of(query)).embeddings().get(0);
            String literal = vectorLiteral(vector);
            long total = vectorSearch.count(embeddingClient.modelName(), category, sourceId, from, to);
            var hits = vectorSearch.search(literal, embeddingClient.modelName(), category, sourceId,
                    from, to, page, size);
            List<UUID> ids = hits.stream().map(PgVectorSearchRepository.Hit::articleId).toList();
            Map<UUID, ArticleResponse> articles = catalog.articlesByIds(ids).stream()
                    .collect(Collectors.toMap(ArticleResponse::id, article -> article));
            List<SearchArticle> items = hits.stream()
                    .filter(hit -> articles.containsKey(hit.articleId()))
                    .map(hit -> new SearchArticle(articles.get(hit.articleId()), hit.relevance(), List.of()))
                    .toList();
            return new SemanticSearchResponse("pgvector", embeddingClient.modelName(), null,
                    indexed, total, page, size, totalPages(total, size), items);
        } catch (RuntimeException exception) {
            logger.warn("Semantic search fell back to catalog keywords exceptionType={}",
                    exception.getClass().getSimpleName());
            return keywordFallback(query, category, sourceId, from, to, page, size, "embedding-service-unavailable");
        }
    }

    public SemanticSearchResponse keywordSearch(
            String query, String category, UUID sourceId, Instant from, Instant to, int page, int size) {
        return keywordFallback(query, category, sourceId, from, to, page, size, "keyword-search-requested");
    }

    private SemanticSearchResponse keywordFallback(
            String query,
            String category,
            UUID sourceId,
            Instant from,
            Instant to,
            int page,
            int size,
            String reason) {
        var result = catalog.articles(query, category, sourceId, from, to, page, size);
        var items = result.items().stream()
                .map(article -> new SearchArticle(article, null, List.of()))
                .toList();
        long indexed = "embedding-index-empty".equals(reason) || "embedding-service-unavailable".equals(reason)
                ? embeddings.indexedCount() : 0;
        return new SemanticSearchResponse("keyword-fallback", null, reason, indexed,
                result.totalItems(), page, size, totalPages(result.totalItems(), size), items);
    }

    private int totalPages(long total, int size) {
        return total == 0 ? 0 : (int) Math.ceil(total / (double) size);
    }

    private String vectorLiteral(List<Double> vector) {
        return "[" + String.join(",", vector.stream().map(value -> Double.toString(value)).toList()) + "]";
    }
}
