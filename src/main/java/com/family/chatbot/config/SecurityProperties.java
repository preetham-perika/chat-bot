package com.family.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for REST API security and AES-256 file encryption at rest.
 */
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    /**
     * Master API Key required in the 'X-API-KEY' HTTP header for all /api/** endpoints.
     */
    private String apiKey = "chat-vault-secret-admin-key-2026";

    /**
     * Whether API key verification is strictly enforced on REST endpoints.
     */
    private boolean enableApiKeyAuth = true;

    /**
     * Secret key or passphrase used to derive the 256-bit AES encryption key.
     */
    private String encryptionKey = "ChatBot-Vault-2026-Secret-Key-AES256";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isEnableApiKeyAuth() {
        return enableApiKeyAuth;
    }

    public void setEnableApiKeyAuth(boolean enableApiKeyAuth) {
        this.enableApiKeyAuth = enableApiKeyAuth;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}
