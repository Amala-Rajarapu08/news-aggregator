package com.pulseai.ingestion;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    private static final Map<String, String> CATEGORY_SLUGS = Map.ofEntries(
            Map.entry("business", "business"),
            Map.entry("economy", "business"),
            Map.entry("finance", "business"),
            Map.entry("entertainment", "entertainment"),
            Map.entry("entertainment & arts", "entertainment"),
            Map.entry("culture", "entertainment"),
            Map.entry("health", "health"),
            Map.entry("medicine", "health"),
            Map.entry("politics", "politics"),
            Map.entry("government", "politics"),
            Map.entry("science", "science"),
            Map.entry("science & environment", "science"),
            Map.entry("sci/environment", "science"),
            Map.entry("technology", "technology"),
            Map.entry("tech", "technology"),
            Map.entry("sports", "sports"),
            Map.entry("sport", "sports"),
            Map.entry("world", "world"),
            Map.entry("international", "world"),
            Map.entry("general", "general"),
            Map.entry("nation", "general"),
            Map.entry("national", "general"));

    public Optional<String> toSlug(String category) {
        if (category == null || category.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(CATEGORY_SLUGS.get(category.trim().toLowerCase(Locale.ROOT)));
    }
}
