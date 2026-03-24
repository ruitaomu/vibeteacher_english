package com.vibeteacher.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;

@Service
public class KeyVaultService {
    private final String keyVaultUrl;
    private final String secretName;
    private final String endpointSecretName;
    private final String ocrSecretName;
    private final String ocrEndpointSecretName;
    private final String wechatAppIdSecretName;
    private final String wechatAppSecretSecretName;
    private final String mysqlDbSecretName;
    private final String mysqlHostSecretName;
    private final String mysqlUsernameSecretName;
    private final String mysqlPasswordSecretName;
    private final String mysqlPortSecretName;
    private final SecretClient secretClient;
    private volatile String cachedSecret;
    private volatile Instant cacheExpiresAt;
    private volatile String cachedEndpoint;
    private volatile Instant endpointCacheExpiresAt;
    private volatile String cachedOcrKey;
    private volatile Instant ocrKeyCacheExpiresAt;
    private volatile String cachedOcrEndpoint;
    private volatile Instant ocrEndpointCacheExpiresAt;
    private volatile String cachedWechatAppId;
    private volatile Instant wechatAppIdCacheExpiresAt;
    private volatile String cachedWechatAppSecret;
    private volatile Instant wechatAppSecretCacheExpiresAt;
    private volatile String cachedMysqlDb;
    private volatile Instant mysqlDbCacheExpiresAt;
    private volatile String cachedMysqlHost;
    private volatile Instant mysqlHostCacheExpiresAt;
    private volatile String cachedMysqlUsername;
    private volatile Instant mysqlUsernameCacheExpiresAt;
    private volatile String cachedMysqlPassword;
    private volatile Instant mysqlPasswordCacheExpiresAt;
    private volatile String cachedMysqlPort;
    private volatile Instant mysqlPortCacheExpiresAt;

    public KeyVaultService(
            @Value("${keyvault.url:}") String keyVaultUrl,
            @Value("${keyvault.secretName:AZUREOPENAI-APIKEY}") String secretName,
            @Value("${keyvault.endpointSecretName:AZUREOPENAI-ENDPOINT}") String endpointSecretName,
            @Value("${keyvault.ocrSecretName:AZUREOCR-APIKEY}") String ocrSecretName,
            @Value("${keyvault.ocrEndpointSecretName:AZUREOCR-ENDPOINT}") String ocrEndpointSecretName,
            @Value("${keyvault.wechatAppIdSecretName:WECHAT-APP-ID}") String wechatAppIdSecretName,
            @Value("${keyvault.wechatAppSecretSecretName:WECHAT-APP-SECRET}") String wechatAppSecretSecretName,
            @Value("${keyvault.mysqlDbSecretName:MYSQL-DB}") String mysqlDbSecretName,
            @Value("${keyvault.mysqlHostSecretName:MYSQL-HOST}") String mysqlHostSecretName,
            @Value("${keyvault.mysqlUsernameSecretName:MYSQL-USERNAME}") String mysqlUsernameSecretName,
            @Value("${keyvault.mysqlPasswordSecretName:MYSQL-PASSWORD}") String mysqlPasswordSecretName,
            @Value("${keyvault.mysqlPortSecretName:MYSQL-PORT}") String mysqlPortSecretName
    ) {
        this.keyVaultUrl = keyVaultUrl == null ? "" : keyVaultUrl.trim();
        this.secretName = secretName == null ? "AZUREOPENAI-APIKEY" : secretName.trim();
        this.endpointSecretName = endpointSecretName == null ? "AZUREOPENAI-ENDPOINT" : endpointSecretName.trim();
        this.ocrSecretName = ocrSecretName == null ? "AZUREOCR-APIKEY" : ocrSecretName.trim();
        this.ocrEndpointSecretName = ocrEndpointSecretName == null ? "AZUREOCR-ENDPOINT" : ocrEndpointSecretName.trim();
        this.wechatAppIdSecretName = wechatAppIdSecretName == null ? "WECHAT-APP-ID" : wechatAppIdSecretName.trim();
        this.wechatAppSecretSecretName = wechatAppSecretSecretName == null ? "WECHAT-APP-SECRET" : wechatAppSecretSecretName.trim();
        this.mysqlDbSecretName = mysqlDbSecretName == null ? "MYSQL-DB" : mysqlDbSecretName.trim();
        this.mysqlHostSecretName = mysqlHostSecretName == null ? "MYSQL-HOST" : mysqlHostSecretName.trim();
        this.mysqlUsernameSecretName = mysqlUsernameSecretName == null ? "MYSQL-USERNAME" : mysqlUsernameSecretName.trim();
        this.mysqlPasswordSecretName = mysqlPasswordSecretName == null ? "MYSQL-PASSWORD" : mysqlPasswordSecretName.trim();
        this.mysqlPortSecretName = mysqlPortSecretName == null ? "MYSQL-PORT" : mysqlPortSecretName.trim();
        if (!this.keyVaultUrl.isBlank()) {
            this.secretClient = new SecretClientBuilder()
                    .vaultUrl(this.keyVaultUrl)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
        } else {
            this.secretClient = null;
        }
    }

