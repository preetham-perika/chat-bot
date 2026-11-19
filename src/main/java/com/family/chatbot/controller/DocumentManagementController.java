package com.family.chatbot.controller;

import com.family.chatbot.config.SecurityProperties;
import com.family.chatbot.dto.StandardApiResponse;
import com.family.chatbot.model.AuditLog;
import com.family.chatbot.model.DocumentItem;
import com.family.chatbot.model.DocumentType;
import com.family.chatbot.model.FamilyMember;
import com.family.chatbot.model.UserRole;
import com.family.chatbot.service.AuditLogService;
import com.family.chatbot.service.DocumentStorageService;
import com.family.chatbot.service.WhitelistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Web Management & Document Retrieval REST Controller.
 * Provides authenticated (X-API-KEY), number-bias-protected REST APIs to access files, stats, and audit logs.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Document Vault & Management APIs", description = "REST endpoints for managing documents, downloads, analytics, and simulator chat")
public class DocumentManagementController {

    private final DocumentStorageService storageService;
    private final WhitelistService whitelistService;
    private final AuditLogService auditLogService;
    private final com.family.chatbot.service.BotCommandService botCommandService;
    private final com.family.chatbot.service.UserAnalyticsService userAnalyticsService;
    private final SecurityProperties securityProperties;

    public DocumentManagementController(DocumentStorageService storageService,
                                        WhitelistService whitelistService,
                                        AuditLogService auditLogService,
                                        com.family.chatbot.service.BotCommandService botCommandService,
                                        com.family.chatbot.service.UserAnalyticsService userAnalyticsService) {
        this(storageService, whitelistService, auditLogService, botCommandService, userAnalyticsService, new SecurityProperties());
    }

    @Autowired
    public DocumentManagementController(DocumentStorageService storageService,
                                        WhitelistService whitelistService,
                                        AuditLogService auditLogService,
                                        com.family.chatbot.service.BotCommandService botCommandService,
                                        com.family.chatbot.service.UserAnalyticsService userAnalyticsService,
                                        @Autowired(required = false) SecurityProperties securityProperties) {
        this.storageService = storageService;
        this.whitelistService = whitelistService;
        this.auditLogService = auditLogService;
        this.botCommandService = botCommandService;
        this.userAnalyticsService = userAnalyticsService;
        this.securityProperties = (securityProperties != null) ? securityProperties : new SecurityProperties();
    }

