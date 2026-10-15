package com.pulseai.repository;

import java.util.UUID;

import com.pulseai.entity.Source;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, UUID> {
    java.util.Optional<Source> findByDomainIgnoreCase(String domain);

    Page<Source> findAllByActiveTrueOrderByNameAsc(Pageable pageable);

    @Query("""
            select distinct source
            from Article article
            join article.source source
            left join article.category category
            where source.active = true
              and (:category = ''
                or lower(category.slug) = :category
                or lower(category.name) = :category)
              and (:query = ''
                or lower(article.title) like concat('%', :query, '%')
                or lower(coalesce(article.description, '')) like concat('%', :query, '%')
                or lower(source.name) like concat('%', :query, '%')
                or lower(source.domain) like concat('%', :query, '%')
                or lower(coalesce(category.name, '')) like concat('%', :query, '%')
                or lower(coalesce(category.slug, '')) like concat('%', :query, '%'))
            order by source.name asc
            """)
    java.util.List<Source> findAvailableForCategory(String category, String query);
}
