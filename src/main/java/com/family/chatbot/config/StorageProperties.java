package com.family.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /**
     * Local directory where uploaded documents, photos and notes are stored.
     */
    private String baseDir = "./data/storage";

    /**
     * Folder where synced files are mirrored (e.g. Google Drive local synced directory, Nextcloud, or backup drive).
     */
    private String driveSyncDir = "./data/drive-sync";

    /**
     * Enable dynamic drive mirroring/syncing.
     */
    private boolean enableDriveSync = true;

    /**
     * Maximum allowed file size in bytes (default 50MB).
     */
    private long maxFileSizeBytes = 50 * 1024 * 1024;

    /**
     * Google Apps Script Webhook URL for 100% free cloud Google Drive sync.
     * When configured, files are directly synced to the Admin's Google Drive in the cloud.
     */
    private String appsScriptWebhookUrl = "";

    /**
     * Dedicated Google Account for Google Drive Cloud Storage (Option B).
     */
    private String googleDriveEmail = "hrudaypreetham10@gmail.com";

    /**
     * Secret token to authenticate webhook requests between the chatbot and Google Apps Script.
     */
    private String appsScriptSecret = "family-vault-gas-secret-2026";

    public String getGoogleDriveEmail() {
        return googleDriveEmail;
    }

    public void setGoogleDriveEmail(String googleDriveEmail) {
        this.googleDriveEmail = googleDriveEmail;
    }

    public String getAppsScriptWebhookUrl() {
        return appsScriptWebhookUrl;
    }

    public void setAppsScriptWebhookUrl(String appsScriptWebhookUrl) {
        this.appsScriptWebhookUrl = appsScriptWebhookUrl;
    }

    public String getAppsScriptSecret() {
        return appsScriptSecret;
    }

    public void setAppsScriptSecret(String appsScriptSecret) {
        this.appsScriptSecret = appsScriptSecret;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getDriveSyncDir() {
        return driveSyncDir;
    }

    public void setDriveSyncDir(String driveSyncDir) {
        this.driveSyncDir = driveSyncDir;
    }

    public boolean isEnableDriveSync() {
        return enableDriveSync;
    }

    public void setEnableDriveSync(boolean enableDriveSync) {
        this.enableDriveSync = enableDriveSync;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public Path getBasePath() {
        return Paths.get(baseDir).toAbsolutePath().normalize();
    }

    public Path getDriveSyncPath() {
        return Paths.get(driveSyncDir).toAbsolutePath().normalize();
    }
}
