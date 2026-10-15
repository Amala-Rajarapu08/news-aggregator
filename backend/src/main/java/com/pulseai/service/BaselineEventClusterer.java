package com.pulseai.service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Conservative article grouping for installations without stored embeddings.
 * It requires category agreement, a short publication window, and strong
 * headline overlap anchored by a shared named-entity-like phrase or near-copy.
 */
@Component
public class BaselineEventClusterer {

    static final Duration MAX_EVENT_SPAN = Duration.ofHours(72);
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]{3,}");
    private static final Pattern CAPITALIZED_PHRASE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?:[\\p{Lu}][\\p{Ll}\\p{M}'’.-]*|[A-Z]{2,})"
                    + "(?:\\s+(?:[\\p{Lu}][\\p{Ll}\\p{M}'’.-]*|[A-Z]{2,})){1,3}");
    private static final Set<String> STOP_WORDS = Set.of(
        "about", "after", "against", "amid", "among", "another", "around", "before", "being", "between",
        "could", "does", "from", "have", "into", "just", "more", "most", "over", "said", "says", "their",
        "there", "these", "they", "this", "those", "under", "what", "when", "where", "which", "while",
        "with", "will", "would", "your", "were", "been", "also", "than", "then", "that", "them",
        "news", "live", "watch", "video", "report", "reports", "update", "updates", "latest", "first");

    public List<Cluster> cluster(List<ArticleText> input) {
        List<ArticleText> articles = input.stream()
                .filter(article -> article.title() != null && !article.title().isBlank() && article.publishedAt() != null)
                .sorted(Comparator.comparing(ArticleText::publishedAt).thenComparing(ArticleText::id))
                .toList();
        Map<UUID, Features> features = new HashMap<>();
        for (ArticleText article : articles) features.put(article.id(), features(article));

        List<List<ArticleText>> clusters = new ArrayList<>();
        for (ArticleText article : articles) {
            List<ArticleText> bestCluster = null;
            double bestScore = -1;
            for (List<ArticleText> cluster : clusters) {
                if (!withinWindow(cluster, article)) continue;
                int requiredMatches = Math.max(1, (int) Math.ceil(cluster.size() / 2.0));
                int matches = 0;
                double scoreSum = 0;
                for (ArticleText member : cluster) {
                    Match match = compare(features.get(member.id()), features.get(article.id()), member, article);
                    if (match.matches()) {
                        matches++;
                        scoreSum += match.score();
                    }
                }
                if (matches >= requiredMatches) {
                    double score = scoreSum / matches;
                    if (score > bestScore) {
                        bestScore = score;
                        bestCluster = cluster;
                    }
                }
            }
            if (bestCluster == null) {
                List<ArticleText> fresh = new ArrayList<>();
                fresh.add(article);
                clusters.add(fresh);
            } else {
                bestCluster.add(article);
            }
        }

        return clusters.stream().filter(cluster -> cluster.size() >= 2)
                .map(members -> new Cluster(List.copyOf(members)))
                .sorted(Comparator.comparing(cluster -> cluster.articles().get(0).publishedAt()))
                .toList();
    }

    private boolean withinWindow(List<ArticleText> cluster, ArticleText article) {
        Instant earliest = cluster.get(0).publishedAt();
        return Duration.between(earliest, article.publishedAt()).compareTo(MAX_EVENT_SPAN) <= 0;
    }

    private Match compare(Features left, Features right, ArticleText leftArticle, ArticleText rightArticle) {
        if (!sameCategory(leftArticle.category(), rightArticle.category())) return Match.NO_MATCH;
        Set<String> commonTitle = intersection(left.titleTokens(), right.titleTokens());
        if (commonTitle.size() < 2) return Match.NO_MATCH;

        double titleSimilarity = jaccard(left.titleTokens(), right.titleTokens());
        double textSimilarity = jaccard(left.allTokens(), right.allTokens());
        Set<String> sharedEntityPhrases = intersection(left.entityPhrases(), right.entityPhrases());
        Set<String> sharedEntityTokens = intersection(left.entityTokens(), right.entityTokens());

        boolean sameNamedEntity = sharedEntityPhrases.stream().anyMatch(phrase -> phrase.split(" ").length >= 2);
        boolean anchoredByEntityPhrase = sameNamedEntity && titleSimilarity >= 0.17 && textSimilarity >= 0.08;
        boolean anchoredByEntity = !sharedEntityTokens.isEmpty()
                && commonTitle.size() >= 3 && titleSimilarity >= 0.25 && textSimilarity >= 0.10;
        boolean nearDuplicateHeadlines = commonTitle.size() >= 4
                && titleSimilarity >= 0.55 && textSimilarity >= 0.12;
        boolean matches = anchoredByEntityPhrase || anchoredByEntity || nearDuplicateHeadlines;
        return matches ? new Match(true, titleSimilarity * 0.7 + textSimilarity * 0.3) : Match.NO_MATCH;
    }

    private boolean sameCategory(String left, String right) {
        if (left == null || left.isBlank()) return right == null || right.isBlank();
        return right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private Features features(ArticleText article) {
        String title = normalize(article.title());
        String text = normalize(article.title() + " " + (article.description() == null ? "" : article.description()));
        Set<String> titleTokens = tokens(title);
        Set<String> allTokens = tokens(text);
        Set<String> phrases = new LinkedHashSet<>();
        Set<String> entityTokens = new LinkedHashSet<>();
        Matcher matcher = CAPITALIZED_PHRASE.matcher(article.title());
        while (matcher.find()) {
            String phrase = normalize(matcher.group()).trim();
            Set<String> phraseTokens = tokens(phrase);
            if (phraseTokens.size() >= 2) {
                phrases.add(String.join(" ", phraseTokens));
                entityTokens.addAll(phraseTokens);
            }
        }
        return new Features(titleTokens, allTokens, phrases, entityTokens);
    }

    private Set<String> tokens(String input) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = WORDS.matcher(input);
        while (matcher.find()) {
            String word = matcher.group();
            if (!STOP_WORDS.contains(word) && !word.chars().allMatch(Character::isDigit)) result.add(word);
        }
        return result;
    }

    private String normalize(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection(left, right).size() / union.size();
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> common = new HashSet<>(left);
        common.retainAll(right);
        return common;
    }

    private record Features(Set<String> titleTokens, Set<String> allTokens,
            Set<String> entityPhrases, Set<String> entityTokens) {}

    private record Match(boolean matches, double score) {
        private static final Match NO_MATCH = new Match(false, 0);
    }

    public record ArticleText(UUID id, String title, String description, String category,
            UUID sourceId, String sourceName, String originalUrl, Instant publishedAt) {}

    public record Cluster(List<ArticleText> articles) {}
}
