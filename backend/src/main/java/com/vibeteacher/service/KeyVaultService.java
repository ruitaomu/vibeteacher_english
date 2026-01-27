package com.vibeteacher.service;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class KeyVaultService {
    private final String keyVaultUrl;
    private final String secretName;
    private final String endpointSecretName;
    private final SecretClient secretClient;
    private volatile String cachedSecret;
    private volatile Instant cacheExpiresAt;
    private volatile String cachedEndpoint;
    private volatile Instant endpointCacheExpiresAt;

    public KeyVaultService(
            @Value("${keyvault.url:}") String keyVaultUrl,
            @Value("${keyvault.secretName:AZUREOPENAI-APIKEY}") String secretName,
            @Value("${keyvault.endpointSecretName:AZUREOPENAI-ENDPOINT}") String endpointSecretName
    ) {
        this.keyVaultUrl = keyVaultUrl == null ? "" : keyVaultUrl.trim();
        this.secretName = secretName == null ? "AZUREOPENAI-APIKEY" : secretName.trim();
        this.endpointSecretName = endpointSecretName == null ? "AZUREOPENAI-ENDPOINT" : endpointSecretName.trim();
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
        if (secretClient == null) {
            return null;
        }
        Instant now = Instant.now();
        if (cachedSecret != null && cacheExpiresAt != null && now.isBefore(cacheExpiresAt)) {
            return cachedSecret;
        }
        KeyVaultSecret secret = secretClient.getSecret(secretName);
        cachedSecret = secret.getValue();
        cacheExpiresAt = now.plusSeconds(300);
        return cachedSecret;
    }

    public String getEndpoint() {
        if (secretClient == null) {
            return null;
        }
        Instant now = Instant.now();
        if (cachedEndpoint != null && endpointCacheExpiresAt != null && now.isBefore(endpointCacheExpiresAt)) {
            return cachedEndpoint;
        }
        KeyVaultSecret secret = secretClient.getSecret(endpointSecretName);
        cachedEndpoint = secret.getValue();
        endpointCacheExpiresAt = now.plusSeconds(300);
        return cachedEndpoint;
    }
}
