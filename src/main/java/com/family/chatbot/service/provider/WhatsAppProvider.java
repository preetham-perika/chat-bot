package com.family.chatbot.service.provider;

/**
 * Common abstraction for WhatsApp providers (Meta WhatsApp Cloud API, Twilio, Console).
 */
public interface WhatsAppProvider {

    /**
     * Send a plain text message to the recipient's phone number.
     *
     * @param recipientPhone normalized phone number with country code (e.g., "919876543210")
     * @param messageText    message content
     */
    void sendTextMessage(String recipientPhone, String messageText);

    /**
     * Send a document or image file directly to the recipient's WhatsApp.
     *
     * @param recipientPhone normalized phone number with country code
     * @param file           stored file on disk
     * @param fileName       display filename (e.g. "Aadhaar_Card.pdf")
     * @param caption        caption for the document
     */
    void sendDocumentMessage(String recipientPhone, java.io.File file, String fileName, String caption);

    /**
     * Download media binary (image, document, audio) using the provider-specific media identifier.
     *
     * @param mediaIdentifier Meta media ID or Twilio MediaUrl
     * @return binary byte array of the downloaded media
     */
    byte[] downloadMedia(String mediaIdentifier);

    /**
     * Name of the provider (e.g., "meta", "twilio", "console").
     */
    String getProviderName();
}
