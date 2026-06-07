package com.vibeteacher.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class WechatAuthService {
    private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final KeyVaultService keyVaultService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final boolean localMockLoginEnabled;
    private final String localMockUserId;

    public WechatAuthService(KeyVaultService keyVaultService,
                             @Value("${wechat.auth.local-mock-login:false}") boolean localMockLoginEnabled,
                             @Value("${wechat.auth.local-mock-user-id:wx_local_dev}") String localMockUserId) {
        this.keyVaultService = keyVaultService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.localMockLoginEnabled = localMockLoginEnabled;
        this.localMockUserId = localMockUserId == null ? "wx_local_dev" : localMockUserId.trim();
    }

    public String authenticateAndResolveUserId(String code) {
        if (code == null || code.isBlank()) {
            throw new WechatAuthException(HttpStatus.BAD_REQUEST, "缺少微信登录 code");
        }

        String appId = keyVaultService.getWechatAppId();
        String appSecret = keyVaultService.getWechatAppSecret();
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            if (localMockLoginEnabled) {
                return localMockUserId.isBlank() ? "wx_local_dev" : localMockUserId;
            }
            throw new WechatAuthException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "未配置微信小程序凭据（WECHAT_APP_ID / WECHAT_APP_SECRET）"
            );
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("appid", appId.trim());
        params.add("secret", appSecret.trim());
        params.add("js_code", code.trim());
        params.add("grant_type", "authorization_code");

        String url = UriComponentsBuilder.fromHttpUrl(JSCODE2SESSION_URL)
                .queryParams(params)
                .toUriString();

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            Map<String, Object> payload = parseWechatPayload(response.getBody());
            if (payload == null) {
                throw new WechatAuthException(HttpStatus.BAD_GATEWAY, "微信登录失败：微信接口返回空响应");
            }

            Object errCodeObj = payload.get("errcode");
            if (errCodeObj instanceof Number number && number.intValue() != 0) {
                Object errMsgObj = payload.get("errmsg");
                String errMsg = errMsgObj == null ? "unknown" : errMsgObj.toString();
                throw new WechatAuthException(resolveStatusByWechatErrCode(number.intValue()),
                    buildWechatErrorMessage(number.intValue(), errMsg));
            }

            Object openidObj = payload.get("openid");
            String openid = openidObj == null ? "" : openidObj.toString().trim();
            if (openid.isBlank()) {
                throw new WechatAuthException(HttpStatus.BAD_GATEWAY, "微信登录失败：微信接口未返回 openid");
            }

            return "wx_" + sha256(openid).substring(0, 24);
        } catch (WechatAuthException ex) {
            throw ex;
        } catch (RestClientException ex) {
            String details = ex.getMessage() == null ? "unknown" : ex.getMessage();
            if (ex instanceof RestClientResponseException responseEx) {
                String body = responseEx.getResponseBodyAsString();
                if (body != null && !body.isBlank()) {
                    details = details + " | body=" + body;
                }
            }
            throw new WechatAuthException(
                HttpStatus.BAD_GATEWAY,
                "微信登录请求失败：后端无法访问微信接口，请检查网络或微信配置。详情=" + details
            );
        }
    }

    private HttpStatus resolveStatusByWechatErrCode(int errCode) {
        return switch (errCode) {
            case 40029 -> HttpStatus.UNAUTHORIZED;
            case 40013, 40125 -> HttpStatus.INTERNAL_SERVER_ERROR;
            case 45011 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    private Map<String, Object> parseWechatPayload(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            throw new WechatAuthException(
                HttpStatus.BAD_GATEWAY,
                "微信登录失败：微信接口返回了非 JSON 响应。body=" + rawBody
            );
        }
    }

    private String buildWechatErrorMessage(int errCode, String errMsg) {
        String advice = switch (errCode) {
            case 40029 -> "code 无效或已过期，请重新 wx.login 后重试。";
            case 40013 -> "appid 配置无效，请检查后端 WECHAT_APP_ID。";
            case 40125 -> "appsecret 配置无效，请检查后端 WECHAT_APP_SECRET。";
            case 45011 -> "请求过于频繁，请稍后重试。";
            case -1 -> "微信系统繁忙，请稍后重试。";
            default -> "请检查微信配置与网络连通性。";
        };
        return "微信登录失败: errcode=" + errCode + ", errmsg=" + errMsg + "。" + advice;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("计算微信用户标识失败", ex);
        }
    }
}
