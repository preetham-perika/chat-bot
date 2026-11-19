package com.family.chatbot.controller;

import com.family.chatbot.config.WhatsAppProperties;
import com.family.chatbot.model.DocumentType;
import com.family.chatbot.model.IncomingMessage;
import com.family.chatbot.service.WhatsAppMessageDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * REST Controller handling incoming WhatsApp Webhooks:
 * - Meta WhatsApp Cloud API verification (GET /webhook)
 * - Meta WhatsApp Cloud API inbound messages (POST /webhook)
 * - Twilio WhatsApp inbound messages (POST /webhook/twilio)
 */
@RestController
@RequestMapping("/webhook")
@Tag(name = "WhatsApp Webhook Listeners", description = "Inbound webhook listeners for Meta WhatsApp Cloud API and Twilio")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppProperties properties;
    private final WhatsAppMessageDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookController(WhatsAppProperties properties,
                                     WhatsAppMessageDispatcher dispatcher,
                                     ObjectMapper objectMapper) {
        this.properties = properties;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    /**
     * Webhook Verification Endpoint for Meta WhatsApp Cloud API.
     * When configuring the webhook in Meta Developer Dashboard, Meta sends a GET request
     * with hub.mode, hub.verify_token, and hub.challenge.
     */
    @Operation(summary = "Meta Webhook Verification (GET)", description = "Handles challenge handshake from Meta WhatsApp Cloud API developer portal during setup")
    @GetMapping(value = {"", "/meta"})
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        log.info("Received Meta Webhook Verification Request: mode={}, token={}", mode, token);

        if ("subscribe".equalsIgnoreCase(mode) && properties.getVerifyToken().equals(token)) {
            log.info("✅ Meta Webhook verification succeeded!");
            return ResponseEntity.ok(challenge);
        }

        log.warn("❌ Meta Webhook verification failed. Token mismatch or invalid mode.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification token mismatch");
    }

    /**
     * Inbound Message Endpoint for Meta WhatsApp Cloud API.
     * Meta sends HTTP POST with JSON payload containing user messages or media files.
     */
    @Operation(summary = "Meta Webhook Inbound Message (POST)", description = "Receives incoming WhatsApp messages, images, documents, and notifications from Meta Cloud API")
    @PostMapping(value = {"", "/meta"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleMetaWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        // Validate HMAC-SHA256 signature if App Secret is configured
        if (properties.getAppSecret() != null && !properties.getAppSecret().isBlank()) {
            if (!isValidSignature(rawPayload, signature, properties.getAppSecret())) {
                log.warn("❌ Rejected webhook payload: Invalid X-Hub-Signature-256");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature");
            }
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);

            // Check if entry contains messages
            if (root.has("entry")) {
                for (JsonNode entry : root.get("entry")) {
                    if (entry.has("changes")) {
                        for (JsonNode change : entry.get("changes")) {
                            JsonNode value = change.path("value");
                            if (value.has("messages")) {
                                for (JsonNode msgNode : value.get("messages")) {
                                    IncomingMessage incoming = parseMetaMessage(msgNode, value);
                                    if (incoming != null) {
                                        // Dispatch asynchronously / process message
                                        dispatcher.dispatch(incoming);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Meta requires an immediate 200 OK response to acknowledge receipt
            return ResponseEntity.ok("EVENT_RECEIVED");

        } catch (Exception e) {
            log.error("Error processing Meta WhatsApp webhook payload: {}", e.getMessage(), e);
            return ResponseEntity.ok("ERROR_PROCESSED");
        }
    }

    /**
     * Inbound Message Endpoint for Twilio WhatsApp Webhook.
     */
    @Operation(summary = "Twilio Webhook Inbound Message (POST)", description = "Receives incoming WhatsApp messages from Twilio")
    @PostMapping(value = "/twilio", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> handleTwilioWebhook(@RequestParam Map<String, String> formData) {
        try {
            String from = formData.get("From"); // e.g. "whatsapp:+919876543210"
            String body = formData.get("Body");
            String senderName = formData.getOrDefault("ProfileName", "Family Member");
            String messageId = formData.getOrDefault("MessageSid", "");

            IncomingMessage incoming = new IncomingMessage(from, senderName, messageId, body);

            // Check if Twilio sent media attachments
            int numMedia = 0;
            try {
                numMedia = Integer.parseInt(formData.getOrDefault("NumMedia", "0"));
            } catch (NumberFormatException ignored) {}

            if (numMedia > 0) {
                incoming.setHasMedia(true);
                incoming.setMediaId(formData.get("MediaUrl0"));
                incoming.setMimeType(formData.get("MediaContentType0"));

                String contentType = incoming.getMimeType() != null ? incoming.getMimeType().toLowerCase() : "";
                if (contentType.startsWith("image/")) {
                    incoming.setMediaType(DocumentType.IMAGE);
                    incoming.setOriginalFileName("photo.jpg");
                } else if (contentType.startsWith("audio/")) {
                    incoming.setMediaType(DocumentType.AUDIO);
                    incoming.setOriginalFileName("voice.ogg");
                } else {
                    incoming.setMediaType(DocumentType.DOCUMENT);
                    incoming.setOriginalFileName("document.pdf");
                }
            }

            dispatcher.dispatch(incoming);
            return ResponseEntity.ok("<Response></Response>");

        } catch (Exception e) {
            log.error("Error processing Twilio WhatsApp webhook: {}", e.getMessage(), e);
            return ResponseEntity.ok("<Response></Response>");
        }
    }

    private boolean isValidSignature(String payload, String signatureHeader, String appSecret) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            String expectedHash = signatureHeader.substring("sha256=".length()).trim();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] calculated = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedHex = HexFormat.of().formatHex(calculated);
            return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
                                         calculatedHex.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error verifying webhook HMAC signature: {}", e.getMessage());
            return false;
        }
    }

    private IncomingMessage parseMetaMessage(JsonNode msgNode, JsonNode valueNode) {
        String from = msgNode.path("from").asText();
        String messageId = msgNode.path("id").asText();
        String type = msgNode.path("type").asText();

        // Extract sender name from contacts if available
        String senderName = "Family Member";
        if (valueNode.has("contacts")) {
            for (JsonNode contact : valueNode.get("contacts")) {
                if (contact.path("wa_id").asText().equals(from)) {
                    senderName = contact.path("profile").path("name").asText(senderName);
                    break;
                }
            }
        }

        IncomingMessage message = new IncomingMessage(from, senderName, messageId, null);

        switch (type.toLowerCase()) {
            case "text" -> {
                message.setText(msgNode.path("text").path("body").asText());
            }
            case "image" -> {
                message.setHasMedia(true);
                message.setMediaType(DocumentType.IMAGE);
                JsonNode img = msgNode.path("image");
                message.setMediaId(img.path("id").asText());
                message.setMimeType(img.path("mime_type").asText("image/jpeg"));
                message.setText(img.path("caption").asText(null));
                message.setOriginalFileName("image_" + messageId + ".jpg");
            }
            case "document" -> {
                message.setHasMedia(true);
                message.setMediaType(DocumentType.DOCUMENT);
                JsonNode doc = msgNode.path("document");
                message.setMediaId(doc.path("id").asText());
                message.setMimeType(doc.path("mime_type").asText("application/pdf"));
                message.setOriginalFileName(doc.path("filename").asText("document.pdf"));
                message.setText(doc.path("caption").asText(null));
            }
            case "audio" -> {
                message.setHasMedia(true);
                message.setMediaType(DocumentType.AUDIO);
                JsonNode aud = msgNode.path("audio");
                message.setMediaId(aud.path("id").asText());
                message.setMimeType(aud.path("mime_type").asText("audio/ogg"));
                message.setOriginalFileName("audio_" + messageId + ".ogg");
            }
            default -> {
                log.info("Ignoring unsupported WhatsApp message type: {}", type);
                return null;
            }
        }

        return message;
    }
}
