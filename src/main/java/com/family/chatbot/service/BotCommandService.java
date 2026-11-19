package com.family.chatbot.service;

import com.family.chatbot.model.AuditEventType;
import com.family.chatbot.model.AuditLog;
import com.family.chatbot.model.AuditStatus;
import com.family.chatbot.model.DocumentItem;
import com.family.chatbot.model.DocumentStatus;
import com.family.chatbot.model.FamilyMember;
import com.family.chatbot.model.UserRole;
import com.family.chatbot.service.notification.AdminNotificationService;
import com.family.chatbot.service.provider.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to process rule-based commands and menu selections with Number-Bias Restriction:
 * - Admin: Full access to all family documents across all members, approvals, analytics, and deletion.
 * - Family Members: Strictly isolated access to select, view, and download their own documents only.
 *   (Regular members CANNOT delete documents; only Master Admin can delete).
 * - Full Audit Logging for every access and security violation attempt.
 */
@Service
public class BotCommandService {

    private static final Logger log = LoggerFactory.getLogger(BotCommandService.class);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    private final DocumentStorageService storageService;
    private final WhitelistService whitelistService;
    private final AuditLogService auditLogService;
    private final WhatsAppProvider whatsAppProvider;
    private final AdminNotificationService adminNotificationService;
    private final UserAnalyticsService userAnalyticsService;
    private final ChatSessionService chatSessionService;
    private final Set<String> awaitingIssueUsers = ConcurrentHashMap.newKeySet();

    public BotCommandService(DocumentStorageService storageService,
                             WhitelistService whitelistService,
                             AuditLogService auditLogService) {
        this(storageService, whitelistService, auditLogService, null, null, null, new ChatSessionService());
    }

    public BotCommandService(DocumentStorageService storageService,
                             WhitelistService whitelistService,
                             AuditLogService auditLogService,
                             WhatsAppProvider whatsAppProvider,
                             AdminNotificationService adminNotificationService,
                             UserAnalyticsService userAnalyticsService) {
        this(storageService, whitelistService, auditLogService, whatsAppProvider, adminNotificationService, userAnalyticsService, new ChatSessionService());
    }

    @Autowired
    public BotCommandService(DocumentStorageService storageService,
                             WhitelistService whitelistService,
                             AuditLogService auditLogService,
                             @Autowired(required = false) WhatsAppProvider whatsAppProvider,
                             @Autowired(required = false) AdminNotificationService adminNotificationService,
                             @Autowired(required = false) UserAnalyticsService userAnalyticsService,
                             @Autowired(required = false) ChatSessionService chatSessionService) {
        this.storageService = storageService;
        this.whitelistService = whitelistService;
        this.auditLogService = auditLogService;
        this.whatsAppProvider = whatsAppProvider;
        this.adminNotificationService = adminNotificationService;
        this.userAnalyticsService = userAnalyticsService;
        this.chatSessionService = (chatSessionService != null) ? chatSessionService : new ChatSessionService();
    }

    /**
     * Process an incoming text command or menu choice with role and number bias.
     */
    public String processCommand(String text, String senderPhone, String senderName) {
        boolean isAdmin = whitelistService.isAdmin(senderPhone);
        String roleName = isAdmin ? "ADMIN" : "MEMBER";

        if (text == null || text.isBlank()) {
            return getHelpMenu(isAdmin);
        }

        String trimmed = text.trim();
        String lower = trimmed.toLowerCase();

        // 1. Strict Session Start: ONLY "hii" (case-insensitive) starts the session!
        if (lower.equals("hii")) {
            awaitingIssueUsers.remove(senderPhone);
            chatSessionService.startSession(senderPhone);
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                    "LOGIN", null, "Session initiated with strict 'hii'");
            return handleGreeting(senderPhone, isAdmin);
        }

