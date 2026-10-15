package com.pulseai.controller;
import com.pulseai.dto.article.ArticleResponse;
import com.pulseai.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/assistant")
public class CopilotController {
    private final ArticleCatalogService catalog;
    private final NewsIntelligenceService intelligence;
    public CopilotController(ArticleCatalogService catalog, NewsIntelligenceService intelligence) { this.catalog=catalog;this.intelligence=intelligence; }
    public record Query(@NotBlank @Size(max=500) String question, UUID articleId, @Size(max=100) String pageContext) {}
    public record Result(String mode,String message,List<ArticleResponse> articles) {}
    @PostMapping("/search") public Result search(@Valid @RequestBody Query query) {
        String text=query.question().toLowerCase(Locale.ROOT);
        if(query.articleId()!=null && (text.contains("summar") || text.contains("excerpt")))
            return new Result("publisher-excerpt","This is the publisher-provided excerpt. No AI summary has been generated.",List.of(catalog.article(query.articleId())));
        if(query.articleId()!=null && text.contains("related"))
            return new Result("related-coverage","Candidates share headline keywords. A common event or perspective has not been verified.",intelligence.related(query.articleId()));
        if(text.contains("why") || text.contains("compare") || text.contains("perspective") || text.contains("developed"))
            return new Result("unavailable","Grounded analysis requires the future LLM/RAG service. You can search headlines or view a publisher excerpt now.",List.of());
        String category=List.of("technology","business","science","health","sports","world","politics","entertainment").stream().filter(text::contains).findFirst().orElse(null);
        String term=text.contains("artificial intelligence") || text.matches(".*\\bai\\b.*") ? "AI" : query.question().trim();
        if(category!=null || text.contains("latest") || text.contains("major") || text.contains("headline")) term=null;
        return new Result("catalog-search","Search results from the temporary news catalog. These are source links, not a generated answer.",catalog.articles(term,category,null,null,null,0,8).items());
    }
}