    public String getApiKey() {
        return getSecretWithCache(
                secretName,
                "OPENAI_API_KEY",
                cachedSecret,
                cacheExpiresAt,
                (value, exp) -> {
                    cachedSecret = value;
                    cacheExpiresAt = exp;
                }
        );
    }

    public String getEndpoint() {
        return getSecretWithCache(
                endpointSecretName,
                "OPENAI_ENDPOINT",
                cachedEndpoint,
                endpointCacheExpiresAt,
                (value, exp) -> {
                    cachedEndpoint = value;
                    endpointCacheExpiresAt = exp;
                }
        );
    }

    public String getOcrKey() {
        return getSecretWithCache(
                ocrSecretName,
                "AZURE_OCR_API_KEY",
                cachedOcrKey,
                ocrKeyCacheExpiresAt,
                (value, exp) -> {
                    cachedOcrKey = value;
                    ocrKeyCacheExpiresAt = exp;
                }
        );
    }

    public String getOcrEndpoint() {
        return getSecretWithCache(
                ocrEndpointSecretName,
                "AZURE_OCR_ENDPOINT",
                cachedOcrEndpoint,
                ocrEndpointCacheExpiresAt,
                (value, exp) -> {
                    cachedOcrEndpoint = value;
                    ocrEndpointCacheExpiresAt = exp;
                }
        );
    }

    public String getWechatAppId() {
        return getSecretWithCache(
                wechatAppIdSecretName,
                "WECHAT_APP_ID",
                cachedWechatAppId,
                wechatAppIdCacheExpiresAt,
                (value, exp) -> {
                    cachedWechatAppId = value;
                    wechatAppIdCacheExpiresAt = exp;
                }
        );
    }

    public String getWechatAppSecret() {
        return getSecretWithCache(
                wechatAppSecretSecretName,
                "WECHAT_APP_SECRET",
                cachedWechatAppSecret,
                wechatAppSecretCacheExpiresAt,
                (value, exp) -> {
                    cachedWechatAppSecret = value;
                    wechatAppSecretCacheExpiresAt = exp;
                }
        );
    }

    public String getMysqlDb() {
        return getSecretWithCache(
                mysqlDbSecretName,
                "MYSQL_DB",
                cachedMysqlDb,
                mysqlDbCacheExpiresAt,
                (value, exp) -> {
                    cachedMysqlDb = value;
                    mysqlDbCacheExpiresAt = exp;
                }
        );
    }

    public String getMysqlHost() {
        return getSecretWithCache(
                mysqlHostSecretName,
                "MYSQL_HOST",
                cachedMysqlHost,
                mysqlHostCacheExpiresAt,
                (value, exp) -> {
                    cachedMysqlHost = value;
                    mysqlHostCacheExpiresAt = exp;
                }
        );
    }

    public String getMysqlUsername() {
        return getSecretWithCache(
                mysqlUsernameSecretName,
                "MYSQL_USERNAME",
                cachedMysqlUsername,
                mysqlUsernameCacheExpiresAt,
                (value, exp) -> {
                    cachedMysqlUsername = value;
                    mysqlUsernameCacheExpiresAt = exp;
                }
        );
    }

    public String getMysqlPassword() {
        return getSecretWithCache(
                mysqlPasswordSecretName,
                "MYSQL_PASSWORD",
                cachedMysqlPassword,
                mysqlPasswordCacheExpiresAt,
                (value, exp) -> {
                    cachedMysqlPassword = value;
                    mysqlPasswordCacheExpiresAt = exp;
                }
        );
    }

    public String getMysqlPort() {
        return getSecretWithCache(
                mysqlPortSecretName,
                "MYSQL_PORT",
                cachedMysqlPort,
                mysqlPortCacheExpiresAt,
                (value, exp) -> {
                    cachedMysqlPort = value;
                    mysqlPortCacheExpiresAt = exp;
                }
        );
    }

    private String getSecretWithCache(String secretName, String envName,
                                      String cachedValue, Instant cacheExpire,
                                      CacheSetter setter) {
        Instant now = Instant.now();
        if (cachedValue != null && cacheExpire != null && now.isBefore(cacheExpire)) {
            return cachedValue;
        }

        if (secretClient == null) {
            String envValue = System.getenv(envName);
            if (envValue == null || envValue.isBlank()) {
                return null;
            }
            setter.set(envValue.trim(), now.plusSeconds(300));
            return envValue.trim();
        }

        try {
            KeyVaultSecret secret = secretClient.getSecret(secretName);
            setter.set(secret.getValue(), now.plusSeconds(300));
            return secret.getValue();
        } catch (Exception ignored) {
            String envValue = System.getenv(envName);
            if (envValue == null || envValue.isBlank()) {
                throw ignored;
            }
            setter.set(envValue.trim(), now.plusSeconds(300));
            return envValue.trim();
        }
    }

    @FunctionalInterface
    private interface CacheSetter {
        void set(String value, Instant expiresAt);
    }
}
