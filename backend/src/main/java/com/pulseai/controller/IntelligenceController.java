package com.pulseai.controller;
import com.pulseai.service.*;
import com.pulseai.security.CurrentUser;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class IntelligenceController {
    private final NewsIntelligenceService intelligence;
    private final com.pulseai.repository.TopicRepository topics;
    public IntelligenceController(NewsIntelligenceService intelligence, com.pulseai.repository.TopicRepository topics) { this.intelligence=intelligence;this.topics=topics; }
    @GetMapping("/articles/{id}/related") public Object related(@PathVariable UUID id) { return Map.of("method","Shared headline keywords; candidates are not verified event matches.","items",intelligence.related(id)); }
    @GetMapping("/users/me/recommendations") public Object recommendations(@CurrentUser UUID id) { return intelligence.recommendations(id); }
    @GetMapping("/users/me/analytics") public Object analytics(@CurrentUser UUID id) { return intelligence.activity(id); }
    @GetMapping("/news/coverage") public Object coverage() { return intelligence.coverage(); }
    @GetMapping("/topics") public Object topics() { return Map.of("topics",topics.findAll().stream().map(t->Map.of("id",t.getId(),"name",t.getName(),"category","Editorial topic")).toList()); }
}
