package com.vibeteacher.api;

import com.vibeteacher.api.dto.RevisionRequest;
import com.vibeteacher.api.dto.RevisionResponse;
import com.vibeteacher.service.OpenAIService;
import com.vibeteacher.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RevisionController {
    private final OpenAIService openAIService;
    private final TokenService tokenService;

    public RevisionController(OpenAIService openAIService, TokenService tokenService) {
        this.openAIService = openAIService;
        this.tokenService = tokenService;
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
        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少英文片段");
        }
        if (request.getModel() == null || request.getModel().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少模型名称");
        }
        String level = request.getLevel() == null ? "中学7年级" : request.getLevel();

        RevisionResponse response = openAIService.revise(request.getModel(), level, request.getText());
        return ResponseEntity.ok(response);
    }
}