    private boolean isApiKeyAuthorized(String apiKey) {
        if (!securityProperties.isEnableApiKeyAuth()) {
            return true;
        }
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                securityProperties.getApiKey().getBytes(StandardCharsets.UTF_8),
                apiKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Health check endpoint (Public).
     */
    @Operation(summary = "Health Check", description = "Public health check endpoint returning system status")
    @ApiResponse(responseCode = "200", description = "Application is healthy and running")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "WhatsApp Family Document Vault",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * List documents with API Key and number-bias protection:
     * - Admin gets all documents, or filters by user phone number.
     * - Member gets only their own documents.
     */
    @Operation(summary = "List Documents", description = "List documents with Number-Bias protection: Admin sees all files or filters by user; Member sees only their own files")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of accessible documents retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: Invalid or missing X-API-KEY"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Caller phone not whitelisted")
    })
    @GetMapping("/documents")
    public ResponseEntity<?> listDocuments(
            @Parameter(description = "Optional filter by user phone number (Admin can view any user's documents)")
            @RequestParam(value = "userPhone", required = false) String userPhone,
            @Parameter(description = "Caller phone number with country code (e.g. 918790208445 for Admin, 919876543211 for Member)")
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhoneHeader,
            @RequestParam(value = "callerPhone", required = false) String callerPhoneParam,
            @Parameter(description = "Master REST API Key (Default: chat-vault-secret-admin-key-2026)")
            @RequestHeader(value = "X-API-KEY", required = false) String apiKeyHeader,
            @RequestParam(value = "apiKey", required = false) String apiKeyParam) {

        String callerPhone = (callerPhoneHeader != null && !callerPhoneHeader.isBlank()) ? callerPhoneHeader : callerPhoneParam;
        String apiKey = (apiKeyHeader != null && !apiKeyHeader.isBlank()) ? apiKeyHeader : apiKeyParam;

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        if (callerPhone == null || !whitelistService.isAuthorized(callerPhone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied: Missing or unauthorized phone number in X-Caller-Phone header"));
        }

        boolean isAdmin = whitelistService.isAdmin(callerPhone);
        List<DocumentItem> documents;
        if (isAdmin) {
            if (userPhone != null && !userPhone.isBlank()) {
                String normalizedUser = WhitelistService.normalize(userPhone);
                documents = storageService.findDocumentsByUser(normalizedUser);
            } else {
                documents = storageService.findAllDocuments();
            }
        } else {
            documents = storageService.findDocumentsByUser(callerPhone);
        }
        return ResponseEntity.ok(documents);
    }

    public ResponseEntity<?> listDocuments(String callerPhone) {
        return listDocuments(null, callerPhone, null, securityProperties.getApiKey(), null);
    }

    public ResponseEntity<?> listDocuments(String callerPhone, String apiKey) {
        return listDocuments(null, callerPhone, null, apiKey, null);
    }

    /**
     * Upload a document or photo directly from local computer or phone.
     * Backed by AES-256 encryption at rest, duplicate detection, and Google Drive cloud syncing.
     * Master Admin can upload ANY document with ANY custom name directly to ANY user's folder in Google Drive!
     */
    @Operation(summary = "Upload Document or Picture", description = "Upload a document/image directly from local system or phone. Admin can upload any document with any custom name to any user's folder in Google Drive.")
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "customFileName", required = false) String customFileName,
            @RequestParam(value = "targetPhone", required = false) String targetPhone,
            @RequestParam(value = "targetFolder", required = false) String targetFolder,
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhone,
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardApiResponse.failed(401, "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        if (callerPhone == null || !whitelistService.isAuthorized(callerPhone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(StandardApiResponse.failed(403, "Access Denied: Missing or unauthorized phone number in X-Caller-Phone header"));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(StandardApiResponse.failed(400, "File is required and cannot be empty"));
        }

        try {
            byte[] fileBytes = file.getBytes();
            String originalFilename = (customFileName != null && !customFileName.isBlank())
                    ? customFileName.trim()
                    : (file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded_document");
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

            DocumentType type = DocumentType.DOCUMENT;
            if (contentType.startsWith("image/")) {
                type = DocumentType.IMAGE;
            } else if (contentType.startsWith("audio/")) {
                type = DocumentType.AUDIO;
            }

            boolean isAdmin = whitelistService.isAdmin(callerPhone);
            String ownerPhone = callerPhone;
            String ownerName = whitelistService.getUserDisplayName(callerPhone);
            String folderName = whitelistService.getUserFolderName(callerPhone);

            // Master Admin superpower: Admin can upload any document with any custom name to any user's folder!
            if (isAdmin && targetPhone != null && !targetPhone.isBlank()) {
                String normalizedTarget = WhitelistService.normalize(targetPhone);
                if (whitelistService.isAuthorized(normalizedTarget)) {
                    ownerPhone = normalizedTarget;
                    ownerName = whitelistService.getUserDisplayName(normalizedTarget);
                    folderName = whitelistService.getUserFolderName(normalizedTarget);
                }
            } else if (isAdmin && targetFolder != null && !targetFolder.isBlank()) {
                folderName = targetFolder.replaceAll("[^a-zA-Z0-9_-]", "_");
            }

            DocumentItem docItem = storageService.storeMediaFile(
                    fileBytes,
                    originalFilename,
                    contentType,
                    type,
                    caption != null && !caption.isBlank() ? caption : originalFilename,
                    ownerPhone,
                    ownerName,
                    folderName,
                    isAdmin // Auto-approved and synced directly to target Drive folder if uploaded by Admin!
            );

            auditLogService.record(
                    com.family.chatbot.model.AuditEventType.FILE_UPLOAD,
                    com.family.chatbot.model.AuditStatus.SUCCESS,
                    callerPhone,
                    isAdmin ? "ADMIN" : "MEMBER",
                    "WEB_FILE_UPLOAD",
                    docItem.getDocId(),
                    String.format("Uploaded document '%s' to folder [%s] for user +%s", originalFilename, folderName, ownerPhone)
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(StandardApiResponse.success(201, "Document uploaded successfully to folder: " + folderName, docItem));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.failed(500, "File upload failed: " + e.getMessage()));
        }
    }

    /**
     * Download or view a stored document with AES-256 decryption and strict ownership validation.
     * Master Admin can view or download ANY user's documents.
     */
    @Operation(summary = "Download or View Document", description = "Download or view document with AES-256 decryption. Admin can view/download any user's file. Regular members can only download their own files.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document downloaded or viewed successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: Invalid or missing X-API-KEY"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Document belongs to another member"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping(value = {"/documents/{docId}/download", "/documents/{docId}/view"})
    public ResponseEntity<?> downloadDocument(
            @Parameter(description = "Document ID to download or view (e.g. DOC-1001)")
            @PathVariable String docId,
            @Parameter(description = "Whether to view inline (preview) or download attachment")
            @RequestParam(value = "inline", required = false, defaultValue = "false") boolean inline,
            @Parameter(description = "Caller phone number with country code")
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhoneHeader,
            @RequestParam(value = "callerPhone", required = false) String callerPhoneParam,
            @Parameter(description = "Master REST API Key")
            @RequestHeader(value = "X-API-KEY", required = false) String apiKeyHeader,
            @RequestParam(value = "apiKey", required = false) String apiKeyParam,
            HttpServletRequest request) {

        String callerPhone = (callerPhoneHeader != null && !callerPhoneHeader.isBlank()) ? callerPhoneHeader : callerPhoneParam;
        String apiKey = (apiKeyHeader != null && !apiKeyHeader.isBlank()) ? apiKeyHeader : apiKeyParam;

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        if (callerPhone == null || !whitelistService.isAuthorized(callerPhone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied: Unauthorized caller"));
        }

        Optional<DocumentItem> opt = storageService.findByDocId(docId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Document not found with ID: " + docId));
        }

        DocumentItem item = opt.get();
        boolean isAdmin = whitelistService.isAdmin(callerPhone);

        if (!storageService.canAccess(item, callerPhone, isAdmin)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Number-Bias Security: Document belongs to another family member"));
        }

        File file = new File(item.getStoredPath());
        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Physical file missing on server storage"));
        }

        try {
            byte[] decrypted = storageService.loadDecryptedBytes(item);
            Resource resource = new ByteArrayResource(decrypted);
            String contentType = item.getMimeType() != null ? item.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

            boolean isView = inline || (request != null && request.getRequestURI() != null && request.getRequestURI().endsWith("/view"));
            String dispositionType = isView ? "inline" : "attachment";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename=\"" + item.getOriginalFileName() + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to read and decrypt document: " + e.getMessage()));
        }
    }

    public ResponseEntity<?> downloadDocument(String docId, String callerPhone) {
        return downloadDocument(docId, false, callerPhone, null, securityProperties.getApiKey(), null, null);
    }

    public ResponseEntity<?> downloadDocument(String docId, String callerPhone, String apiKey) {
        return downloadDocument(docId, false, callerPhone, null, apiKey, null, null);
    }

    /**
     * Delete a stored document (Master Admin only).
     */
    @Operation(summary = "Delete Document (Admin Only)", description = "Permanently delete a document. Restricted strictly to Master Admin.")
    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String docId,
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhoneHeader,
            @RequestParam(value = "callerPhone", required = false) String callerPhoneParam,
            @RequestHeader(value = "X-API-KEY", required = false) String apiKeyHeader,
            @RequestParam(value = "apiKey", required = false) String apiKeyParam) {

        String callerPhone = (callerPhoneHeader != null && !callerPhoneHeader.isBlank()) ? callerPhoneHeader : callerPhoneParam;
        String apiKey = (apiKeyHeader != null && !apiKeyHeader.isBlank()) ? apiKeyHeader : apiKeyParam;

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardApiResponse.failed(401, "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        if (callerPhone == null || !whitelistService.isAdmin(callerPhone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(StandardApiResponse.failed(403, "Access Denied: Only Master Admin can delete documents"));
        }

        try {
            boolean deleted = storageService.deleteDocument(docId, callerPhone, true);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(StandardApiResponse.failed(404, "Document not found with ID: " + docId));
            }

            auditLogService.record(
                    com.family.chatbot.model.AuditEventType.FILE_ACCESS,
                    com.family.chatbot.model.AuditStatus.SUCCESS,
                    callerPhone,
                    "ADMIN",
                    "DELETE_DOCUMENT",
                    docId,
                    "Admin deleted document: " + docId
            );

            return ResponseEntity.ok(StandardApiResponse.success(200, "Document " + docId + " deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.failed(500, "Delete failed: " + e.getMessage()));
        }
    }

    /**
     * Storage statistics API.
     */
    @Operation(summary = "Storage Statistics", description = "Retrieve storage statistics (total items, documents, images, notes, and file sizes)")
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(
            @Parameter(description = "Caller phone number")
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhone,
            @Parameter(description = "Master REST API Key")
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        if (callerPhone == null || !whitelistService.isAuthorized(callerPhone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied"));
        }

        boolean isAdmin = whitelistService.isAdmin(callerPhone);
        return ResponseEntity.ok(storageService.getStats(callerPhone, isAdmin));
    }

    public ResponseEntity<?> getStats(String callerPhone) {
        return getStats(callerPhone, securityProperties.getApiKey());
    }

    /**
     * Dynamic Statistics API with issue and call counts.
     */
    @Operation(summary = "Dynamic Statistics & Activity Metrics", description = "Retrieve storage statistics together with dynamic issue and call/login metrics")
    @GetMapping("/stats/dynamic")
    public ResponseEntity<?> getDynamicStats(
            @Parameter(description = "Caller phone number")
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhone,
            @Parameter(description = "Master REST API Key")
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        boolean isAdmin = callerPhone != null && whitelistService.isAdmin(callerPhone);
        DocumentStorageService.StorageStats stats = storageService.getStats(callerPhone != null ? callerPhone : "", isAdmin);

        long issueCount = auditLogService.getReportedIssueCount();
        long callContactCount = auditLogService.getContactCount();
        long sessionCount = auditLogService.getLoginCount();
        int memberCount = whitelistService.getAllActiveMembers().size();

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("totalItems", stats.totalItems());
        response.put("totalDocuments", stats.totalDocuments());
        response.put("totalImages", stats.totalImages());
        response.put("totalNotes", stats.totalNotes());
        response.put("totalSizeBytes", stats.totalSizeBytes());
        response.put("formattedSize", stats.getFormattedSize());
        response.put("isAdminView", isAdmin);
        response.put("issueCount", issueCount);
        response.put("callContactCount", callContactCount);
        response.put("sessionCount", sessionCount);
        response.put("memberCount", memberCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Reported Issues Inbox API (Admin-only).
     */
    @Operation(summary = "Reported User Issues Inbox (Admin Only)", description = "Retrieve all reported issues from family members with sender details and timestamp")
    @GetMapping("/issues")
    public ResponseEntity<?> getReportedIssues(
            @Parameter(description = "Admin caller phone number")
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhone,
            @Parameter(description = "Master REST API Key")
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        List<AuditLog> issues = auditLogService.getReportedIssues();
        List<Map<String, Object>> result = new java.util.ArrayList<>();

        for (AuditLog log : issues) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", log.getId());
            map.put("senderPhone", log.getSenderPhoneNumber());
            map.put("senderName", whitelistService.getUserDisplayName(log.getSenderPhoneNumber()));
            map.put("timestamp", log.getTimestamp());
            map.put("details", log.getDetails());
            map.put("status", log.getStatus().name());
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Audit logs API (Admin-only).
     */
    @Operation(summary = "System Audit Logs (Admin Only)", description = "Retrieve complete audit logs for security, commands, and access events. Strictly Master Admin only.")
    @GetMapping("/logs")
    public ResponseEntity<?> getAuditLogs(
            @Parameter(description = "Admin caller phone number (918790208445)")
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhone,
            @Parameter(description = "Master REST API Key")
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        if (callerPhone == null || !whitelistService.isAdmin(callerPhone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied: Master Admin privileges required to view audit logs"));
        }

        return ResponseEntity.ok(auditLogService.getRecentLogs());
    }

    public ResponseEntity<?> getAuditLogs(String callerPhone) {
        return getAuditLogs(callerPhone, securityProperties.getApiKey());
    }

    /**
     * User Activity Analysis & Usage Graphs API (Admin-only).
     */
    @Operation(summary = "User Activity Analytics & Graphs (Admin Only)", description = "Retrieve user login counts, service usage breakdown, and top active users. Strictly Master Admin only.")
    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics(
            @Parameter(description = "Admin caller phone number (918790208445)")
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhone,
            @Parameter(description = "Master REST API Key")
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        if (callerPhone == null || !whitelistService.isAdmin(callerPhone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied: Master Admin privileges required to view user analytics"));
        }

        return ResponseEntity.ok(userAnalyticsService.getAnalyticsReport());
    }

    public ResponseEntity<?> getAnalytics(String callerPhone) {
        return getAnalytics(callerPhone, securityProperties.getApiKey());
    }

    /**
     * Local Developer Simulator API.
     * Allows testing chatbot messages directly via curl, Postman, or Web UI without needing WhatsApp/Meta!
     */
    @Operation(summary = "Developer Simulator Chat", description = "Simulate WhatsApp messages directly from Postman or Web UI without WhatsApp/Meta credentials.")
    @PostMapping("/test/chat")
    public ResponseEntity<?> testChat(
            @RequestBody Map<String, String> request,
            @Parameter(description = "Master REST API Key")
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Invalid or missing X-API-KEY header",
                                 "statusCode", 401, "status", "FAILED"));
        }

        String phone = request.getOrDefault("senderPhone", "918790208445");
        String message = request.getOrDefault("message", "hii");

        boolean authorized = whitelistService.isAuthorized(phone);
        String name = whitelistService.getUserDisplayName(phone);
        String role = whitelistService.getRole(phone).name();

        String reply;
        if (!authorized) {
            reply = "⛔ Access Denied: This WhatsApp bot is private and restricted to authorized family members only.";
        } else {
            reply = botCommandService.processCommand(message, phone, name);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("statusCode", 200);
        response.put("status", "SUCCESS");
        response.put("message", "Chat command processed successfully");
        response.put("senderPhone", phone);
        response.put("senderName", name);
        response.put("role", role);
        response.put("inputMessage", message);
        response.put("botReply", reply);
        response.put("data", Map.of(
                "reply", reply,
                "senderPhone", phone,
                "senderName", name,
                "role", role
        ));

        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> testChat(Map<String, String> request) {
        return testChat(request, securityProperties.getApiKey());
    }

    // =========================================================================
    // DYNAMIC WHITELIST & DOCUMENT SERVICE MANAGEMENT REST ENDPOINTS (ADMIN ONLY)
    // =========================================================================

    /**
     * List all active family members and their assigned services.
     */
    @Operation(summary = "List Whitelist Members (Admin Only)", description = "Retrieve list of all active registered family members, their roles, and assigned services.")
    @GetMapping("/whitelist")
    public ResponseEntity<?> getWhitelist(
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhone,
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardApiResponse.failed(401, "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        if (callerPhone == null || !whitelistService.isAdmin(callerPhone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(StandardApiResponse.failed(403, "Access Denied: Master Admin privileges required"));
        }

        List<FamilyMember> members = whitelistService.getAllActiveMembers();
        return ResponseEntity.ok(StandardApiResponse.success(200, "Active family members retrieved successfully", members));
    }

    /**
     * Dynamically add or register a new family member.
     */
    @Operation(summary = "Add Whitelist Member (Admin Only)", description = "Dynamically register a new family member with custom name, Indian phone number, role, and assigned services.")
    @PostMapping("/whitelist")
    public ResponseEntity<?> addWhitelistMember(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhone,
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardApiResponse.failed(401, "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        if (callerPhone == null || !whitelistService.isAdmin(callerPhone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(StandardApiResponse.failed(403, "Access Denied: Master Admin privileges required"));
        }

        String name = (String) payload.get("name");
        String phone = (String) payload.get("phone");
        if (phone == null) phone = (String) payload.get("phoneNumber");
        String roleStr = (String) payload.getOrDefault("role", "MEMBER");
        String services = (String) payload.get("services");

        if (name == null || name.isBlank() || phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(StandardApiResponse.failed(400, "Name and Indian Phone Number are required"));
        }

        try {
            UserRole role = UserRole.valueOf(roleStr.toUpperCase());
            FamilyMember member = whitelistService.addFamilyMember(name, phone, role);
            if (services != null && !services.isBlank()) {
                whitelistService.updateUserServices(member.getPhoneNumber(),
                        Set.of(services.split(",")));
            }

            auditLogService.record(com.family.chatbot.model.AuditEventType.COMMAND,
                    com.family.chatbot.model.AuditStatus.SUCCESS, callerPhone, "ADMIN",
                    "ADD_WHITELIST_API", null, "Added family member via REST: " + name + " (+" + member.getPhoneNumber() + ")");

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(StandardApiResponse.success(201, "Family member whitelisted successfully", member));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(StandardApiResponse.failed(400, "Failed to add member: " + e.getMessage()));
        }
    }

    /**
     * Dynamically remove/deactivate a family member.
     */
    @Operation(summary = "Remove Whitelist Member (Admin Only)", description = "Dynamically deactivate a family member by phone number.")
    @DeleteMapping("/whitelist/{phone}")
    public ResponseEntity<?> removeWhitelistMember(
            @PathVariable String phone,
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhone,
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardApiResponse.failed(401, "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        if (callerPhone == null || !whitelistService.isAdmin(callerPhone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(StandardApiResponse.failed(403, "Access Denied: Master Admin privileges required"));
        }

        try {
            boolean removed = whitelistService.removeFamilyMember(phone);
            if (removed) {
                auditLogService.record(com.family.chatbot.model.AuditEventType.COMMAND,
                        com.family.chatbot.model.AuditStatus.SUCCESS, callerPhone, "ADMIN",
                        "REMOVE_WHITELIST_API", null, "Deactivated member phone: +" + phone);
                return ResponseEntity.ok(StandardApiResponse.success(200, "Family member deactivated successfully", null));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(StandardApiResponse.failed(404, "Member not found with phone: " + phone));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(StandardApiResponse.failed(400, "Failed to remove member: " + e.getMessage()));
        }
    }

    /**
     * Get allowed services for a user.
     */
    @Operation(summary = "Get User Document Services", description = "Retrieve the list of allowed document services for a specific family member.")
    @GetMapping("/whitelist/{phone}/services")
    public ResponseEntity<?> getUserServices(
            @PathVariable String phone,
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhone,
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardApiResponse.failed(401, "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        Set<String> services = whitelistService.getUserServices(phone);
        return ResponseEntity.ok(StandardApiResponse.success(200, "Allowed services retrieved", services));
    }

    /**
     * Dynamically update allowed services for a user.
     */
    @Operation(summary = "Update User Document Services (Admin Only)", description = "Assign or revoke document services (Aadhaar, PAN, DL, etc.) for a specific user.")
    @PutMapping("/whitelist/{phone}/services")
    public ResponseEntity<?> updateUserServices(
            @PathVariable String phone,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Caller-Phone", required = false) String callerPhone,
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        if (!isApiKeyAuthorized(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardApiResponse.failed(401, "Unauthorized: Invalid or missing X-API-KEY header"));
        }

        if (callerPhone == null || !whitelistService.isAdmin(callerPhone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(StandardApiResponse.failed(403, "Access Denied: Master Admin privileges required"));
        }

        @SuppressWarnings("unchecked")
        List<String> serviceList = (List<String>) payload.get("services");
        Set<String> serviceSet = (serviceList != null) ? Set.copyOf(serviceList) : Set.of();

        boolean updated = whitelistService.updateUserServices(phone, serviceSet);
        if (updated) {
            return ResponseEntity.ok(StandardApiResponse.success(200, "User document services updated successfully", serviceSet));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(StandardApiResponse.failed(404, "Member not found with phone: " + phone));
        }
    }

    /**
     * List all available document service templates in the vault.
     */
    @Operation(summary = "List Available Document Services", description = "Returns available system document services that can be assigned to users.")
    @GetMapping("/services")
    public ResponseEntity<?> getAvailableServices() {
        List<Map<String, String>> services = List.of(
                Map.of("key", "AADHAAR", "name", "Aadhaar Card", "digit", "1"),
                Map.of("key", "PAN", "name", "PAN Card", "digit", "2"),
                Map.of("key", "DRIVING_LICENSE", "name", "Driving License", "digit", "3"),
                Map.of("key", "PASSPORT", "name", "Passport", "digit", "4"),
                Map.of("key", "HEALTH", "name", "Health / Insurance", "digit", "5"),
                Map.of("key", "OTHER", "name", "Other Documents", "digit", "6"),
                Map.of("key", "MARKSHEET", "name", "Educational Marksheets", "digit", "custom"),
                Map.of("key", "VOTER_ID", "name", "Voter ID Card", "digit", "custom"),
                Map.of("key", "PROPERTY_TAX", "name", "Property Tax / Utility Bill", "digit", "custom")
        );
        return ResponseEntity.ok(StandardApiResponse.success(200, "Available services catalog", services));
    }
}
