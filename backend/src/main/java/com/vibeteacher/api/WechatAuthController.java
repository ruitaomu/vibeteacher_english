package com.vibeteacher.api;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibeteacher.security.TokenService;
import com.vibeteacher.service.WechatAuthException;
import com.vibeteacher.service.WechatAuthService;

@RestController
@RequestMapping("/api/auth")
public class WechatAuthController {
    private final WechatAuthService wechatAuthService;
    private final TokenService tokenService;

    public WechatAuthController(WechatAuthService wechatAuthService, TokenService tokenService) {
        this.wechatAuthService = wechatAuthService;
        this.tokenService = tokenService;
    }

    @PostMapping("/wechat")
    public ResponseEntity<?> wechatLogin(@RequestBody Map<String, String> payload) {
        String code = payload == null ? null : payload.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少微信登录 code");
        }
        try {
            String userId = wechatAuthService.authenticateAndResolveUserId(code);
            TokenService.Token token = tokenService.issueToken(userId);

            Map<String, Object> result = new HashMap<>();
            result.put("token", token.value());
            result.put("expiresAt", token.expiresAt());
            result.put("userId", userId);
            result.put("authMode", "wechat");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (WechatAuthException ex) {
            return ResponseEntity.status(ex.getStatus()).body(ex.getMessage());
        }
    }
}
