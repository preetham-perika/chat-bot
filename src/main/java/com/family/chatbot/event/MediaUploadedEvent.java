package com.family.chatbot.event;

import java.nio.file.Path;

/**
 * Domain event published when a media document or photo is saved locally.
 * Decouples file storage from external Google Drive sync so that external failures
 * never affect WhatsApp messaging or vault operations.
 */
public record MediaUploadedEvent(
        String docId,
        Path filePath,
        String category,
        String folderName,
        String senderPhone
) {}
