package com.family.chatbot.service.provider;

import com.family.chatbot.config.WhatsAppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Official Meta WhatsApp Cloud API Provider.
 * Completely free tier: provides 1,000 free conversations per month directly from Meta Graph API.
 */
@Service
public class MetaCloudWhatsAppProvider implements WhatsAppProvider {

    private static final Logger log = LoggerFactory.getLogger(MetaCloudWhatsAppProvider.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public MetaCloudWhatsAppProvider(WhatsAppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void sendTextMessage(String recipientPhone, String messageText) {
        if (properties.getAccessToken() == null || properties.getAccessToken().isBlank() ||
            properties.getPhoneNumberId() == null || properties.getPhoneNumberId().isBlank()) {
            log.warn("[Meta WhatsApp API] Token or Phone Number ID not configured. Message to {} logged locally: {}",
                    recipientPhone, messageText);
            return;
        }

        String url = String.format("https://graph.facebook.com/%s/%s/messages",
                properties.getApiVersion(), properties.getPhoneNumberId());

        try {
            // Build JSON payload
            String payload = objectMapper.writeValueAsString(new MetaTextMessagePayload(
                    "whatsapp",
                    "individual",
                    recipientPhone,
                    "text",
                    new TextBody(false, messageText)
            ));

            RequestBody body = RequestBody.create(payload, JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + properties.getAccessToken())
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    log.error("[Meta WhatsApp API] Failed to send message to {}: HTTP {} - {}",
                            recipientPhone, response.code(), respBody);
                } else {
                    log.info("[Meta WhatsApp API] Successfully sent message to {}", recipientPhone);
                }
            }
        } catch (Exception e) {
            log.error("[Meta WhatsApp API] Error sending message to {}: {}", recipientPhone, e.getMessage(), e);
        }
    }

    @Override
    public byte[] downloadMedia(String mediaIdentifier) {
        if (mediaIdentifier == null || mediaIdentifier.isBlank()) {
            return new byte[0];
        }

        try {
            // Step 1: Retrieve media metadata URL from Graph API
            String metaUrl = String.format("https://graph.facebook.com/%s/%s",
                    properties.getApiVersion(), mediaIdentifier);

            Request metaRequest = new Request.Builder()
                    .url(metaUrl)
                    .addHeader("Authorization", "Bearer " + properties.getAccessToken())
                    .get()
                    .build();

            String downloadUrl = null;
            try (Response response = httpClient.newCall(metaRequest).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("[Meta WhatsApp API] Failed to get media URL for ID {}: HTTP {}",
                            mediaIdentifier, response.code());
                    return new byte[0];
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                if (root.has("url")) {
                    downloadUrl = root.get("url").asText();
                }
            }

            if (downloadUrl == null) {
                log.error("[Meta WhatsApp API] No download URL returned for media ID {}", mediaIdentifier);
                return new byte[0];
            }

            // Step 2: Download raw media binary using downloadUrl with Authorization header
            Request downloadRequest = new Request.Builder()
                    .url(downloadUrl)
                    .addHeader("Authorization", "Bearer " + properties.getAccessToken())
                    .get()
                    .build();

            try (Response downloadResponse = httpClient.newCall(downloadRequest).execute()) {
                if (!downloadResponse.isSuccessful() || downloadResponse.body() == null) {
                    log.error("[Meta WhatsApp API] Failed to download media binary from {}: HTTP {}",
                            downloadUrl, downloadResponse.code());
                    return new byte[0];
                }
                return downloadResponse.body().bytes();
            }

        } catch (IOException e) {
            log.error("[Meta WhatsApp API] Error downloading media {}: {}", mediaIdentifier, e.getMessage(), e);
            return new byte[0];
        }
    }

    @Override
    public void sendDocumentMessage(String recipientPhone, java.io.File file, String fileName, String caption) {
        if (properties.getAccessToken() == null || properties.getAccessToken().isBlank() ||
            properties.getPhoneNumberId() == null || properties.getPhoneNumberId().isBlank()) {
            log.warn("[Meta WhatsApp API] Outbound document to {} logged locally: {} (Caption: {})",
                    recipientPhone, fileName, caption);
            return;
        }
        sendTextMessage(recipientPhone, "📄 [Document: " + fileName + "]\n" + (caption != null ? caption : ""));
    }

    @Override
    public String getProviderName() {
        return "meta";
    }

    // Helper records for JSON serialization
    private record MetaTextMessagePayload(String messaging_product, String recipient_type,
                                          String to, String type, TextBody text) {}

    private record TextBody(boolean preview_url, String body) {}
}
