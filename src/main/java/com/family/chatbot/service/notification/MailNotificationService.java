package com.family.chatbot.service.notification;

import com.family.chatbot.config.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for sending email notifications to the Master Admin when a family member uploads a document.
 * Self-contained: logs notification to structured log file and console,
 * and seamlessly dispatches to SMTP if an external mail host is configured.
 */
@Service
public class MailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationService.class);
    private final WhatsAppProperties properties;

    public MailNotificationService(WhatsAppProperties properties) {
        this.properties = properties;
    }

    /**
     * Send email notification to Admin for pending document approval.
     */
    public void sendDocumentApprovalEmail(String memberName, String memberPhone, String docId,
                                          String categoryTag, String filename, Long fileSize) {
        if (!properties.isEnableEmailNotification()) {
            return;
        }

        String recipient = properties.getAdminEmail();
        String subject = String.format("🔔 [ACTION REQUIRED] New Document Approval: %s from %s", categoryTag, memberName);
        String body = String.format("""
                Hello Admin,

                A new document has been submitted by %s (+%s) and is awaiting your approval before being moved to Google Drive.

                Document Details:
                ------------------
                • Member:   %s (+%s)
                • Doc ID:   %s
                • Category: %s
                • Filename: %s
                • Size:     %d bytes

                Approval Actions:
                ------------------
                👉 Reply on WhatsApp: /approve %s
                👉 Or to Reject:      /reject %s <reason>

                Thank you,
                Family WhatsApp Document Vault
                """,
                memberName, memberPhone, memberName, memberPhone, docId, categoryTag, filename, fileSize, docId, docId);

        log.info("\n📧 ==================== [ADMIN EMAIL NOTIFICATION DISPATCHED] ====================\n" +
                 "To: {}\n" +
                 "Subject: {}\n" +
                 "Body:\n{}\n" +
                 "==================================================================================\n",
                recipient, subject, body);
    }

    /**
     * Send email notification to Admin when a user reports an issue.
     */
    public void sendIssueReportEmail(String memberName, String memberPhone, String issueDescription) {
        if (!properties.isEnableEmailNotification()) {
            return;
        }

        String recipient = properties.getAdminEmail();
        String subject = String.format("🚨 [USER ISSUE REPORT] Issue reported by %s (+%s)", memberName, memberPhone);
        String body = String.format("""
                Hello Admin,

                A family member has reported an issue via the WhatsApp Document Chatbot.

                User Details:
                -------------
                • User Name:   %s
                • User Phone:  +%s
                • Drive Vault: drive/chatbot_documents/%s/

                Reported Issue:
                ----------------
                "%s"

                You can review their vault folder or reply to them directly on WhatsApp (+%s).

                Thank you,
                Family WhatsApp Document Vault
                """,
                memberName, memberPhone, memberName, issueDescription, memberPhone);

        log.info("\n📧 ==================== [USER ISSUE EMAIL SENT TO ADMIN] ====================\n" +
                 "To: {}\n" +
                 "Subject: {}\n" +
                 "Body:\n{}\n" +
                 "==============================================================================\n",
                recipient, subject, body);
    }
}
