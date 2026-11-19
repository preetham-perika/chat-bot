package com.family.chatbot.service.notification;

import com.family.chatbot.model.DocumentItem;
import com.family.chatbot.service.WhitelistService;
import com.family.chatbot.service.provider.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates multi-channel approval notifications to the Master Admin (WhatsApp + Email)
 * and notifications back to the family member upon approval or rejection.
 */
@Service
public class AdminNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationService.class);

    private final WhatsAppProvider whatsAppProvider;
    private final WhitelistService whitelistService;
    private final MailNotificationService mailService;

    public AdminNotificationService(WhatsAppProvider whatsAppProvider,
                                    WhitelistService whitelistService,
                                    MailNotificationService mailService) {
        this.whatsAppProvider = whatsAppProvider;
        this.whitelistService = whitelistService;
        this.mailService = mailService;
    }

    /**
     * Notify Admin via both WhatsApp and Email when a family member uploads a document.
     */
    public void notifyAdminPendingApproval(DocumentItem doc, String memberName) {
        String adminPhone = whitelistService.getAdminNumber();

        // 1. WhatsApp notification with quick approval command
        if (adminPhone != null && !adminPhone.isBlank() && !adminPhone.equals(doc.getSenderPhoneNumber())) {
            String waMessage = String.format("""
                    🔔 *New Document Approval Request*
                    ━━━━━━━━━━━━━━━━━━━━
                    👤 *Member:* %s (+%s)
                    📁 *Doc ID:* %s
                    🏷️ *Category:* %s
                    📄 *File:* %s
                    📦 *Size:* %s
                    %s
                    *Admin Actions:*
                    • To approve & sync to Drive: Reply */approve %s*
                    • To reject: Reply */reject %s <reason>*
                    """,
                    memberName, doc.getSenderPhoneNumber(),
                    doc.getDocId(),
                    doc.getCategoryTag() != null ? doc.getCategoryTag() : doc.getType(),
                    doc.getOriginalFileName(),
                    formatSize(doc.getFileSizeBytes()),
                    doc.getCaptionOrNote() != null && !doc.getCaptionOrNote().isBlank()
                            ? "📝 *Caption:* " + doc.getCaptionOrNote() + "\n" : "",
                    doc.getDocId(),
                    doc.getDocId()
            );

            whatsAppProvider.sendTextMessage(adminPhone, waMessage);
            log.info("Dispatched WhatsApp approval notification to Admin: +{}", adminPhone);
        }

        // 2. Email notification
        mailService.sendDocumentApprovalEmail(
                memberName,
                doc.getSenderPhoneNumber(),
                doc.getDocId(),
                doc.getCategoryTag(),
                doc.getOriginalFileName(),
                doc.getFileSizeBytes()
        );
    }

    /**
     * Notify member when Admin approves their document.
     */
    public void notifyMemberApproved(DocumentItem doc, String folderName) {
        String message = String.format("""
                ✅ *Document Approved by Admin!*
                ━━━━━━━━━━━━━━━━━━━━
                🎉 Your document *%s* (%s) has been approved and moved into your official Google Drive vault:
                📂 `drive/family_chatbot/%s/`
                
                Reply with */get %s* anytime to retrieve it.
                """,
                doc.getOriginalFileName(), doc.getDocId(), folderName, doc.getDocId()
        );

        whatsAppProvider.sendTextMessage(doc.getSenderPhoneNumber(), message);
    }

    /**
     * Notify member when Admin rejects their document.
     */
    public void notifyMemberRejected(DocumentItem doc, String reason) {
        String message = String.format("""
                ❌ *Document Approval Update*
                ━━━━━━━━━━━━━━━━━━━━
                Your document *%s* (%s) was not approved by the Admin.
                📝 *Reason:* %s
                
                Please check the document and upload a clear copy if necessary.
                """,
                doc.getOriginalFileName(), doc.getDocId(),
                reason != null && !reason.isBlank() ? reason : "Document details could not be verified"
        );

        whatsAppProvider.sendTextMessage(doc.getSenderPhoneNumber(), message);
    }

    /**
     * Send user issue report directly to Admin via both Email and WhatsApp.
     */
    public void notifyAdminUserIssue(String memberName, String memberPhone, String issueDescription) {
        // 1. Email notification to Admin (hrudaypreetham10@gmail.com)
        mailService.sendIssueReportEmail(memberName, memberPhone, issueDescription);

        // 2. WhatsApp notification to Admin (+91 8790208445)
        String adminPhone = whitelistService.getAdminNumber();
        if (adminPhone != null && !adminPhone.isBlank() && !adminPhone.equals(memberPhone)) {
            String waMessage = String.format("""
                    🚨 *User Reported an Issue!*
                    ━━━━━━━━━━━━━━━━━━━━
                    👤 *Member:* %s (+%s)
                    📝 *Issue Description:*
                    "%s"
                    
                    📂 *User Vault:* `drive/family_chatbot/%s/`
                    """, memberName, memberPhone, issueDescription, memberName);

            whatsAppProvider.sendTextMessage(adminPhone, waMessage);
            log.info("Dispatched WhatsApp issue alert to Admin: +{}", adminPhone);
        }
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