        // 2. Validate Active Session: If user sends ANY other word before "hii", reject politely!
        if (!chatSessionService.isSessionActive(senderPhone)) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                    "SESSION_REQUIRED", null, "Attempted action without starting session via 'hii': " + text);
            return """
                    🙏 *Please start the session with 'hii' only.*
                    ━━━━━━━━━━━━━━━━━━━━
                    Welcome to the Family Document Vault!
                    
                    To begin your session and view your documents, please reply with:
                    👉 *hii*
                    """;
        }

        // 3. User can explicitly end session with "bye", "exit", "/exit", or "logout"
        if (lower.equals("bye") || lower.equals("exit") || lower.equals("/exit") || lower.equals("logout")) {
            awaitingIssueUsers.remove(senderPhone);
            chatSessionService.endSession(senderPhone);
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                    "LOGOUT", null, "User ended session explicitly");
            return """
                    👋 *Session Ended Successfully.*
                    ━━━━━━━━━━━━━━━━━━━━
                    Thank you! Whenever you need your documents again, simply reply with *hii* to begin.
                    """;
        }

        // Sliding expiration refresh on any valid command in an active session
        chatSessionService.refreshSession(senderPhone);

        // 3a. If the user previously selected "Report Problem", treat their next message as the issue description!
        if (awaitingIssueUsers.contains(senderPhone)) {
            // If they sent another menu command, process the command instead of treating it as issue text
            if (!lower.equals("9") && !lower.equals("issue") && !lower.equals("issues") &&
                !lower.equals("/issue") && !lower.equals("report") && !lower.equals("/report") &&
                !lower.equals("menu") && !lower.equals("help") && !lower.equals("/menu") && !lower.equals("/help") &&
                !lower.equals("contact") && !lower.equals("/contact") && !lower.matches("^[1-8]$")) {
                awaitingIssueUsers.remove(senderPhone);
                return handleReportIssueCommand(trimmed, senderPhone, senderName, roleName);
            }
        }

        // 4. Menu / Help
        if (lower.equals("menu") || lower.equals("help") || lower.equals("/help") || lower.equals("/menu")) {
            return getHelpMenu(isAdmin);
        }

        // 5. Contact Admin Command
        if (lower.equals("contact") || lower.equals("/contact") || lower.equals("admin") || lower.equals("/admin")) {
            return """
                    📞 *Master Admin Contact Information*
                    ━━━━━━━━━━━━━━━━━━━━
                    For any urgent help, document approvals, or questions:
                    
                    👤 *Admin Name:* Hruday Preetham
                    📱 *Phone Number:* +91 8790208445
                    📧 *Email:* hrudaypreetham10@gmail.com
                    👉 *Direct WhatsApp Call/Chat:* https://wa.me/918790208445
                    """;
        }

        // 6. Document Services: 1: Aadhaar, 2: PAN, 3: Driving License, 4: Passport, 5: Health, 6: Other
        if (lower.equals("1") || lower.equals("aadhaar") || lower.equals("aadhar") || lower.equals("adhar") || lower.equals("uidai")) {
            return handleCategorySelection("AADHAAR", "Aadhaar Card", senderPhone, isAdmin, roleName);
        }

        if (lower.equals("2") || lower.equals("pan") || lower.equals("pancard")) {
            return handleCategorySelection("PAN", "PAN Card", senderPhone, isAdmin, roleName);
        }

        if (lower.equals("3") || lower.equals("driving license") || lower.equals("license") || lower.equals("dl") || lower.equals("licence")) {
            return handleCategorySelection("DRIVING_LICENSE", "Driving License", senderPhone, isAdmin, roleName);
        }

        if (lower.equals("4") || lower.equals("passport") || lower.equals("visa")) {
            return handleCategorySelection("PASSPORT", "Passport", senderPhone, isAdmin, roleName);
        }

        if (lower.equals("5") || lower.equals("health") || lower.equals("insurance") || lower.equals("mediclaim")) {
            return handleCategorySelection("HEALTH", "Health / Insurance", senderPhone, isAdmin, roleName);
        }

        if (lower.equals("6") || lower.equals("other") || lower.equals("docs") || lower.equals("documents")) {
            return handleCategorySelection("OTHER", "Other Documents", senderPhone, isAdmin, roleName);
        }

        // Report Issue / Helpdesk: 9, report, /report, issue, /issue
        if (lower.equals("9") || lower.equals("issue") || lower.equals("issues") || lower.equals("/issue") ||
            lower.equals("report") || lower.equals("/report") || lower.equals("helpdesk")) {
            awaitingIssueUsers.add(senderPhone);
            return """
                    📝 *Any Issues? / Report a Problem*
                    ━━━━━━━━━━━━━━━━━━━━
                    Please tell us what issue you are experiencing with the chatbot or your documents.

                    👉 *Reply with:* */issue <describe your issue>*
                    Example: */issue Unable to download my Aadhaar Card*

                    Or simply type your problem below and send it. Your message will be logged immediately to the server console and sent to Master Admin.

                    📞 *For any issues contact Admin Call:*
                    Admin Number: +91 8790208445
                    👉 *Direct WhatsApp Call/Chat:* https://wa.me/918790208445
                    """;
        }

        if (lower.startsWith("/issue ") || lower.startsWith("issue ") || lower.startsWith("/report ") || lower.startsWith("report ")) {
            return handleReportIssueCommand(trimmed, senderPhone, senderName, roleName);
        }

        // 4. Download Document: /download <DOC-ID>
        if (lower.startsWith("/download ") || lower.startsWith("download ")) {
            String docId = trimmed.replaceFirst("(?i)^(/download|download)\\s+", "").trim();
            return handleDownloadCommand(docId, senderPhone, isAdmin, roleName);
        }

        // 5. Delete Document: /delete <DOC-ID> or /deletedoc <DOC-ID> (STRICTLY ADMIN ONLY)
        if (lower.startsWith("/delete ") || lower.startsWith("delete ") ||
            lower.startsWith("/deletedoc ") || lower.startsWith("deletedoc ")) {
            String docId = trimmed.replaceFirst("(?i)^(/delete|delete|/deletedoc|deletedoc)\\s+", "").trim();
            return handleDeleteCommand(docId, senderPhone, isAdmin, roleName);
        }

        // 6. Admin Approval Commands: /pending, /approve <ID>, /reject <ID> [reason]
        if (lower.equals("/pending") || lower.equals("pending") || lower.equals("approvals")) {
            return handlePendingCommand(senderPhone, isAdmin, roleName);
        }

        if (lower.startsWith("/approve ") || lower.startsWith("approve ")) {
            String docId = trimmed.replaceFirst("(?i)^(/approve|approve)\\s+", "").trim();
            return handleApproveCommand(docId, senderPhone, isAdmin, roleName);
        }

        if (lower.startsWith("/reject ") || lower.startsWith("reject ")) {
            return handleRejectCommand(trimmed, senderPhone, isAdmin, roleName);
        }

        // 7. Admin User Analytics & Visual Usage Graphs: /analytics, /graph
        if (lower.equals("/analytics") || lower.equals("analytics") || lower.equals("/graph") || lower.equals("graph")) {
            return handleAnalyticsCommand(senderPhone, isAdmin, roleName);
        }

        // 8. Admin User Management Commands
        if (lower.startsWith("/adduser ") || lower.startsWith("adduser ")) {
            return handleAddUserCommand(trimmed, senderPhone, isAdmin, roleName);
        }

        if (lower.startsWith("/removeuser ") || lower.startsWith("removeuser ")) {
            return handleRemoveUserCommand(trimmed, senderPhone, isAdmin, roleName);
        }

        if (lower.equals("/listusers") || lower.equals("listusers") || lower.equals("users")) {
            return handleListUsersCommand(senderPhone, isAdmin, roleName);
        }

        if (lower.startsWith("/modifyuser ") || lower.startsWith("modifyuser ")) {
            return handleModifyUserCommand(trimmed, senderPhone, isAdmin, roleName);
        }

        // 8b. Dynamic Document Service Assignment Commands (Admin)
        if (lower.startsWith("/addservice ") || lower.startsWith("addservice ")) {
            return handleAddServiceCommand(trimmed, senderPhone, isAdmin, roleName);
        }

        if (lower.startsWith("/removeservice ") || lower.startsWith("removeservice ")) {
            return handleRemoveServiceCommand(trimmed, senderPhone, isAdmin, roleName);
        }

        if (lower.startsWith("/services") || lower.startsWith("/listservices") || lower.startsWith("services")) {
            return handleListServicesCommand(trimmed, senderPhone, isAdmin, roleName);
        }

        // 9. List Documents (Number-bias restricted)
        if (lower.equals("7") || lower.equals("list") || lower.equals("/list")) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                    "LIST_DOCUMENTS", null, "Listed recent documents");
            return handleListCommand(senderPhone, isAdmin);
        }

        // 10. Search Documents (Number-bias restricted)
        if (lower.startsWith("/search ") || lower.startsWith("search ")) {
            String query = trimmed.replaceFirst("(?i)^(/search|search)\\s+", "").trim();
            auditLogService.record(AuditEventType.SEARCH, AuditStatus.SUCCESS, senderPhone, roleName,
                    "SEARCH_DOCUMENTS", null, "Search query: " + query);
            return handleSearchCommand(query, senderPhone, isAdmin);
        }

        // 11. Get Document Details (Strict ownership check & number-bias defense)
        if (lower.startsWith("/get ") || lower.startsWith("get ")) {
            String docId = trimmed.replaceFirst("(?i)^(/get|get)\\s+", "").trim();
            return handleGetCommand(docId, senderPhone, isAdmin, roleName);
        }

        // 12. Save Note / Documentation
        if (lower.startsWith("/note ") || lower.startsWith("note ")) {
            String noteContent = trimmed.replaceFirst("(?i)^(/note|note)\\s+", "").trim();
            return handleSaveNoteCommand(noteContent, senderPhone, senderName, roleName);
        }

        // 13. Storage Statistics
        if (lower.equals("8") || lower.equals("stats") || lower.equals("/stats")) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                    "VIEW_STATS", null, "Viewed storage statistics");
            return handleStatsCommand(senderPhone, isAdmin);
        }

        // 14. Whitelisted Family Numbers
        if (lower.equals("family") || lower.equals("/family")) {
            return handleFamilyCommand(isAdmin);
        }

        // 15. Admin Audit Logs & Security Alerts
        if (lower.equals("/logs") || lower.equals("logs")) {
            if (!isAdmin) {
                auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                        "VIEW_LOGS", null, "Non-admin attempted to view system audit logs");
                return "⛔ *Access Denied*: Only the Master Admin can view system audit logs.";
            }
            return handleAdminLogsCommand();
        }

        // 16. Admin Issues Inbox Command
        if (lower.equals("/issues") || lower.equals("/listissues")) {
            if (!isAdmin) {
                auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                        "VIEW_ISSUES", null, "Non-admin attempted to view issues inbox");
                return "⛔ *Access Denied*: Only the Master Admin can view reported user issues.";
            }
            return handleAdminIssuesCommand();
        }

        return "💡 *Need help?*\n" +
               "I didn't recognize: \"" + trimmed + "\"\n\n" +
               "*Here is what you can do:*\n" +
               "• Reply *Hii* to view your Document Services (Aadhaar, PAN, DL, etc.)\n" +
               "• Reply */download <ID>* to download an approved document\n" +
               "• Reply *9* or */issue <text>* if you face any problem\n" +
               "• Or attach any photo or PDF with a caption to store it!\n\n" +
               "📞 *For any issues contact Admin Call:* +91 8790208445\n" +
               "👉 *Direct WhatsApp Call/Chat:* https://wa.me/918790208445";
    }

    public String getHelpMenu(boolean isAdmin) {
        String roleHeader = isAdmin ? "👑 *ROLE: MASTER ADMIN*" : "👤 *ROLE: FAMILY MEMBER*";
        String adminExtra = isAdmin
                ? "\n👑 *Admin Tools:*\n• */pending* - Pending Approvals\n• */analytics* - User Activity Graphs\n• */delete <ID>* - Delete Document\n• */logs* - System Logs\n• */listusers* - Manage Users"
                : "";

        return String.format("""
                📂 *FAMILY DOCUMENT & PIC VAULT* 📂
                %s
                
                *Select a Document Service:*
                1️⃣ *Aadhaar Card* (Reply *1* or *aadhaar*)
                2️⃣ *PAN Card* (Reply *2* or *pan*)
                3️⃣ *Driving License* (Reply *3* or *license*)
                4️⃣ *Passport* (Reply *4* or *passport*)
                5️⃣ *Health / Insurance* (Reply *5* or *health*)
                6️⃣ *Other Documents* (Reply *6* or *other*)
                7️⃣ *List My Files* (Reply *7* or *list*)
                8️⃣ *Storage Stats* (Reply *8* or *stats*)
                9️⃣ *Any Issues?* (Reply *9* or */issue <text>* to alert Admin)
                
                🔍 *Other Actions:*
                • */download <ID>* - Download approved file directly
                • */search <keyword>* - Search your documents
                • */get <ID>* - View file metadata & Drive path
                • */note <text>* - Save a text note
                • */issue <text>* - Report any issue/problem to Admin
                %s
                
                📎 *To save any Document or Photo:*
                Attach a PDF or photo with the document name as caption (e.g. *Aadhaar* or *PAN*)!
                """,
                roleHeader,
                adminExtra
        );
    }

    private String handleGreeting(String senderPhone, boolean isAdmin) {
        String displayName = whitelistService.getUserDisplayName(senderPhone);
        String folderName = whitelistService.getUserFolderName(senderPhone);
        List<DocumentItem> userDocs = storageService.findRecentDocuments(senderPhone, isAdmin);

        StringBuilder sb = new StringBuilder();
        sb.append("👋 *Hii ").append(displayName).append("!*\n");
        sb.append("Welcome back to your Private Family Document Vault.\n\n");
        sb.append("📂 *Your Personal Drive Vault:* `drive/family_chatbot/").append(folderName).append("/`\n\n");

        sb.append("📋 *Select a Document Service:*\n");
        if (isAdmin || whitelistService.hasService(senderPhone, "AADHAAR")) {
            sb.append("1️⃣ *Aadhaar Card* (Reply *1* or *aadhaar*)\n");
        }
        if (isAdmin || whitelistService.hasService(senderPhone, "PAN")) {
            sb.append("2️⃣ *PAN Card* (Reply *2* or *pan*)\n");
        }
        if (isAdmin || whitelistService.hasService(senderPhone, "DRIVING_LICENSE")) {
            sb.append("3️⃣ *Driving License* (Reply *3* or *license*)\n");
        }
        if (isAdmin || whitelistService.hasService(senderPhone, "PASSPORT")) {
            sb.append("4️⃣ *Passport* (Reply *4* or *passport*)\n");
        }
        if (isAdmin || whitelistService.hasService(senderPhone, "HEALTH")) {
            sb.append("5️⃣ *Health / Insurance* (Reply *5* or *health*)\n");
        }
        if (isAdmin || whitelistService.hasService(senderPhone, "OTHER")) {
            sb.append("6️⃣ *Other Documents* (Reply *6* or *other*)\n");
        }
        sb.append("7️⃣ *List All My Files* (Reply *7* or *list*)\n");
        sb.append("8️⃣ *Storage Stats* (Reply *8* or *stats*)\n");
        sb.append("9️⃣ *Any Issues?* (Reply *9* or */issue <problem>*)\n\n");

        if (!userDocs.isEmpty()) {
            sb.append("📋 *Recent Files (").append(userDocs.size()).append("):*\n");
            for (DocumentItem doc : userDocs) {
                String icon = switch (doc.getType()) {
                    case IMAGE -> "🖼️";
                    case DOCUMENT -> "📄";
                    case NOTE -> "📝";
                    default -> "📁";
                };
                sb.append("• ").append(icon).append(" *").append(doc.getDocId()).append("*: ")
                  .append(doc.getOriginalFileName()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("💡 *Quick Actions:*\n");
        sb.append("• Reply *1* or *aadhaar* to view your Aadhaar Card\n");
        sb.append("• Reply */download <DOC-ID>* to download your file\n");
        sb.append("• 📎 *Attach any Photo/PDF* to save a new document\n");

        if (isAdmin) {
            sb.append("\n👑 *Admin Controls:*\n");
            sb.append("• */pending* - Review pending approvals\n");
            sb.append("• */approve <ID>* | */reject <ID> <reason>*\n");
            sb.append("• */delete <ID>* - Delete document\n");
            sb.append("• */analytics* - View user login & service usage graphs\n");
            sb.append("• */listusers* | */adduser <name> <phone>* | */logs*");
        }

        return sb.toString();
    }

    private String handleCategorySelection(String categoryTag, String categoryDisplayName, String senderPhone, boolean isAdmin, String roleName) {
        // Enforce dynamic service restriction: Only allowed services can be accessed by members
        if (!isAdmin && !whitelistService.hasService(senderPhone, categoryTag)) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                    "SERVICE_RESTRICTED", null, "Attempted to access disabled service: " + categoryDisplayName);
            return String.format("""
                    ⛔ *Service Restricted: Not Assigned to Your Account*
                    ━━━━━━━━━━━━━━━━━━━━
                    The *%s* service is currently not enabled for your account by the Master Admin.
                    
                    📞 *For any issues or to request access:*
                    Admin Call: +91 8790208445
                    👉 *Direct WhatsApp Chat:* https://wa.me/918790208445
                    """, categoryDisplayName);
        }

        auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                "SERVICE_" + categoryTag, null, "Selected service: " + categoryDisplayName);

        Optional<DocumentItem> opt = storageService.findCategoryDoc(senderPhone, categoryTag);
        String folderName = whitelistService.getUserFolderName(senderPhone);

        if (opt.isEmpty()) {
            // Also check physical Google Drive folder for matching file (e.g. aadhar.pdf, pan.jpg, etc.)
            List<Path> driveFiles = storageService.listDriveFiles(folderName);
            for (Path p : driveFiles) {
                String fname = p.getFileName().toString().toLowerCase();
                boolean matches = switch (categoryTag.toUpperCase()) {
                    case "AADHAAR" -> fname.contains("aadhaar") || fname.contains("aadhar") || fname.contains("adhar");
                    case "PAN" -> fname.contains("pan");
                    case "DRIVING_LICENSE" -> fname.contains("license") || fname.contains("driving") || fname.contains("dl");
                    case "PASSPORT" -> fname.contains("passport");
                    case "HEALTH" -> fname.contains("health") || fname.contains("insurance");
                    default -> false;
                };
                if (matches) {
                    try {
                        long size = Files.size(p);
                        return String.format("""
                                📄 *%s Found in Google Drive Folder!*
                                ━━━━━━━━━━━━━━━━━━━━
                                📁 *File Name:* %s
                                📦 *Size:* %s
                                📂 *Drive Folder:* `drive/family_chatbot/%s/%s`
                                ☁️ *Cloud Status:* Verified in Google Drive Cloud Vault
                                """, categoryDisplayName, p.getFileName().toString(), formatSize(size), folderName, p.getFileName().toString());
                    } catch (IOException ignored) {}
                }
            }

            return String.format("""
                    📭 *No %s Found in Your Vault*
                    ━━━━━━━━━━━━━━━━━━━━
                    We checked both your Vault Database and your Google Drive folder:
                    📂 `drive/family_chatbot/%s/`
                    
                    No %s was found.
                    
                    📎 *To upload now:*
                    Simply attach your %s (Photo or PDF) in this chat with the caption: *%s*
                    """, categoryDisplayName, folderName, categoryDisplayName, categoryDisplayName, categoryDisplayName);
        }

        DocumentItem item = opt.get();

        if (item.getStatus() == DocumentStatus.PENDING_APPROVAL) {
            return String.format("""
                    ⏳ *%s (Pending Admin Approval)*
                    ━━━━━━━━━━━━━━━━━━━━
                    🆔 *Doc ID:* %s
                    📁 *File:* %s
                    📦 *Size:* %s
                    📅 *Uploaded:* %s
                    ⚠️ *Status:* ⏳ Waiting for Master Admin approval before syncing to Drive.
                    You will receive a WhatsApp message once the Admin approves it.
                    """, categoryDisplayName, item.getDocId(), item.getOriginalFileName(),
                    formatSize(item.getFileSizeBytes()), item.getCreatedAt().format(DISPLAY_DATE));
        }

        if (item.getStatus() == DocumentStatus.REJECTED) {
            return String.format("""
                    ❌ *%s (Approval Rejected)*
                    ━━━━━━━━━━━━━━━━━━━━
                    🆔 *Doc ID:* %s
                    📁 *File:* %s
                    📝 *Reason:* %s
                    
                    Please upload a fresh, clear copy of your %s by attaching it in this chat.
                    """, categoryDisplayName, item.getDocId(), item.getOriginalFileName(),
                    item.getRejectionReason() != null ? item.getRejectionReason() : "Could not verify document details",
                    categoryDisplayName);
        }

        // APPROVED
        return String.format("""
                📄 *%s Found!*
                ━━━━━━━━━━━━━━━━━━━━
                🆔 *Doc ID:* %s
                📁 *File:* %s
                📦 *Size:* %s
                📅 *Uploaded:* %s
                📂 *Drive Path:* `drive/family_chatbot/%s/%s`
                ✅ *Status:* Approved & Synced to Drive
                
                📥 *To download this document:*
                Reply: */download %s*
                """, categoryDisplayName, item.getDocId(), item.getOriginalFileName(),
                formatSize(item.getFileSizeBytes()), item.getCreatedAt().format(DISPLAY_DATE),
                folderName, item.getOriginalFileName(), item.getDocId());
    }

    private String handleDownloadCommand(String docId, String senderPhone, boolean isAdmin, String roleName) {
        Optional<DocumentItem> opt = storageService.findByDocId(docId);
        if (opt.isEmpty()) {
            auditLogService.record(AuditEventType.FILE_ACCESS, AuditStatus.ERROR, senderPhone, roleName,
                    "DOWNLOAD_DOCUMENT", docId, "Document not found for download");
            return "❌ *Document Not Found:* " + docId;
        }

        DocumentItem item = opt.get();

        // Strict Number-Bias Restriction: Member cannot download another member's document
        if (!storageService.canAccess(item, senderPhone, isAdmin)) {
            auditLogService.record(AuditEventType.NUMBER_BIAS_VIOLATION, AuditStatus.DENIED, senderPhone, roleName,
                    "DOWNLOAD_DOCUMENT", docId, "Unauthorized download attempt for document owned by: " + item.getSenderPhoneNumber());
            return "⛔ *Access Denied*: You can only download your own documents.";
        }

        // Approval check
        if (item.getStatus() == DocumentStatus.PENDING_APPROVAL) {
            return "⏳ *Download Blocked*: This document is pending Master Admin approval. Once approved, it can be downloaded.";
        }

        if (item.getStatus() == DocumentStatus.REJECTED) {
            return "❌ *Download Unavailable*: This document was rejected by the Admin.";
        }

        auditLogService.record(AuditEventType.FILE_ACCESS, AuditStatus.SUCCESS, senderPhone, roleName,
                "DOWNLOAD_DOCUMENT", docId, "Downloaded document: " + item.getOriginalFileName());

        if (whatsAppProvider != null && item.getStoredPath() != null) {
            try {
                File file = storageService.getDecryptedTempFile(item);
                whatsAppProvider.sendDocumentMessage(senderPhone, file, item.getOriginalFileName(),
                        "Here is your " + item.getOriginalFileName());
                file.delete();
            } catch (Exception e) {
                log.error("Failed to decrypt or send document via WhatsApp: {}", e.getMessage());
            }
        }

        String folderName = whitelistService.getUserFolderName(item.getSenderPhoneNumber());
        return String.format("""
                📥 *Downloading Document:* *%s* (%s)
                📦 *Size:* %s
                📂 *Drive Path:* `drive/family_chatbot/%s/%s`
                
                Sending the file directly to your WhatsApp chat!
                """, item.getOriginalFileName(), item.getDocId(),
                formatSize(item.getFileSizeBytes()), folderName, item.getOriginalFileName());
    }

    private String handleDeleteCommand(String docId, String senderPhone, boolean isAdmin, String roleName) {
        // STRICT USER PERMISSION CHECK: Regular users CANNOT delete any document!
        if (!isAdmin) {
            auditLogService.record(AuditEventType.FILE_ACCESS, AuditStatus.DENIED, senderPhone, roleName,
                    "DELETE_DOCUMENT", docId, "Non-admin attempted to delete document");
            return "⛔ *Access Denied*: Only the Master Admin can delete documents. You are only permitted to select, view, and download documents.";
        }

        Optional<DocumentItem> opt = storageService.findByDocId(docId);
        if (opt.isEmpty()) {
            return "❌ Document not found: " + docId;
        }

        DocumentItem item = opt.get();
        String fileName = item.getOriginalFileName();
        boolean deleted = storageService.deleteDocument(docId, senderPhone, true);

        if (deleted) {
            auditLogService.record(AuditEventType.FILE_ACCESS, AuditStatus.SUCCESS, senderPhone, roleName,
                    "DELETE_DOCUMENT", docId, "Admin permanently deleted document: " + fileName);
            return "🗑️ *Document Deleted Successfully!*\nDocument *" + docId + "* (" + fileName + ") has been permanently removed from local storage and Google Drive.";
        } else {
            return "❌ Failed to delete document: " + docId;
        }
    }

    private String handlePendingCommand(String senderPhone, boolean isAdmin, String roleName) {
        if (!isAdmin) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                    "LIST_PENDING", null, "Non-admin attempted to view pending approvals");
            return "⛔ *Access Denied*: Only Master Admin can view pending approvals.";
        }

        List<DocumentItem> pending = storageService.findPendingApprovals();
        if (pending.isEmpty()) {
            return "✅ *No Pending Approvals!*\nAll uploaded documents have been reviewed.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("⏳ *Documents Awaiting Admin Approval (").append(pending.size()).append("):*\n\n");

        for (DocumentItem item : pending) {
            sb.append("• *").append(item.getDocId()).append("*: ").append(item.getOriginalFileName()).append("\n")
              .append("  👤 Member: ").append(item.getSenderName() != null ? item.getSenderName() : "Member")
              .append(" (+").append(maskPhoneNumber(item.getSenderPhoneNumber())).append(")\n")
              .append("  🏷️ Category: ").append(item.getCategoryTag()).append("\n")
              .append("  📦 Size: ").append(formatSize(item.getFileSizeBytes())).append("\n")
              .append("  📅 Date: ").append(item.getCreatedAt().format(DISPLAY_DATE)).append("\n\n");
        }

        sb.append("💡 *Actions:*\n• Reply */approve <ID>* to approve and sync to Drive\n• Reply */reject <ID> <reason>* to reject");
        return sb.toString();
    }

    private String handleApproveCommand(String docId, String senderPhone, boolean isAdmin, String roleName) {
        if (!isAdmin) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                    "APPROVE_DOCUMENT", docId, "Non-admin attempted to approve document");
            return "⛔ *Access Denied*: Only Master Admin can approve documents.";
        }

        try {
            Optional<DocumentItem> opt = storageService.findByDocId(docId);
            if (opt.isEmpty()) {
                return "❌ Document not found: " + docId;
            }

            DocumentItem doc = opt.get();
            String folderName = whitelistService.getUserFolderName(doc.getSenderPhoneNumber());
            DocumentItem approved = storageService.approveDocument(docId, senderPhone, folderName);

            if (adminNotificationService != null) {
                adminNotificationService.notifyMemberApproved(approved, folderName);
            }

            auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                    "APPROVE_DOCUMENT", docId, "Admin approved document for member: " + doc.getSenderPhoneNumber());

            return String.format("""
                    ✅ *Document Approved!*
                    ━━━━━━━━━━━━━━━━━━━━
                    🆔 *Doc ID:* %s
                    📄 *File:* %s
                    👤 *Member:* %s (+%s)
                    📂 *Drive Vault:* `drive/family_chatbot/%s/`
                    
                    The document has been synced to Google Drive and the member has been notified!
                    """, approved.getDocId(), approved.getOriginalFileName(),
                    approved.getSenderName() != null ? approved.getSenderName() : "Member",
                    maskPhoneNumber(approved.getSenderPhoneNumber()), folderName);
        } catch (Exception e) {
            return "❌ Error approving document: " + e.getMessage();
        }
    }

    private String handleRejectCommand(String trimmed, String senderPhone, boolean isAdmin, String roleName) {
        if (!isAdmin) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                    "REJECT_DOCUMENT", null, "Non-admin attempted to reject document");
            return "⛔ *Access Denied*: Only Master Admin can reject documents.";
        }

        String[] parts = trimmed.split("\\s+", 3);
        if (parts.length < 2) {
            return "⚠️ Usage: */reject <DOC-ID> [reason]*\nExample: */reject DOC-1002 Unclear photo*";
        }

        String docId = parts[1];
        String reason = parts.length > 2 ? parts[2] : "Document image unclear or details could not be verified";

        try {
            Optional<DocumentItem> opt = storageService.findByDocId(docId);
            if (opt.isEmpty()) {
                return "❌ Document not found: " + docId;
            }

            DocumentItem rejected = storageService.rejectDocument(docId, reason, senderPhone);
            if (adminNotificationService != null) {
                adminNotificationService.notifyMemberRejected(rejected, reason);
            }

            auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                    "REJECT_DOCUMENT", docId, "Admin rejected document. Reason: " + reason);

            return "❌ Document *" + docId + "* has been rejected. Member has been notified.";
        } catch (Exception e) {
            return "❌ Error rejecting document: " + e.getMessage();
        }
    }

    private String handleAnalyticsCommand(String senderPhone, boolean isAdmin, String roleName) {
        if (!isAdmin) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                    "VIEW_ANALYTICS", null, "Non-admin attempted to view analytics graphs");
            return "⛔ *Access Denied*: Only the Master Admin can view user analytics and usage graphs.";
        }

        auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                "VIEW_ANALYTICS", null, "Admin viewed user analytics and usage graph");

        if (userAnalyticsService != null) {
            return userAnalyticsService.generateWhatsAppAnalyticsGraph();
        }

        return "📊 *Analytics Service Unavailable*: Please check server logs.";
    }

    private String handleListCommand(String senderPhone, boolean isAdmin) {
        List<DocumentItem> list = storageService.findRecentDocuments(senderPhone, isAdmin);
        if (list.isEmpty()) {
            return isAdmin
                    ? "📭 *Vault is empty!*\nNo documents or photos have been saved by any family member yet."
                    : "📭 *Your personal vault is empty!*\nYou haven't saved any documents or photos yet. Send any photo or document file to start.";
        }

        StringBuilder sb = new StringBuilder();
        if (isAdmin) {
            sb.append("📋 *All Family Vault Documents (Admin View - Last ").append(list.size()).append("):*\n\n");
        } else {
            sb.append("📋 *Your Personal Stored Files (Last ").append(list.size()).append("):*\n\n");
        }

        for (DocumentItem item : list) {
            String icon = switch (item.getType()) {
                case IMAGE -> "🖼️";
                case DOCUMENT -> "📄";
                case NOTE -> "📝";
                default -> "📁";
            };

            sb.append(icon).append(" *").append(item.getDocId()).append("*: ")
              .append(item.getOriginalFileName()).append("\n")
              .append("   📅 ").append(item.getCreatedAt().format(DISPLAY_DATE))
              .append(" | 🏷️ ").append(item.getCategoryTag());

            if (isAdmin) {
                sb.append(" | 👤 Member: +").append(maskPhoneNumber(item.getSenderPhoneNumber()));
            }

            if (item.getStatus() == DocumentStatus.PENDING_APPROVAL) {
                sb.append(" | ⏳ Pending");
            }

            sb.append("\n\n");
        }

        String userFolder = whitelistService.getUserFolderName(senderPhone);
        List<Path> driveFiles = storageService.listDriveFiles(userFolder);
        if (!driveFiles.isEmpty()) {
            sb.append("☁️ *Files Present in Google Drive (`drive/family_chatbot/").append(userFolder).append("/`):*\n");
            for (Path df : driveFiles) {
                try {
                    long sz = Files.size(df);
                    sb.append("• 📄 *").append(df.getFileName()).append("* (").append(formatSize(sz)).append(")\n");
                } catch (IOException e) {
                    sb.append("• 📄 *").append(df.getFileName()).append("*\n");
                }
            }
            sb.append("\n");
        }

        sb.append("💡 Reply */download <ID>* to download or */get <ID>* for details.");
        return sb.toString();
    }

    private String handleSearchCommand(String query, String senderPhone, boolean isAdmin) {
        if (query.isBlank()) {
            return "⚠️ Please provide a search keyword. Example: */search passport*";
        }

        List<DocumentItem> results = storageService.search(query, senderPhone, isAdmin);
        if (results.isEmpty()) {
            return "🔍 *No matching documents found for:* \"" + query + "\"\n" +
                   (isAdmin ? "Searched across all family documents." : "Searched within your personal files.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🔍 *Search Results for:* \"").append(query).append("\" (")
          .append(results.size()).append(" found").append(isAdmin ? " in Family Vault" : " in Your Files").append(")\n\n");

        for (DocumentItem item : results) {
            String icon = switch (item.getType()) {
                case IMAGE -> "🖼️";
                case DOCUMENT -> "📄";
                case NOTE -> "📝";
                default -> "📁";
            };

            sb.append(icon).append(" *").append(item.getDocId()).append("*: ")
              .append(item.getOriginalFileName()).append("\n")
              .append("   📅 ").append(item.getCreatedAt().format(DISPLAY_DATE));

            if (isAdmin) {
                sb.append(" | 👤 Member: +").append(maskPhoneNumber(item.getSenderPhoneNumber()));
            }

            if (item.getCaptionOrNote() != null && !item.getCaptionOrNote().isBlank()) {
                sb.append("\n   📝 Note: ").append(item.getCaptionOrNote());
            }
            sb.append("\n\n");
        }

        sb.append("💡 Reply */get <ID>* for details or */download <ID>* to download.");
        return sb.toString();
    }

    private String handleGetCommand(String docId, String senderPhone, boolean isAdmin, String roleName) {
        Optional<DocumentItem> opt = storageService.findByDocId(docId);
        if (opt.isEmpty()) {
            auditLogService.record(AuditEventType.FILE_ACCESS, AuditStatus.ERROR, senderPhone, roleName,
                    "GET_DOCUMENT", docId, "Document not found");
            return "❌ *Document Not Found:* " + docId + "\nPlease verify the ID with */list* or */search*.";
        }

        DocumentItem item = opt.get();

        // Strict Number-Bias Restriction: Member cannot view another member's file
        if (!storageService.canAccess(item, senderPhone, isAdmin)) {
            auditLogService.record(
                    AuditEventType.NUMBER_BIAS_VIOLATION,
                    AuditStatus.DENIED,
                    senderPhone,
                    roleName,
                    "GET_DOCUMENT",
                    docId,
                    "Unauthorized attempt to access document owned by sender: " + item.getSenderPhoneNumber()
            );

            return "⛔ *Access Restricted: Number-Bias Security Active*\n" +
                   "This document belongs to another members you can't access this document.";
        }

        auditLogService.record(
                AuditEventType.FILE_ACCESS,
                AuditStatus.SUCCESS,
                senderPhone,
                roleName,
                "GET_DOCUMENT",
                docId,
                "Retrieved metadata for " + item.getOriginalFileName()
        );

        String formattedSize = formatSize(item.getFileSizeBytes());
        String folderName = whitelistService.getUserFolderName(item.getSenderPhoneNumber());

        return String.format("""
                📄 *Document Details: %s*
                ━━━━━━━━━━━━━━━━━━━━
                📁 *Filename:* %s
                🏷️ *Category:* %s
                📦 *Size:* %s
                📅 *Uploaded:* %s
                👤 *Uploaded By:* %s (+%s)
                📂 *Drive Folder:* `drive/family_chatbot/%s/`
                🔄 *Drive Synced:* %s
                ⏳ *Approval Status:* %s
                📝 *Note / Caption:* %s
                
                📥 Reply */download %s* to download this file.
                """,
                item.getDocId(),
                item.getOriginalFileName(),
                item.getCategoryTag(),
                formattedSize,
                item.getCreatedAt().format(DISPLAY_DATE),
                item.getSenderName() != null ? item.getSenderName() : "Family Member",
                maskPhoneNumber(item.getSenderPhoneNumber()),
                folderName,
                item.isSyncedToDrive() ? "✅ Synced" : "Local only",
                item.getStatus(),
                item.getCaptionOrNote() != null ? item.getCaptionOrNote() : "None",
                item.getDocId()
        );
    }

    private String handleSaveNoteCommand(String noteContent, String senderPhone, String senderName, String roleName) {
        if (noteContent.isBlank()) {
            return "⚠️ Note content cannot be empty. Example: */note Wifi password is XYZ*";
        }

        try {
            String title = noteContent.length() > 20 ? noteContent.substring(0, 20) : noteContent;
            DocumentItem saved = storageService.storeNote(noteContent, title, senderPhone, senderName);

            auditLogService.record(
                    AuditEventType.NOTE_CREATED,
                    AuditStatus.SUCCESS,
                    senderPhone,
                    roleName,
                    "SAVE_NOTE",
                    saved.getDocId(),
                    "Saved note with title: " + title
            );

            return "✅ *Note Saved Successfully!*\n" +
                   "🆔 *ID:* " + saved.getDocId() + "\n" +
                   "📅 *Saved:* " + saved.getCreatedAt().format(DISPLAY_DATE) + "\n" +
                   "🔄 *Drive Synced:* " + (saved.isSyncedToDrive() ? "Yes" : "No") + "\n\n" +
                   "Content: \"" + noteContent + "\"";
        } catch (IOException e) {
            auditLogService.record(
                    AuditEventType.NOTE_CREATED,
                    AuditStatus.ERROR,
                    senderPhone,
                    roleName,
                    "SAVE_NOTE",
                    null,
                    "Error saving note: " + e.getMessage()
            );
            return "❌ Error saving note: " + e.getMessage();
        }
    }

    private String handleStatsCommand(String senderPhone, boolean isAdmin) {
        DocumentStorageService.StorageStats stats = storageService.getStats(senderPhone, isAdmin);

        if (isAdmin) {
            long issueCount = auditLogService != null ? auditLogService.getReportedIssueCount() : 0L;
            long callCount = auditLogService != null ? auditLogService.getContactCount() : 0L;
            long sessionCount = auditLogService != null ? auditLogService.getLoginCount() : 0L;
            int memberCount = whitelistService.getAllActiveMembers().size();

            return String.format("""
                    📊 *Master Admin - Family Vault Statistics*
                    ━━━━━━━━━━━━━━━━━━━━━━━━
                    👥 *Registered Family Members:* %d
                    📁 *Total Vault Items:* %d
                    📄 *Documents (PDF/Doc):* %d
                    🖼️ *Photos & Images:* %d
                    📝 *Text Notes:* %d
                    💾 *Total Storage Used:* %s

                    ⚡ *Activity & Support Metrics:*
                    🔑 *Total 'hii' Sessions Started:* %d
                    📞 *Admin Contact/Call Inquiries:* %d
                    🚨 *Reported Issues Logged:* %d

                    🔒 *Security:* Role-Based & Number-Bias Active
                    ☁️ *Google Drive Sync:* Active (hrudaypreetham10@gmail.com)
                    
                    💡 Type */issues* to view all member issues in detail.
                    """,
                    memberCount,
                    stats.totalItems(),
                    stats.totalDocuments(),
                    stats.totalImages(),
                    stats.totalNotes(),
                    stats.getFormattedSize(),
                    sessionCount,
                    callCount,
                    issueCount
            );
        } else {
            long userIssues = auditLogService != null ? auditLogService.getUserIssueCount(senderPhone) : 0L;
            long userSessions = auditLogService != null ? auditLogService.getUserLoginCount(senderPhone) : 0L;

            return String.format("""
                    📊 *Your Personal Storage Statistics*
                    ━━━━━━━━━━━━━━━━━━━━━━━━
                    📁 *Your Total Items:* %d
                    📄 *Your Documents:* %d
                    🖼️ *Your Photos:* %d
                    📝 *Your Notes:* %d
                    💾 *Your Space Used:* %s
                    🔑 *Your Vault Sessions:* %d
                    🚨 *Your Reported Issues:* %d
                    🔒 *Privacy:* Isolated to your number only
                    """,
                    stats.totalItems(),
                    stats.totalDocuments(),
                    stats.totalImages(),
                    stats.totalNotes(),
                    stats.getFormattedSize(),
                    userSessions,
                    userIssues
            );
        }
    }

    private String handleFamilyCommand(boolean isAdmin) {
        Set<String> numbers = whitelistService.getMemberNumbers();
        String adminNum = whitelistService.getAdminNumber();

        StringBuilder sb = new StringBuilder();
        sb.append("👨‍👩‍👧‍👦 *Family Vault Access List*\n\n");
        sb.append("👑 *Master Admin:* ");
        if (adminNum == null || adminNum.isBlank()) {
            sb.append("Not set\n");
        } else {
            sb.append("+").append(maskPhoneNumber(adminNum)).append(" (Full Drive & File Access)\n");
        }

        sb.append("\n👥 *Whitelisted Family Members:*\n");
        if (numbers.isEmpty()) {
            sb.append("• No additional members registered.\n");
        } else {
            for (String num : numbers) {
                sb.append("• +").append(maskPhoneNumber(num)).append(" (Isolated Member Access)\n");
            }
        }

        sb.append("\n🔒 *Number-Bias Protection:* Active. Each member can only access their own files.");
        return sb.toString();
    }

    private String handleAdminLogsCommand() {
        List<AuditLog> logs = auditLogService.getRecentLogs();
        long violations = auditLogService.getSecurityViolationCount();

        StringBuilder sb = new StringBuilder();
        sb.append("📜 *System Audit Trail (Admin View)*\n");
        sb.append("🚨 Total Security Violations Blocked: ").append(violations).append("\n\n");

        if (logs.isEmpty()) {
            sb.append("No audit events recorded yet.\n");
        } else {
            int count = Math.min(logs.size(), 8);
            for (int i = 0; i < count; i++) {
                AuditLog entry = logs.get(i);
                String statusIcon = entry.getStatus() == AuditStatus.SUCCESS ? "✅"
                        : (entry.getStatus() == AuditStatus.DENIED ? "⛔" : "❌");

                sb.append(statusIcon).append(" *").append(entry.getEventType()).append("* (")
                  .append(entry.getTimestamp().format(DISPLAY_DATE)).append(")\n")
                  .append("   From: +").append(maskPhoneNumber(entry.getSenderPhoneNumber()))
                  .append(" | ").append(entry.getAction()).append("\n");

                if (entry.getDetails() != null && !entry.getDetails().isBlank()) {
                    String detail = entry.getDetails().length() > 50
                            ? entry.getDetails().substring(0, 50) + "..."
                            : entry.getDetails();
                    sb.append("   Info: ").append(detail).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("📁 Stored in ./logs/security-audit.log and database.");
        return sb.toString();
    }

    private String handleAdminIssuesCommand() {
        List<AuditLog> issues = auditLogService.getReportedIssues();
        if (issues.isEmpty()) {
            return "✅ *Reported Issues Inbox (0 Tickets)*\n━━━━━━━━━━━━━━━━━━━━\nNo pending issues reported by any family member. Everything is operating smoothly!";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🚨 *Reported Issues Inbox (Admin View - ").append(issues.size()).append(" Total)*\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        int count = 1;
        for (AuditLog logEntry : issues) {
            String memberName = whitelistService.getUserDisplayName(logEntry.getSenderPhoneNumber());
            String issueText = logEntry.getDetails().replaceFirst("(?i)^User reported issue:\\s*", "");
            sb.append(count++).append(". 👤 *").append(memberName).append("* (+").append(maskPhoneNumber(logEntry.getSenderPhoneNumber())).append(")\n")
              .append("   📅 ").append(logEntry.getTimestamp().format(DISPLAY_DATE)).append("\n")
              .append("   📝 \"").append(issueText).append("\"\n\n");
        }
        sb.append("👉 You can reply to members directly on WhatsApp or manage issues in the Admin Web Dashboard.");
        return sb.toString();
    }

    private String handleAddUserCommand(String trimmed, String senderPhone, boolean isAdmin, String roleName) {
        if (!isAdmin) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                    "ADD_USER", null, "Non-admin attempted to add family member");
            return "⛔ *Access Denied*: Only the Master Admin can register new family members.";
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) {
            return "⚠️ Usage: */adduser <Name> <PhoneNumber>*\nExample: */adduser Dad +918790208445*";
        }

        String name = parts[1];
        String phone = parts[2];

        try {
            FamilyMember member = whitelistService.addFamilyMember(name, phone, UserRole.MEMBER);
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                    "ADD_USER", null, "Added family member: " + name + " (" + phone + ")");

            return String.format("""
                    ✅ *Family Member Created Successfully!*
                    ━━━━━━━━━━━━━━━━━━━━
                    👤 *Name:* %s
                    📱 *Phone:* +%s
                    📂 *Drive Folder:* `drive/family_chatbot/%s/`
                    🔒 *Security:* Number-bias isolation active
                    """,
                    member.getName(), member.getPhoneNumber(), member.getFolderName());
        } catch (Exception e) {
            return "❌ Error registering user: " + e.getMessage();
        }
    }

    private String handleRemoveUserCommand(String trimmed, String senderPhone, boolean isAdmin, String roleName) {
        if (!isAdmin) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                    "REMOVE_USER", null, "Non-admin attempted to remove family member");
            return "⛔ *Access Denied*: Only the Master Admin can remove family members.";
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) {
            return "⚠️ Usage: */removeuser <PhoneNumber>*\nExample: */removeuser +919876543211*";
        }

        String phone = parts[1];
        boolean removed = whitelistService.removeFamilyMember(phone);

        if (removed) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                    "REMOVE_USER", null, "Deactivated member phone: " + phone);
            return "✅ Family member with phone +" + phone + " has been deactivated.";
        } else {
            return "❌ No active family member found with phone +" + phone;
        }
    }

    private String handleListUsersCommand(String senderPhone, boolean isAdmin, String roleName) {
        if (!isAdmin) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                    "LIST_USERS", null, "Non-admin attempted to list users");
            return "⛔ *Access Denied*: Only the Master Admin can list registered family members.";
        }

        List<FamilyMember> members = whitelistService.getAllActiveMembers();
        StringBuilder sb = new StringBuilder();
        sb.append("👨‍👩‍👧‍👦 *Registered Family Members (").append(members.size()).append(")*\n\n");

        for (FamilyMember m : members) {
            String roleBadge = m.getRole() == UserRole.ADMIN ? "👑 [ADMIN]" : "👤 [MEMBER]";
            sb.append(roleBadge).append(" *").append(m.getName()).append("*\n")
              .append("   📱 Phone: +").append(m.getPhoneNumber()).append("\n")
              .append("   📂 Drive Folder: `drive/family_chatbot/").append(m.getFolderName()).append("/`\n\n");
        }

        sb.append("💡 *Admin Actions:*\n• /adduser <name> <phone>\n• /removeuser <phone>\n• /modifyuser <phone> <newName>");
        return sb.toString();
    }

    private String handleModifyUserCommand(String trimmed, String senderPhone, boolean isAdmin, String roleName) {
        if (!isAdmin) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                    "MODIFY_USER", null, "Non-admin attempted to modify family member");
            return "⛔ *Access Denied*: Only the Master Admin can modify family members.";
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) {
            return "⚠️ Usage: */modifyuser <PhoneNumber> <NewName>*\nExample: */modifyuser +919876543211 Father*";
        }

        String phone = parts[1];
        String newName = parts[2];

        boolean updated = whitelistService.modifyFamilyMember(phone, newName);
        if (updated) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                    "MODIFY_USER", null, "Renamed member +" + phone + " to " + newName);
            return "✅ Family member updated to *" + newName + "* (Drive folder: `drive/family_chatbot/" + newName + "/`).";
        } else {
            return "❌ Member not found with phone +" + phone;
        }
    }

    private String handleReportIssueCommand(String trimmed, String senderPhone, String senderName, String roleName) {
        String issueText = trimmed.replaceFirst("(?i)^(/issue|issue|/report|report)\\s*", "").trim();
        if (issueText.isBlank()) {
            awaitingIssueUsers.add(senderPhone);
            return "⚠️ *Please describe your issue:* Reply with the details of the problem you are experiencing (e.g. \"Unable to download my Aadhaar Card\").";
        }

        String displayName = senderName != null && !senderName.isBlank()
                ? senderName
                : whitelistService.getUserDisplayName(senderPhone);

        // 1. Prominent Server Console Log Output
        System.out.println("\n=======================================================");
        System.out.println("🚨 [USER ISSUE CONSOLE ALERT] New Problem Reported!");
        System.out.println("👤 Member: " + displayName + " (Phone: +" + senderPhone + ")");
        System.out.println("📝 Issue Description: " + issueText);
        System.out.println("⏰ Timestamp: " + java.time.LocalDateTime.now());
        System.out.println("=======================================================\n");

        log.warn("🚨 [USER ISSUE CONSOLE ALERT] From {} (+{}): {}", displayName, senderPhone, issueText);

        // 2. Dispatch Email & WhatsApp alerts to Admin
        if (adminNotificationService != null) {
            adminNotificationService.notifyAdminUserIssue(displayName, senderPhone, issueText);
        }

        // 3. Audit Log
        auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                "REPORT_ISSUE", null, "User reported issue: " + issueText);

        return String.format("""
                ✅ *Issue Reported Successfully!*
                ━━━━━━━━━━━━━━━━━━━━
                Thank you, *%s*! Your issue has been logged in the server console and forwarded directly to the Master Admin:
                📧 *Admin Email:* hrudaypreetham10@gmail.com
                📱 *Admin WhatsApp:* +%s
                
                📞 *For any issues contact Admin Call:*
                Admin Number: +91 8790208445
                👉 *Direct WhatsApp Call/Chat:* https://wa.me/918790208445
                
                📝 *Your Report:*
                "%s"
                
                The Master Admin has received an alert and will look into this shortly!
                """, displayName, maskPhoneNumber(whitelistService.getAdminNumber()), issueText);
    }

    private String handleAddServiceCommand(String trimmed, String senderPhone, boolean isAdmin, String roleName) {
        if (!isAdmin) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                    "ADD_SERVICE", null, "Non-admin attempted to add service");
            return "⛔ *Access Denied*: Only the Master Admin can manage document services.";
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) {
            return "⚠️ Usage: */addservice <Phone/Name> <ServiceName>*\nExample: */addservice 919876543211 PASSPORT*\nAvailable: AADHAAR, PAN, DRIVING_LICENSE, PASSPORT, HEALTH, OTHER, MARKSHEET, VOTER_ID";
        }

        String phoneOrName = parts[1];
        String service = parts[2].toUpperCase().trim();
        String phone = resolvePhone(phoneOrName);

        boolean added = whitelistService.addUserService(phone, service);
        if (added) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                    "ADD_SERVICE", null, "Added service " + service + " to " + phone);
            return String.format("✅ *Service Added!*\nService *%s* has been enabled for user *+%s*.\nThey can now access and view it from their menu.", service, phone);
        } else {
            return "❌ User not found with phone/name: " + phoneOrName;
        }
    }

    private String handleRemoveServiceCommand(String trimmed, String senderPhone, boolean isAdmin, String roleName) {
        if (!isAdmin) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.DENIED, senderPhone, roleName,
                    "REMOVE_SERVICE", null, "Non-admin attempted to remove service");
            return "⛔ *Access Denied*: Only the Master Admin can manage document services.";
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) {
            return "⚠️ Usage: */removeservice <Phone/Name> <ServiceName>*\nExample: */removeservice 919876543211 PASSPORT*";
        }

        String phoneOrName = parts[1];
        String service = parts[2].toUpperCase().trim();
        String phone = resolvePhone(phoneOrName);

        boolean removed = whitelistService.removeUserService(phone, service);
        if (removed) {
            auditLogService.record(AuditEventType.COMMAND, AuditStatus.SUCCESS, senderPhone, roleName,
                    "REMOVE_SERVICE", null, "Removed service " + service + " from " + phone);
            return String.format("✅ *Service Removed!*\nService *%s* has been disabled for user *+%s*.\nThey will no longer see or access this document service.", service, phone);
        } else {
            return "❌ User not found with phone/name: " + phoneOrName;
        }
    }

    private String handleListServicesCommand(String trimmed, String senderPhone, boolean isAdmin, String roleName) {
        String[] parts = trimmed.split("\\s+");
        String targetPhone = (parts.length >= 2 && isAdmin) ? resolvePhone(parts[1]) : senderPhone;

        java.util.Set<String> services = whitelistService.getUserServices(targetPhone);
        String userName = whitelistService.getUserDisplayName(targetPhone);

        StringBuilder sb = new StringBuilder();
        sb.append("📋 *Document Services for ").append(userName).append(" (+").append(targetPhone).append(")*:\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        if (services.isEmpty()) {
            sb.append("⚠️ No specific services assigned.\n");
        } else {
            for (String s : services) {
                sb.append("• ✅ *").append(s).append("*\n");
            }
        }
        if (isAdmin) {
            sb.append("\n💡 *Admin Options:*\n");
            sb.append("• */addservice ").append(targetPhone).append(" <SERVICE>*\n");
            sb.append("• */removeservice ").append(targetPhone).append(" <SERVICE>*\n");
        }
        return sb.toString();
    }

    private String resolvePhone(String phoneOrName) {
        String digits = phoneOrName.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
            return digits.length() == 10 ? "91" + digits : digits;
        }
        for (FamilyMember m : whitelistService.getAllActiveMembers()) {
            if (m.getName().equalsIgnoreCase(phoneOrName)) {
                return m.getPhoneNumber();
            }
        }
        return phoneOrName;
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 6) return "******";
        int len = phone.length();
        return phone.substring(0, 3) + "****" + phone.substring(len - 3);
    }
}
