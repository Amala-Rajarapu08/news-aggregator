package com.pulseai.repository;

import java.util.UUID;

import com.pulseai.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Article> {
    boolean existsByCanonicalUrl(String canonicalUrl);
    java.util.Optional<Article> findByCanonicalUrl(String canonicalUrl);

    @EntityGraph(attributePaths = {"source", "category"})
    @Query("""
            select article from Article article
            left join article.category category
            order by case
                when lower(category.slug) = 'sports' then 0
                when lower(category.slug) = 'technology' then 1
                when lower(category.slug) = 'entertainment' then 2
                else 3
            end, article.id asc
            """)
    Slice<Article> findImageAuditCandidates(Pageable pageable);

    @EntityGraph(attributePaths = {"source", "category"})
    @Query("""
            select article from Article article
            where article.imageUrl is null
            order by article.id asc
            """)
    Slice<Article> findMissingImages(Pageable pageable);

    @EntityGraph(attributePaths = {"source", "category"})
    @Query("""
            select article from Article article
            where article.imageUrl is null
              and article.id > :afterId
            order by article.id asc
            """)
    Slice<Article> findMissingImagesAfter(@Param("afterId") UUID afterId, Pageable pageable);

    @Query("""
            select article from Article article
            left join article.category category
            where (:query is null
                or lower(article.title) like lower(concat('%', :query, '%'))
                or lower(article.description) like lower(concat('%', :query, '%')))
              and (:category is null
                or lower(category.slug) = lower(:category)
                or lower(category.name) = lower(:category))
              and (:sourceId is null or article.source.id = :sourceId)
              and (:fromDate is null or article.publishedAt >= :fromDate)
              and (:toDate is null or article.publishedAt <= :toDate)
            """)
    Page<Article> findCatalog(
            @Param("query") String query,
            @Param("category") String category,
            @Param("sourceId") UUID sourceId,
            @Param("fromDate") java.time.Instant fromDate,
            @Param("toDate") java.time.Instant toDate,
            Pageable pageable);
}
