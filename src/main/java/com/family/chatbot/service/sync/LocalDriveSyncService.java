package com.family.chatbot.service.sync;

import com.family.chatbot.config.StorageProperties;
import com.family.chatbot.service.FileEncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Service that synchronizes files directly to:
 * 1. Local drive directory (e.g. ./data/drive-sync/chatbot_documents/{UserName}/)
 * 2. Option B (100% Free): Google Apps Script Webhook directly into hrudaypreetham10@gmail.com Google Drive Cloud!
 *
 * Ensures the Admin has an organized view per family member both locally and in Google Drive cloud.
 */
@Service
public class LocalDriveSyncService implements DriveSyncService {

    private static final Logger log = LoggerFactory.getLogger(LocalDriveSyncService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final StorageProperties properties;
    private final ObjectMapper objectMapper;
    private final FileEncryptionService encryptionService;
    private final OkHttpClient httpClient;

    public LocalDriveSyncService(StorageProperties properties) {
        this(properties, new ObjectMapper(), null);
    }

    public LocalDriveSyncService(StorageProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, null);
    }

    @Autowired
    public LocalDriveSyncService(StorageProperties properties,
                                 @Autowired(required = false) ObjectMapper objectMapper,
                                 @Autowired(required = false) FileEncryptionService encryptionService) {
        this.properties = properties;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
        this.encryptionService = encryptionService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    @PostConstruct
    public void init() {
        if (properties.isEnableDriveSync()) {
            try {
                Path drivePath = properties.getDriveSyncPath().resolve("chatbot_documents");
                Files.createDirectories(drivePath);
                Path rootDrivePath = java.nio.file.Paths.get("./drive/chatbot_documents");
                Files.createDirectories(rootDrivePath);
                log.info("Drive Sync root directories initialized at: {} and {}", drivePath, rootDrivePath);
            } catch (IOException e) {
                log.error("Failed to initialize drive sync directory: {}", e.getMessage());
            }
        }
    }

    @Override
    public Path syncFile(Path sourceFile, String category, String folderName) throws IOException {
        if (!properties.isEnableDriveSync() || sourceFile == null || !Files.exists(sourceFile)) {
            return null;
        }

        String memberFolder = (folderName != null && !folderName.isBlank())
                ? sanitizeFolder(folderName)
                : "general";

        // Read raw data from storage and decrypt if encrypted so Google Drive has readable documents
        byte[] rawBytes = Files.readAllBytes(sourceFile);
        byte[] plainBytes = (encryptionService != null) ? encryptionService.decrypt(rawBytes) : rawBytes;

        // 1. Save to local drive mirror under drive/chatbot_documents/{UserName}/
        Path targetMemberDir = properties.getDriveSyncPath().resolve("chatbot_documents").resolve(memberFolder);
        Files.createDirectories(targetMemberDir);

        Path targetFile = targetMemberDir.resolve(sourceFile.getFileName());
        Files.write(targetFile, plainBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        log.info("Synced {} to local drive folder: drive/chatbot_documents/{}/{}",
                sourceFile.getFileName(), memberFolder, targetFile.getFileName());

        // 2. Option B: Cloud Sync to hrudaypreetham10@gmail.com Google Drive via Google Apps Script
        String gasUrl = properties.getAppsScriptWebhookUrl();
        if (gasUrl != null && !gasUrl.isBlank()) {
            uploadToGoogleAppsScript(plainBytes, sourceFile.getFileName().toString(), memberFolder, category);
        }

        return targetFile;
    }

    /**
     * Uploads the file directly to Google Apps Script Webhook running in hrudaypreetham10@gmail.com's Drive.
     * 100% Free, Zero GCP Console billing.
     */
    public boolean uploadToGoogleAppsScript(byte[] plainBytes, String fileName, String memberFolder, String category) {
        String gasUrl = properties.getAppsScriptWebhookUrl();
        if (gasUrl == null || gasUrl.isBlank()) {
            return false;
        }

        try {
            String base64Data = Base64.getEncoder().encodeToString(plainBytes);
            String mimeType = probeMimeType(fileName);

            Map<String, Object> payload = Map.of(
                    "secret", properties.getAppsScriptSecret() != null ? properties.getAppsScriptSecret() : "",
                    "userName", memberFolder,
                    "fileName", fileName,
                    "category", (category != null) ? category : "documents",
                    "mimeType", mimeType,
                    "fileBase64", base64Data
            );

            String jsonBody = objectMapper.writeValueAsString(payload);
            RequestBody requestBody = RequestBody.create(jsonBody, JSON);

            Request request = new Request.Builder()
                    .url(gasUrl)
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String respStr = response.body().string();
                    log.info("☁️ [Google Apps Script] Successfully synced {} to Cloud Google Drive (chatbot_documents/{}): {}",
                            fileName, memberFolder, respStr);
                    return true;
                } else {
                    log.warn("⚠️ [Google Apps Script] Cloud upload returned HTTP {}: {}",
                            response.code(), response.message());
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ [Google Apps Script] Cloud upload error for {}: {}. Offline local sync copy preserved.",
                    fileName, e.getMessage());
        }
        return false;
    }

    @Override
    public void createMemberFolder(String folderName) {
        if (!properties.isEnableDriveSync() || folderName == null || folderName.isBlank()) return;
        try {
            String safe = sanitizeFolder(folderName);

            // 1. Local drive directory creation
            Path userDir = properties.getDriveSyncPath().resolve("chatbot_documents").resolve(safe);
            Files.createDirectories(userDir);

            // Also create in root drive directory for convenience
            Path rootDriveDir = java.nio.file.Paths.get("./drive/chatbot_documents").resolve(safe);
            Files.createDirectories(rootDriveDir);

            log.info("Initialized local Google Drive member directories at: {} and {}", userDir, rootDriveDir);

            // 2. Cloud Google Drive folder creation via Apps Script in hrudaypreetham10@gmail.com Drive
            String gasUrl = properties.getAppsScriptWebhookUrl();
            if (gasUrl != null && !gasUrl.isBlank()) {
                new Thread(() -> {
                    try {
                        byte[] initBytes = ("Vault folder initialized for " + safe + " at " + java.time.LocalDateTime.now())
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        uploadToGoogleAppsScript(initBytes, ".vault_init.txt", safe, "system");
                        log.info("☁️ [Google Drive] Pre-created cloud folder 'chatbot_documents/{}' in hrudaypreetham10@gmail.com Drive", safe);
                    } catch (Exception ex) {
                        log.warn("Non-fatal: Cloud folder pre-creation for {} error: {}", safe, ex.getMessage());
                    }
                }, "DriveFolderInit-" + safe).start();
            }
        } catch (IOException e) {
            log.warn("Could not pre-create member folder {}: {}", folderName, e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return properties.isEnableDriveSync() && Files.exists(properties.getDriveSyncPath());
    }

    @Override
    public List<Path> listUserDriveFiles(String folderName) {
        if (!properties.isEnableDriveSync() || folderName == null || folderName.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Path userDir = properties.getDriveSyncPath().resolve("chatbot_documents").resolve(sanitizeFolder(folderName));
            if (!Files.exists(userDir) || !Files.isDirectory(userDir)) {
                return Collections.emptyList();
            }
            try (Stream<Path> stream = Files.list(userDir)) {
                return stream.filter(Files::isRegularFile).sorted().toList();
            }
        } catch (Exception e) {
            log.warn("Could not list files in drive folder {}: {}", folderName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String sanitizeFolder(String folder) {
        return folder.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String probeMimeType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }
}
