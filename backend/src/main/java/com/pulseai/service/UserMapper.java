package com.pulseai.service;

import com.pulseai.dto.article.ArticleResponse;
import com.pulseai.dto.article.SourceResponse;
import com.pulseai.dto.auth.AuthResponse;
import com.pulseai.dto.history.ReadingHistoryResponse;
import com.pulseai.dto.article.SavedArticleResponse;
import com.pulseai.dto.user.UserResponse;
import com.pulseai.entity.Article;
import com.pulseai.entity.ReadingHistory;
import com.pulseai.entity.SavedArticle;
import com.pulseai.entity.Source;
import com.pulseai.entity.User;
import com.pulseai.security.JwtTokenService;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt());
    }

    public AuthResponse toAuthResponse(User user, JwtTokenService.IssuedToken token) {
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), toUserResponse(user));
    }

    public ArticleResponse toArticleResponse(Article article) {
        Source source = article.getSource();
        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getDescription(),
                article.getOriginalUrl(),
                article.getImageUrl(),
                article.getAuthor(),
                article.getPublishedAt(),
                article.getLanguage(),
                new SourceResponse(source.getId(), source.getName(), source.getDomain(), source.getBaseUrl()),
                article.getCategory() == null ? null : article.getCategory().getName());
    }

    public SavedArticleResponse toSavedArticleResponse(SavedArticle savedArticle) {
        return new SavedArticleResponse(
                savedArticle.getId(),
                savedArticle.getCreatedAt(),
                savedArticle.getArticle() == null ? savedArticle.snapshot() : toArticleResponse(savedArticle.getArticle()), savedArticle.getArticle() == null);
    }

    public ReadingHistoryResponse toReadingHistoryResponse(ReadingHistory history) {
        return new ReadingHistoryResponse(
                history.getId(),
                history.getOpenedAt(),
                history.getDurationSeconds(),
                history.isCompleted(),
                history.getScrollPercentage(),
                toArticleResponse(history.getArticle()));
    }
}
