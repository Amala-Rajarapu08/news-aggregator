package com.pulseai.repository;

import java.util.UUID;

import com.pulseai.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    java.util.Optional<Category> findBySlugIgnoreCase(String slug);

    java.util.List<Category> findAllByOrderByNameAsc();
}
