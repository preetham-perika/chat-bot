package com.family.chatbot;

import com.family.chatbot.config.StorageProperties;
import com.family.chatbot.service.sync.LocalDriveSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LocalDriveSyncServiceTest {

    @TempDir
    Path tempDir;

    private HttpServer testHttpServer;
    private StorageProperties storageProperties;
    private LocalDriveSyncService syncService;
    private final AtomicReference<String> receivedRequestBody = new AtomicReference<>("");

    @BeforeEach
    void setUp() throws IOException {
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/macros/s/exec", exchange -> {
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            receivedRequestBody.set(new String(bytes, StandardCharsets.UTF_8));
            byte[] response = "{\"status\":\"SUCCESS\",\"fileId\":\"gdrive-12345\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        testHttpServer.start();

        storageProperties = new StorageProperties();
        storageProperties.setBaseDir(tempDir.resolve("storage").toString());
        storageProperties.setDriveSyncDir(tempDir.resolve("drive-sync").toString());
        storageProperties.setEnableDriveSync(true);

        syncService = new LocalDriveSyncService(storageProperties, new ObjectMapper(), null);
        syncService.init();
    }

    @AfterEach
    void tearDown() {
        if (testHttpServer != null) {
            testHttpServer.stop(0);
        }
    }

    @Test
    void testSyncFile_LocalCopy_Successful() throws IOException {
        Path testFile = tempDir.resolve("sample.pdf");
        Files.writeString(testFile, "Sample Aadhaar Document Content");

        Path synced = syncService.syncFile(testFile, "documents", "Dad");
        assertNotNull(synced);
        assertTrue(Files.exists(synced));
        assertTrue(synced.toString().contains("family_chatbot/Dad/sample.pdf"));
    }

    @Test
    void testSyncFile_OptionB_GoogleAppsScriptCloudUpload() throws Exception {
        int port = testHttpServer.getAddress().getPort();
        String webhookUrl = "http://localhost:" + port + "/macros/s/exec";
        storageProperties.setAppsScriptWebhookUrl(webhookUrl);
        storageProperties.setAppsScriptSecret("family-vault-gas-secret-2026");

        Path testFile = tempDir.resolve("Sister_PAN.pdf");
        Files.writeString(testFile, "PAN Card bytes of Sister");

        Path synced = syncService.syncFile(testFile, "documents", "Sister");
        assertNotNull(synced);
        assertTrue(Files.exists(synced));

        // Verify HTTP request was received by the mock Google Apps Script Webhook
        String body = receivedRequestBody.get();
        assertTrue(body.contains("Sister"));
        assertTrue(body.contains("family-vault-gas-secret-2026"));
        assertTrue(body.contains("Sister_PAN.pdf"));
    }
}
