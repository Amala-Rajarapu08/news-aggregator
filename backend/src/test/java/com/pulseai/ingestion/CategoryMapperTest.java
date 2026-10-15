package com.pulseai.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CategoryMapperTest {

    private final CategoryMapper mapper = new CategoryMapper();

    @Test
    void mapsKnownProviderCategoriesAndLeavesUnknownValuesUnassigned() {
        assertThat(mapper.toSlug("Tech")).contains("technology");
        assertThat(mapper.toSlug("Economy")).contains("business");
        assertThat(mapper.toSlug("Science & Environment")).contains("science");
        assertThat(mapper.toSlug("World")).contains("world");
        assertThat(mapper.toSlug("Nation")).contains("general");
        assertThat(mapper.toSlug("National")).contains("general");
        assertThat(mapper.toSlug("General")).contains("general");
        assertThat(mapper.toSlug("unclassified")).isEmpty();
        assertThat(mapper.toSlug(null)).isEmpty();
    }
}
