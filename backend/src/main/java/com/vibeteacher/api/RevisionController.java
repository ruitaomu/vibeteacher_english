package com.vibeteacher.api;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibeteacher.api.dto.RevisionRequest;
import com.vibeteacher.api.dto.RevisionResponse;
import com.vibeteacher.model.Achievement;
import com.vibeteacher.security.TokenService;
import com.vibeteacher.service.OpenAIService;
import com.vibeteacher.service.RevisionPersistenceService;
import com.vibeteacher.service.UserProgressService;

@RestController
@RequestMapping("/api")
public class RevisionController {
    private static final Logger logger = LoggerFactory.getLogger(RevisionController.class);

    private final OpenAIService openAIService;
    private final TokenService tokenService;
    private final UserProgressService userProgressService;
    private final RevisionPersistenceService revisionPersistenceService;

    public RevisionController(OpenAIService openAIService,
                              TokenService tokenService,
                              UserProgressService userProgressService,
                              RevisionPersistenceService revisionPersistenceService) {
        this.openAIService = openAIService;
        this.tokenService = tokenService;
        this.userProgressService = userProgressService;
        this.revisionPersistenceService = revisionPersistenceService;
    }

    @PostMapping("/session")
    public ResponseEntity<?> issueSession() {
        TokenService.Token token = tokenService.issueToken();
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token.value());
        payload.put("expiresAt", token.expiresAt());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/revise")
    public ResponseEntity<?> revise(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestBody RevisionRequest request) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("缺少访问令牌");
        }
        String token = authorization.substring("Bearer ".length());
        if (!tokenService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("访问令牌无效或已过期");
        }
        String tokenUserId = normalizeUserId(tokenService.extractUserId(token));
        String requestUserId = normalizeUserId(request.getUserId());
        String userId;
        try {
            userId = resolveUserId(tokenUserId, requestUserId);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }

        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少英文片段");
        }
        if (request.getModel() == null || request.getModel().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少模型名称");
        }
        String level = request.getLevel() == null ? "中学7年级" : request.getLevel();

        RevisionResponse response;
        try {
            // 查询用户词汇本
            List<com.vibeteacher.api.dto.VocabularyItem> userVocabulary = null;
            if (userId != null) {
                userVocabulary = userProgressService.getVocabulary(userId);
            }
            
            response = openAIService.revise(
                request.getModel(),
                level,
                request.getText(),
                request.getRequirements(),
                request.isExpand(),
                userVocabulary
            );
        } catch (IllegalStateException ex) {
            logger.warn("Revise failed due to service state. model={}, level={}", request.getModel(), level, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
        } catch (RuntimeException ex) {
            logger.error("Revise failed unexpectedly. model={}, level={}", request.getModel(), level, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("写作改写服务暂时不可用，请稍后重试。错误详情: " + ex.getMessage());
        }

        if (userId != null) {
            List<Achievement> newAchievements = userProgressService.recordSubmission(userId);
            int usageCount = userProgressService.countSuggestedVocabularyMatches(userId, request.getText());
            List<Achievement> usageAchievements = userProgressService.recordSuggestedVocabularyUsage(userId, usageCount);
            if (response.getVocabularySuggestions() != null && !response.getVocabularySuggestions().isEmpty()) {
                userProgressService.addVocabulary(userId, response.getVocabularySuggestions());
            }
            
            // 处理词汇表使用情况，添加成就
            List<Achievement> vocabUsageAchievements = userProgressService.recordVocabularyUsageFromAI(userId, response.getVocabularyUsage());
            revisionPersistenceService.saveRevisionWithIssues(userId, request, response, level);
            response.setNewAchievements(mergeAchievements(mergeAchievements(newAchievements, usageAchievements), vocabUsageAchievements));
        }
        return ResponseEntity.ok(response);
    }

    private String resolveUserId(String tokenUserId, String requestUserId) {
        if (tokenUserId == null) {
            // Anonymous token: no persistence.
            return null;
        }
        if (requestUserId != null && !requestUserId.equals(tokenUserId)) {
            throw new IllegalArgumentException("uid 与登录身份不一致");
        }
        return tokenUserId;
    }

    private String normalizeUserId(String userId) {
        if (userId == null) {
            return null;
        }
        String trimmed = userId.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private List<Achievement> mergeAchievements(List<Achievement> a, List<Achievement> b) {
        Map<String, Achievement> merged = new LinkedHashMap<>();
        if (a != null) {
            for (Achievement item : a) {
                if (item != null && item.getId() != null) {
                    merged.put(item.getId(), item);
                }
            }
        }
        if (b != null) {
            for (Achievement item : b) {
                if (item != null && item.getId() != null) {
                    merged.put(item.getId(), item);
                }
            }
        }
        return List.copyOf(merged.values());
    }
}
