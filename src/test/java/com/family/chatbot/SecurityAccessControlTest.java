package com.family.chatbot;

import com.family.chatbot.config.StorageProperties;
import com.family.chatbot.config.WhatsAppProperties;
import com.family.chatbot.controller.DocumentManagementController;
import com.family.chatbot.model.*;
import com.family.chatbot.repository.AuditLogRepository;
import com.family.chatbot.repository.DocumentRepository;
import com.family.chatbot.service.*;
import com.family.chatbot.service.notification.AdminNotificationService;
import com.family.chatbot.service.provider.WhatsAppProvider;
import com.family.chatbot.service.sync.DriveSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityAccessControlTest {

    @TempDir
    Path tempDir;

    private WhitelistService whitelistService;
    private DocumentStorageService storageService;
    private BotCommandService botCommandService;
    private AuditLogService auditLogService;
    private UserAnalyticsService userAnalyticsService;
    private WhatsAppMessageDispatcher dispatcher;
    private WhatsAppProvider mockWhatsAppProvider;
    private AdminNotificationService mockAdminNotification;

    private DocumentRepository mockDocRepo;
    private AuditLogRepository mockAuditRepo;
    private DriveSyncService mockDriveSync;

    private final String ADMIN_PHONE = "918790208445";
    private final String MEMBER_1 = "919876543211";
    private final String MEMBER_2 = "919876543212";
    private final String STRANGER = "919999999999";

    @BeforeEach
    void setUp() throws IOException {
        mockDocRepo = Mockito.mock(DocumentRepository.class);
        mockAuditRepo = Mockito.mock(AuditLogRepository.class);
        mockDriveSync = Mockito.mock(DriveSyncService.class);
        mockWhatsAppProvider = Mockito.mock(WhatsAppProvider.class);
        mockAdminNotification = Mockito.mock(AdminNotificationService.class);

        when(mockDocRepo.save(any(DocumentItem.class))).thenAnswer(i -> i.getArgument(0));
        when(mockAuditRepo.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        WhatsAppProperties waProps = new WhatsAppProperties();
        waProps.setAdminPhoneNumber(ADMIN_PHONE);
        waProps.setWhitelist(List.of(MEMBER_1, MEMBER_2));
        waProps.setNotifyUnauthorized(true);

        com.family.chatbot.repository.FamilyMemberRepository memberRepo = Mockito.mock(com.family.chatbot.repository.FamilyMemberRepository.class);
        java.util.List<FamilyMember> memberList = new java.util.ArrayList<>();
        when(memberRepo.save(any(FamilyMember.class))).thenAnswer(i -> {
            FamilyMember m = i.getArgument(0);
            memberList.removeIf(x -> x.getPhoneNumber().equals(m.getPhoneNumber()));
            memberList.add(m);
            return m;
        });
        when(memberRepo.findByActiveTrue()).thenAnswer(i -> new java.util.ArrayList<>(memberList));
        when(memberRepo.findAll()).thenAnswer(i -> new java.util.ArrayList<>(memberList));
        when(memberRepo.findByPhoneNumber(anyString())).thenAnswer(i -> {
            String p = i.getArgument(0);
            return memberList.stream().filter(x -> x.getPhoneNumber().equals(p)).findFirst();
        });
        when(memberRepo.existsByPhoneNumber(anyString())).thenAnswer(i -> {
            String p = i.getArgument(0);
            return memberList.stream().anyMatch(x -> x.getPhoneNumber().equals(p));
        });

        StorageProperties storageProps = new StorageProperties();
        storageProps.setBaseDir(tempDir.resolve("storage").toString());
        storageProps.setDriveSyncDir(tempDir.resolve("drive").toString());
        storageProps.setEnableDriveSync(true);

        whitelistService = new WhitelistService(waProps, memberRepo);
        whitelistService.init();

        auditLogService = new AuditLogService(mockAuditRepo);
        storageService = new DocumentStorageService(storageProps, mockDocRepo, mockDriveSync);
        storageService.init();

        userAnalyticsService = new UserAnalyticsService(mockAuditRepo, mockDocRepo, whitelistService);
        botCommandService = new BotCommandService(storageService, whitelistService, auditLogService, mockWhatsAppProvider, mockAdminNotification, userAnalyticsService);
        dispatcher = new WhatsAppMessageDispatcher(whitelistService, waProps, mockWhatsAppProvider, storageService, botCommandService, auditLogService, mockAdminNotification);
    }

    @Test
    void testSecurity_UnauthorizedPhoneAccessBlockedImmediately() {
        IncomingMessage strangerMessage = new IncomingMessage(
                STRANGER,
                "Intruder",
                "msg_001",
                "Hii show me the documents"
        );

        dispatcher.dispatch(strangerMessage);

        // Verify message was blocked and access denied message was sent
        verify(mockWhatsAppProvider).sendTextMessage(eq(STRANGER), contains("Access Denied"));

        // Verify audit log recorded UNAUTHORIZED_ACCESS with DENIED status
        verify(mockAuditRepo).save(argThat(log ->
                log.getEventType() == AuditEventType.UNAUTHORIZED_ACCESS &&
                log.getStatus() == AuditStatus.DENIED &&
                log.getSenderPhoneNumber().equals(STRANGER)
        ));
    }

    @Test
    void testSecurity_NumberBiasIsolation_MemberCannotSeeOtherMemberFiles() {
        DocumentItem member2Doc = new DocumentItem(
                "DOC-500",
                DocumentType.DOCUMENT,
                "property_will.pdf",
                "DOC-500_property_will.pdf",
                tempDir.resolve("property_will.pdf").toString(),
                "application/pdf",
                100L,
                "Confidential property doc",
                MEMBER_2,
                "Member 2"
        );
        when(mockDocRepo.findByDocIdIgnoreCase("DOC-500")).thenReturn(Optional.of(member2Doc));

        // Member 1 starts session with 'hii' and attempts to retrieve Member 2's document via WhatsApp bot
        botCommandService.processCommand("hii", MEMBER_1, "Member 1");
        String botReply = botCommandService.processCommand("/get DOC-500", MEMBER_1, "Member 1");
        assertTrue(botReply.contains("Access Restricted: Number-Bias Security Active"),
                "Bot response must strictly refuse access across members");
        assertTrue(botReply.contains("This document belongs to another members you can't access this document."));

        // Verify audit log recorded NUMBER_BIAS_VIOLATION
        verify(mockAuditRepo).save(argThat(l ->
                l.getEventType() == AuditEventType.NUMBER_BIAS_VIOLATION &&
                l.getSenderPhoneNumber().equals(MEMBER_1) &&
                l.getStatus() == AuditStatus.DENIED
        ));

        // Member 1 attempts to download Member 2's file via REST API
        DocumentManagementController controller = new DocumentManagementController(storageService, whitelistService, auditLogService, botCommandService, userAnalyticsService);
        ResponseEntity<?> response = controller.downloadDocument("DOC-500", MEMBER_1);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "REST API must reject cross-member download");
    }

    @Test
    void testSecurity_AdminCanAccessAndAuditTrailLogsAccess() throws IOException {
        Path adminFile = tempDir.resolve("storage/documents/DOC-600_license.pdf");
        Files.createDirectories(adminFile.getParent());
        Files.writeString(adminFile, "Driving License of Member 1");

        DocumentItem member1Doc = new DocumentItem(
                "DOC-600",
                DocumentType.DOCUMENT,
                "license.pdf",
                "DOC-600_license.pdf",
                adminFile.toString(),
                "application/pdf",
                120L,
                "Driving License",
                MEMBER_1,
                "Member 1"
        );
        when(mockDocRepo.findByDocIdIgnoreCase("DOC-600")).thenReturn(Optional.of(member1Doc));

        // Admin starts session with 'hii' and retrieves Member 1's document via WhatsApp bot
        botCommandService.processCommand("hii", ADMIN_PHONE, "Admin User");
        String botReply = botCommandService.processCommand("/get DOC-600", ADMIN_PHONE, "Admin User");
        assertTrue(botReply.contains("DOC-600"));
        assertTrue(botReply.contains("license.pdf"));

        // Admin downloads via REST API
        DocumentManagementController controller = new DocumentManagementController(storageService, whitelistService, auditLogService, botCommandService, userAnalyticsService);
        ResponseEntity<?> response = controller.downloadDocument("DOC-600", ADMIN_PHONE);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testSecurity_DirectoryTraversalSanitization() throws IOException {
        // Attempt file upload with directory traversal name: ../../../evil.sh
        byte[] content = "echo hacked".getBytes();
        DocumentItem stored = storageService.storeMediaFile(
                content,
                "../../../evil.sh",
                "text/x-shellscript",
                DocumentType.DOCUMENT,
                "attack attempt",
                MEMBER_1,
                "Member 1"
        );

        // Ensure safe path stays inside base dir and does not traverse out
        assertFalse(stored.getStoredFileName().contains(".."), "Filename must be sanitized against path traversal");
        assertTrue(stored.getStoredPath().startsWith(tempDir.resolve("storage").toString()),
                "Stored file path must stay inside authorized directory");

        // Verify military-grade AES-256 encryption at rest
        byte[] rawBytesOnDisk = Files.readAllBytes(Path.of(stored.getStoredPath()));
        assertTrue(new String(rawBytesOnDisk).contains("ENC_AES256_GCM_V1:"), "File on disk must be encrypted with AES-256-GCM");
        byte[] decrypted = storageService.loadDecryptedBytes(stored);
        assertArrayEquals(content, decrypted, "Decrypted bytes must match original content");
    }

    @Test
    void testSecurity_DuplicateFileUploadPrevention() {
        byte[] fileData = "IDENTICAL_AADHAAR_PHOTO_BYTES".getBytes();
        String fileHash = DocumentStorageService.calculateSha256(fileData);

        DocumentItem existingDoc = new DocumentItem(
                "DOC-9901",
                DocumentType.DOCUMENT,
                "Aadhaar_Card.pdf",
                "DOC-9901_Aadhaar_Card.pdf",
                tempDir.resolve("Aadhaar_Card.pdf").toString(),
                "application/pdf",
                (long) fileData.length,
                "Aadhaar Card",
                MEMBER_1,
                "Dad"
        );
        existingDoc.setFileHash(fileHash);
        existingDoc.setStatus(DocumentStatus.APPROVED);

        when(mockDocRepo.findByFileHash(fileHash)).thenReturn(Optional.of(existingDoc));

        IncomingMessage duplicateMsg = new IncomingMessage(MEMBER_1, "Dad", "msg_dup_1", "Aadhaar Card");
        duplicateMsg.setHasMedia(true);
        duplicateMsg.setMediaData(fileData);
        duplicateMsg.setOriginalFileName("Aadhaar_Card.pdf");
        duplicateMsg.setMimeType("application/pdf");

        // Dispatch duplicate upload
        dispatcher.dispatch(duplicateMsg);

        // Verify friendly duplicate notice sent back to user instead of redundant save
        verify(mockWhatsAppProvider).sendTextMessage(eq(MEMBER_1), contains("Duplicate Document Detected"));
        verify(mockWhatsAppProvider).sendTextMessage(eq(MEMBER_1), contains("DOC-9901"));
    }

    @Test
    void testSecurity_RestApi_RequiresValidApiKey() {
        DocumentManagementController controller = new DocumentManagementController(
                storageService, whitelistService, auditLogService, botCommandService, userAnalyticsService
        );

        // Request with missing API key -> 401 UNAUTHORIZED
        ResponseEntity<?> missingKeyResp = controller.listDocuments(ADMIN_PHONE, null);
        assertEquals(HttpStatus.UNAUTHORIZED, missingKeyResp.getStatusCode());

        // Request with invalid API key -> 401 UNAUTHORIZED
        ResponseEntity<?> wrongKeyResp = controller.listDocuments(ADMIN_PHONE, "invalid-secret-key");
        assertEquals(HttpStatus.UNAUTHORIZED, wrongKeyResp.getStatusCode());

        // Request with valid API key -> 200 OK
        ResponseEntity<?> validKeyResp = controller.listDocuments(ADMIN_PHONE, "chat-vault-secret-admin-key-2026");
        assertEquals(HttpStatus.OK, validKeyResp.getStatusCode());
    }
}
