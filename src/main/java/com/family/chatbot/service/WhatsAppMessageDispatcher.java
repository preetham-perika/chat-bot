package com.family.chatbot.service;

import com.family.chatbot.config.WhatsAppProperties;
import com.family.chatbot.model.*;
import com.family.chatbot.service.notification.AdminNotificationService;
import com.family.chatbot.service.provider.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Main coordinator for processing incoming WhatsApp messages.
 * Enforces strict whitelist authorization, logs all audit events,
 * and routes messages to media storage or command processing.
 */
@Service
public class WhatsAppMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessageDispatcher.class);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    private final WhitelistService whitelistService;
    private final WhatsAppProperties properties;
    private final WhatsAppProvider whatsAppProvider;
    private final DocumentStorageService storageService;
    private final BotCommandService commandService;
    private final AuditLogService auditLogService;
    private final AdminNotificationService adminNotificationService;

    public WhatsAppMessageDispatcher(WhitelistService whitelistService,
                                     WhatsAppProperties properties,
                                     WhatsAppProvider whatsAppProvider,
                                     DocumentStorageService storageService,
                                     BotCommandService commandService,
                                     AuditLogService auditLogService,
                                     AdminNotificationService adminNotificationService) {
        this.whitelistService = whitelistService;
        this.properties = properties;
        this.whatsAppProvider = whatsAppProvider;
        this.storageService = storageService;
        this.commandService = commandService;
        this.auditLogService = auditLogService;
        this.adminNotificationService = adminNotificationService;
    }

    /**
     * Dispatch an incoming WhatsApp message.
     */
    public void dispatch(IncomingMessage message) {
        String senderPhone = message.getSenderPhone();
        UserRole role = whitelistService.getRole(senderPhone);

        // 1. Strict Whitelist Verification (Data Privacy & Access Control)
        if (role == UserRole.UNAUTHORIZED) {
            auditLogService.record(
                    AuditEventType.UNAUTHORIZED_ACCESS,
                    AuditStatus.DENIED,
                    senderPhone,
                    role.name(),
                    "MESSAGE_DISPATCH",
                    null,
                    "Access attempted by non-whitelisted number. Request blocked."
            );

            if (properties.isNotifyUnauthorized()) {
                whatsAppProvider.sendTextMessage(senderPhone, properties.getUnauthorizedMessage());
            }
            return;
        }

        // 2. Handle Incoming Media (Document, Image, Audio)
        if (message.isHasMedia()) {
            handleMediaMessage(message, role);
            return;
        }

        // 3. Handle Text Command or Menu Navigation
        String userText = message.getText();
        String botReply = commandService.processCommand(userText, senderPhone, message.getSenderName());
        whatsAppProvider.sendTextMessage(senderPhone, botReply);
    }

    private void handleMediaMessage(IncomingMessage message, UserRole role) {
        String senderPhone = message.getSenderPhone();
        try {
            byte[] fileBytes = message.getMediaData();

            // If not pre-downloaded, download using provider
            if (fileBytes == null || fileBytes.length == 0) {
                fileBytes = whatsAppProvider.downloadMedia(message.getMediaId());
            }

            if (fileBytes == null || fileBytes.length == 0) {
                auditLogService.record(
                        AuditEventType.FILE_UPLOAD,
                        AuditStatus.ERROR,
                        senderPhone,
                        role.name(),
                        "DOWNLOAD_MEDIA",
                        null,
                        "Failed to download media attachment from WhatsApp provider"
                );

                whatsAppProvider.sendTextMessage(senderPhone,
                        "❌ Failed to download the attached file from WhatsApp. Please try sending it again.");
                return;
            }

            DocumentType type = message.getMediaType() != null ? message.getMediaType() : DocumentType.DOCUMENT;
            String caption = message.getText(); // WhatsApp puts caption in text
            String folderName = whitelistService.getUserFolderName(senderPhone);
            String displayName = whitelistService.getUserDisplayName(senderPhone);

            // =========================================================================
            // DUPLICATE UPLOAD DETECTION (SHA-256 Cryptographic Checksum)
            // If user uploads the exact same file twice:
            // 1. Prevent duplicate disk/drive storage.
            // 2. Prevent spamming Admin with repeated approval notifications.
            // 3. Inform user of existing document status and provide existing Doc ID.
            // =========================================================================
            Optional<DocumentItem> duplicateOpt = storageService.findDuplicate(fileBytes, senderPhone);
            if (duplicateOpt.isPresent()) {
                DocumentItem existing = duplicateOpt.get();
                auditLogService.record(
                        AuditEventType.FILE_UPLOAD,
                        AuditStatus.SUCCESS,
                        senderPhone,
                        role.name(),
                        "DUPLICATE_UPLOAD_PREVENTED",
                        existing.getDocId(),
                        "Identical file upload detected (SHA-256: " + existing.getFileHash() + "). Reusing existing " + existing.getDocId()
                );

                String duplicateReply = switch (existing.getStatus()) {
                    case APPROVED -> String.format("""
                            ⚠️ *Duplicate Document Detected!*
                            ━━━━━━━━━━━━━━━━━━━━
                            You have already uploaded this exact file previously:
                            🆔 *Doc ID:* %s
                            📁 *File Name:* %s
                            🏷️ *Category:* %s
                            ✅ *Status:* Already Approved & Synced to Drive
                            📂 *Drive Path:* `drive/family_chatbot/%s/%s`
                            
                            💡 No duplicate copy was saved to conserve your drive storage.
                            Reply */download %s* anytime to download this file!
                            """, existing.getDocId(), existing.getOriginalFileName(), existing.getCategoryTag(),
                            folderName, existing.getOriginalFileName(), existing.getDocId());

                    case PENDING_APPROVAL -> String.format("""
                            ⚠️ *Duplicate Upload Notice*
                            ━━━━━━━━━━━━━━━━━━━━
                            You have already uploaded this exact document:
                            🆔 *Doc ID:* %s
                            📁 *File Name:* %s
                            🏷️ *Category:* %s
                            ⏳ *Status:* Currently Pending Master Admin Approval
                            
                            The Master Admin (+%s) was already notified. No duplicate upload is necessary!
                            """, existing.getDocId(), existing.getOriginalFileName(), existing.getCategoryTag(),
                            whitelistService.getAdminNumber());

                    case REJECTED -> String.format("""
                            ⚠️ *Duplicate Document Notice*
                            ━━━━━━━━━━━━━━━━━━━━
                            This exact file was previously rejected by the Master Admin.
                            🆔 *Doc ID:* %s
                            📝 *Rejection Reason:* %s
                            
                            Please take a fresh, clearer photo or updated document before re-uploading.
                            """, existing.getDocId(),
                            existing.getRejectionReason() != null ? existing.getRejectionReason() : "Details could not be verified");
                };

                whatsAppProvider.sendTextMessage(senderPhone, duplicateReply);
                return;
            }

            boolean isAdmin = (role == UserRole.ADMIN);
            String targetPhone = senderPhone;
            String targetName = displayName;
            String targetFolder = folderName;

            if (isAdmin && caption != null) {
                for (com.family.chatbot.model.FamilyMember m : whitelistService.getAllActiveMembers()) {
                    if (m.getRole() != UserRole.ADMIN &&
                       (caption.toLowerCase().contains("@" + m.getName().toLowerCase()) ||
                        caption.toLowerCase().contains("for " + m.getName().toLowerCase()) ||
                        caption.contains(m.getPhoneNumber()))) {
                        targetPhone = m.getPhoneNumber();
                        targetName = m.getName();
                        targetFolder = m.getFolderName();
                        break;
                    }
                }
            }

            DocumentItem savedItem = storageService.storeMediaFile(
                    fileBytes,
                    message.getOriginalFileName(),
                    message.getMimeType(),
                    type,
                    caption,
                    targetPhone,
                    targetName,
                    targetFolder,
                    isAdmin
            );

            // Audit Log successful upload
            auditLogService.record(
                    AuditEventType.FILE_UPLOAD,
                    AuditStatus.SUCCESS,
                    senderPhone,
                    role.name(),
                    "STORE_FILE",
                    savedItem.getDocId(),
                    String.format("Uploaded %s (%s, %d bytes). Status: %s. Category: %s",
                            savedItem.getOriginalFileName(), savedItem.getType(),
                            savedItem.getFileSizeBytes(), savedItem.getStatus(), savedItem.getCategoryTag())
            );

            if (isAdmin) {
                // Send confirmation for Admin upload (auto-approved and synced)
                String confirmation = String.format("""
                        ✅ *File Saved & Synced Successfully! (Admin)*
                        ━━━━━━━━━━━━━━━━━━━━
                        🆔 *Document ID:* %s
                        📁 *File:* %s
                        🏷️ *Category:* %s
                        📦 *Size:* %s
                        📂 *Drive Vault:* `drive/family_chatbot/%s/`
                        📅 *Saved:* %s
                        🔄 *Drive Synced:* %s
                        %s
                        💡 Reply */get %s* anytime to retrieve this file details.
                        """,
                        savedItem.getDocId(),
                        savedItem.getOriginalFileName(),
                        savedItem.getCategoryTag(),
                        formatSize(savedItem.getFileSizeBytes()),
                        targetFolder,
                        savedItem.getCreatedAt().format(DISPLAY_DATE),
                        savedItem.isSyncedToDrive() ? "Yes" : "Local only",
                        (caption != null && !caption.isBlank()) ? "📝 *Caption:* " + caption + "\n" : "",
                        savedItem.getDocId()
                );
                whatsAppProvider.sendTextMessage(senderPhone, confirmation);
            } else {
                // Member upload: Auto-synced to Drive! Notify member and alert Admin
                String memberMsg = String.format("""
                        ✅ *Document Saved & Synced Successfully to Google Drive!*
                        ━━━━━━━━━━━━━━━━━━━━
                        🆔 *Doc ID:* %s
                        🏷️ *Category:* %s
                        📁 *File:* %s
                        📦 *Size:* %s
                        📂 *Drive Vault:* `drive/family_chatbot/%s/`
                        %s
                        💡 Reply */download %s* anytime to retrieve this file!
                        """,
                        savedItem.getDocId(),
                        savedItem.getCategoryTag(),
                        savedItem.getOriginalFileName(),
                        formatSize(savedItem.getFileSizeBytes()),
                        folderName,
                        (caption != null && !caption.isBlank()) ? "📝 *Caption:* " + caption + "\n" : "",
                        savedItem.getDocId()
                );
                whatsAppProvider.sendTextMessage(senderPhone, memberMsg);
                adminNotificationService.notifyAdminDocumentUploaded(savedItem, displayName, folderName);
            }

        } catch (Exception e) {
            log.error("Error storing media file from {}: {}", senderPhone, e.getMessage(), e);

            auditLogService.record(
                    AuditEventType.FILE_UPLOAD,
                    AuditStatus.ERROR,
                    senderPhone,
                    role.name(),
                    "STORE_FILE_ERROR",
                    null,
                    "Exception saving file: " + e.getMessage()
            );

            whatsAppProvider.sendTextMessage(senderPhone,
                    "❌ Error saving your file: " + e.getMessage());
        }
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
