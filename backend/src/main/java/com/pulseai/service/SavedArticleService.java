package com.pulseai.service;

import java.util.UUID;

import com.pulseai.dto.article.SavedArticleResponse;
import com.pulseai.dto.common.PageResponse;
import com.pulseai.entity.SavedArticle;
import com.pulseai.exception.DuplicateResourceException;
import com.pulseai.exception.ResourceNotFoundException;
import com.pulseai.repository.ArticleRepository;
import com.pulseai.repository.SavedArticleRepository;
import com.pulseai.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedArticleService {

    private final SavedArticleRepository savedArticleRepository;
    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public SavedArticleService(
            SavedArticleRepository savedArticleRepository,
            ArticleRepository articleRepository,
            UserRepository userRepository,
            UserMapper userMapper) {
        this.savedArticleRepository = savedArticleRepository;
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional
    public SavedArticleResponse save(UUID userId, UUID articleId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
        var article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article was not found."));
        if (savedArticleRepository.existsByUserIdAndArticleKey(userId, articleId)) {
            throw new DuplicateResourceException("Article is already saved.");
        }
        if (savedArticleRepository.countByUserId(userId) >= 500) throw new IllegalArgumentException("Your reading list is limited to 500 citations. Remove a story first.");
        try {
            return userMapper.toSavedArticleResponse(
                    savedArticleRepository.saveAndFlush(new SavedArticle(user, article)));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("Article is already saved.");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<SavedArticleResponse> list(UUID userId, int page, int size) {
        var result = savedArticleRepository.findByUserId(
                userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(result, result.getContent().stream()
                .map(userMapper::toSavedArticleResponse)
                .toList());
    }

    @Transactional
    public void remove(UUID userId, UUID articleId) {
        var savedArticle = savedArticleRepository.findByUserIdAndArticleKey(userId, articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Saved article was not found."));
        savedArticleRepository.delete(savedArticle);
    }
}
