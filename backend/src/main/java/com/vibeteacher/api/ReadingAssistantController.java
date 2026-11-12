package com.vibeteacher.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibeteacher.api.dto.ReadingAnalysisRequest;
import com.vibeteacher.api.dto.ReadingAnalysisResponse;
import com.vibeteacher.api.dto.SentenceStructureRequest;
import com.vibeteacher.api.dto.SentenceStructureResponse;
import com.vibeteacher.security.TokenService;
import com.vibeteacher.service.ReadingArticleService;
import com.vibeteacher.service.ReadingAssistantService;

@RestController
@RequestMapping("/api/reading")
public class ReadingAssistantController {
    private static final Logger logger = LoggerFactory.getLogger(ReadingAssistantController.class);

    private final ReadingAssistantService readingAssistantService;
    private final ReadingArticleService readingArticleService;
    private final TokenService tokenService;

    public ReadingAssistantController(ReadingAssistantService readingAssistantService,
                                      ReadingArticleService readingArticleService,
                                      TokenService tokenService) {
        this.readingAssistantService = readingAssistantService;
        this.readingArticleService = readingArticleService;
        this.tokenService = tokenService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @RequestBody ReadingAnalysisRequest request) {
        ResponseEntity<String> authError = validateAuthorization(authorization);
        if (authError != null) {
            return authError;
        }
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少阅读内容");
        }
        if (request.getModel() == null || request.getModel().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少模型名称");
        }

        try {
            ReadingAnalysisResponse response = readingAssistantService.analyze(request.getModel(), request.getText());
            return ResponseEntity.ok(response);
        } catch (IllegalStateException ex) {
            logger.warn("Reading analyze failed due to service state. model={}", request.getModel(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
        } catch (RuntimeException ex) {
            logger.error("Reading analyze failed unexpectedly. model={}", request.getModel(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("阅读分析服务暂时不可用，请稍后重试。错误详情: " + ex.getMessage());
        }
    }

    @PostMapping("/structure")
    public ResponseEntity<?> explainStructure(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestBody SentenceStructureRequest request) {
        ResponseEntity<String> authError = validateAuthorization(authorization);
        if (authError != null) {
            return authError;
        }
        if (request == null || request.getSentence() == null || request.getSentence().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少句子内容");
        }
        if (request.getModel() == null || request.getModel().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少模型名称");
        }

        try {
            SentenceStructureResponse response = readingAssistantService.explainSentenceStructure(
                request.getModel(),
                request.getSentence()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalStateException ex) {
            logger.warn("Sentence structure explain failed due to service state. model={}", request.getModel(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
        } catch (RuntimeException ex) {
            logger.error("Sentence structure explain failed unexpectedly. model={}", request.getModel(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("句子结构讲解服务暂时不可用，请稍后重试。错误详情: " + ex.getMessage());
        }
    }

    @GetMapping("/article")
    public ResponseEntity<?> fetchArticle(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestParam(value = "level", required = false) String level) {
        ResponseEntity<String> authError = validateAuthorization(authorization);
        if (authError != null) {
            return authError;
        }
        if (level == null || level.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少学习级别");
        }

        try {
            return ResponseEntity.ok(readingArticleService.fetchArticle(level));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (IllegalStateException ex) {
            logger.warn("Reading article fetch failed. level={}", level, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
        }
    }

    private ResponseEntity<String> validateAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("缺少访问令牌");
        }
        String token = authorization.substring("Bearer ".length());
        if (!tokenService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("访问令牌无效或已过期");
        }
        return null;
    }
}
