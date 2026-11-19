package com.family.chatbot;

import com.family.chatbot.config.StorageProperties;
import com.family.chatbot.config.WhatsAppProperties;
import com.family.chatbot.model.*;
import com.family.chatbot.repository.AuditLogRepository;
import com.family.chatbot.repository.DocumentRepository;
import com.family.chatbot.repository.FamilyMemberRepository;
import com.family.chatbot.service.*;
import com.family.chatbot.service.notification.AdminNotificationService;
import com.family.chatbot.service.provider.WhatsAppProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class BotCommandServiceTest {

    private BotCommandService botCommandService;
    private DocumentStorageService storageService;
    private WhitelistService whitelistService;
    private AuditLogService auditLogService;
    private UserAnalyticsService userAnalyticsService;
    private WhatsAppProvider whatsAppProvider;
    private AdminNotificationService adminNotificationService;

    private DocumentRepository documentRepository;
    private AuditLogRepository auditLogRepository;
    private com.family.chatbot.service.sync.DriveSyncService driveSyncService;

    @TempDir
    Path tempDir;

    private final String ADMIN_PHONE = "918790208445";
    private final String MEMBER_A_PHONE = "919876543211";
    private final String MEMBER_B_PHONE = "919876543212";

    @BeforeEach
    void setUp() throws IOException {
        documentRepository = Mockito.mock(DocumentRepository.class);
        auditLogRepository = Mockito.mock(AuditLogRepository.class);
        driveSyncService = Mockito.mock(com.family.chatbot.service.sync.DriveSyncService.class);
        whatsAppProvider = Mockito.mock(WhatsAppProvider.class);
        adminNotificationService = Mockito.mock(AdminNotificationService.class);

        when(documentRepository.save(any(DocumentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StorageProperties storageProps = new StorageProperties();
        storageProps.setBaseDir(tempDir.resolve("storage").toString());
        storageProps.setDriveSyncDir(tempDir.resolve("drive").toString());
        storageProps.setEnableDriveSync(true);

        storageService = new DocumentStorageService(storageProps, documentRepository, driveSyncService);
        storageService.init();

        FamilyMemberRepository memberRepo = Mockito.mock(FamilyMemberRepository.class);
        List<FamilyMember> memberList = new ArrayList<>();
        when(memberRepo.save(any(FamilyMember.class))).thenAnswer(i -> {
            FamilyMember m = i.getArgument(0);
            memberList.removeIf(x -> x.getPhoneNumber().equals(m.getPhoneNumber()));
            memberList.add(m);
            return m;
        });
        when(memberRepo.findByActiveTrue()).thenAnswer(i -> new ArrayList<>(memberList));
        when(memberRepo.findAll()).thenAnswer(i -> new ArrayList<>(memberList));
        when(memberRepo.findByPhoneNumber(anyString())).thenAnswer(i -> {
            String p = i.getArgument(0);
            return memberList.stream().filter(x -> x.getPhoneNumber().equals(p)).findFirst();
        });
        when(memberRepo.existsByPhoneNumber(anyString())).thenAnswer(i -> {
            String p = i.getArgument(0);
            return memberList.stream().anyMatch(x -> x.getPhoneNumber().equals(p));
        });

        WhatsAppProperties waProps = new WhatsAppProperties();
        waProps.setAdminPhoneNumber(ADMIN_PHONE);
        waProps.setWhitelist(List.of(MEMBER_A_PHONE, MEMBER_B_PHONE));

        whitelistService = new WhitelistService(waProps, memberRepo);
        whitelistService.init();

        auditLogService = new AuditLogService(auditLogRepository);
        userAnalyticsService = new UserAnalyticsService(auditLogRepository, documentRepository, whitelistService);

        botCommandService = new BotCommandService(
                storageService,
                whitelistService,
                auditLogService,
                whatsAppProvider,
                adminNotificationService,
                userAnalyticsService
        );

        // Pre-init active sessions for test phones
        botCommandService.processCommand("hii", ADMIN_PHONE, "Master Admin");
        botCommandService.processCommand("hii", MEMBER_A_PHONE, "Dad");
        botCommandService.processCommand("hii", MEMBER_B_PHONE, "Mom");
    }

    @Test
    void testStrictHiiSessionRequirement() {
        // End existing session first
        botCommandService.processCommand("bye", MEMBER_A_PHONE, "Dad");

        // 1. Sending "hi", "hello", "1", or random word without starting session -> POLITE PROMPT
        String hiReply = botCommandService.processCommand("hi", MEMBER_A_PHONE, "Dad");
        assertTrue(hiReply.contains("Please start the session with 'hii' only"), "Must reject 'hi' and demand 'hii'");

        String randomReply = botCommandService.processCommand("hello world", MEMBER_A_PHONE, "Dad");
        assertTrue(randomReply.contains("Please start the session with 'hii' only"), "Must reject random text without session");

        String optionBeforeHii = botCommandService.processCommand("1", MEMBER_A_PHONE, "Dad");
        assertTrue(optionBeforeHii.contains("Please start the session with 'hii' only"), "Must reject menu options before 'hii'");

        // 2. Sending case-insensitive "Hii" / "HII" / "hii" -> STARTS SESSION
        String startReply = botCommandService.processCommand("Hii", MEMBER_A_PHONE, "Dad");
        assertTrue(startReply.contains("Hii"), "Hii must successfully start the session");
        assertTrue(startReply.contains("Select a Document Service:"));

        // 3. Now options work during active session!
        String optionAfterHii = botCommandService.processCommand("1", MEMBER_A_PHONE, "Dad");
        assertTrue(optionAfterHii.contains("Aadhaar Card"), "Option 1 must work once session is active");

        // 4. Ending session with "bye" or "exit"
        String byeReply = botCommandService.processCommand("bye", MEMBER_A_PHONE, "Dad");
        assertTrue(byeReply.contains("Session Ended Successfully"));

        // 5. Subsequent option requires "hii" again
        String afterExitReply = botCommandService.processCommand("1", MEMBER_A_PHONE, "Dad");
        assertTrue(afterExitReply.contains("Please start the session with 'hii' only"));
    }

    @Test
    void testHelpMenuDisplaysProperRole() {
        // Start session first with hii
        botCommandService.processCommand("hii", ADMIN_PHONE, "Admin User");
        String adminMenu = botCommandService.processCommand("menu", ADMIN_PHONE, "Admin User");
        assertTrue(adminMenu.contains("ROLE: MASTER ADMIN"));

        botCommandService.processCommand("hii", MEMBER_A_PHONE, "Member User");
        String memberMenu = botCommandService.processCommand("menu", MEMBER_A_PHONE, "Member User");
        assertTrue(memberMenu.contains("ROLE: FAMILY MEMBER"));
    }

    @Test
    void testGreeting_Hii_DisplaysUserDrivePathAndServicesMenu() {
        String greeting = botCommandService.processCommand("Hii", MEMBER_A_PHONE, "Dad");
        assertTrue(greeting.contains("Hii"));
        assertTrue(greeting.contains("drive/family_chatbot/"));
        assertTrue(greeting.contains("1️⃣ *Aadhaar Card*"));
        assertTrue(greeting.contains("2️⃣ *PAN Card*"));
        assertTrue(greeting.contains("3️⃣ *Driving License*"));
        assertTrue(greeting.contains("/download"));
    }

    @Test
    void testCategorySelection_AadhaarFoundApproved() {
        DocumentItem aadhaar = new DocumentItem(
                "DOC-1001",
                DocumentType.DOCUMENT,
                "Aadhaar_Card.pdf",
                "DOC-1001_Aadhaar_Card.pdf",
                tempDir.resolve("Aadhaar_Card.pdf").toString(),
                "application/pdf",
                1024L,
                "Aadhaar Card",
                MEMBER_A_PHONE,
                "Dad"
        );
        aadhaar.setCategoryTag("AADHAAR");
        aadhaar.setStatus(DocumentStatus.APPROVED);

        when(documentRepository.findTop1BySenderPhoneNumberAndCategoryTagIgnoreCaseOrderByCreatedAtDesc(MEMBER_A_PHONE, "AADHAAR"))
                .thenReturn(Optional.of(aadhaar));

        // Test with "1"
        String reply1 = botCommandService.processCommand("1", MEMBER_A_PHONE, "Dad");
        assertTrue(reply1.contains("Aadhaar Card Found!"));
        assertTrue(reply1.contains("/download DOC-1001"));

        // Test with case-insensitive "aadhar"
        String replyAadhar = botCommandService.processCommand("aadhar", MEMBER_A_PHONE, "Dad");
        assertTrue(replyAadhar.contains("Aadhaar Card Found!"));

        // Test with case-insensitive "AADHAAR"
        String replyUpper = botCommandService.processCommand("AADHAAR", MEMBER_A_PHONE, "Dad");
        assertTrue(replyUpper.contains("Aadhaar Card Found!"));
    }

    @Test
    void testCategorySelection_PendingAdminApproval() {
        DocumentItem pendingAadhaar = new DocumentItem(
                "DOC-1002",
                DocumentType.DOCUMENT,
                "Aadhaar_Card.pdf",
                "DOC-1002_Aadhaar_Card.pdf",
                tempDir.resolve("Aadhaar_Card.pdf").toString(),
                "application/pdf",
                1024L,
                "Aadhaar Card",
                MEMBER_A_PHONE,
                "Dad"
        );
        pendingAadhaar.setCategoryTag("AADHAAR");
        pendingAadhaar.setStatus(DocumentStatus.PENDING_APPROVAL);

        when(documentRepository.findTop1BySenderPhoneNumberAndCategoryTagIgnoreCaseOrderByCreatedAtDesc(MEMBER_A_PHONE, "AADHAAR"))
                .thenReturn(Optional.of(pendingAadhaar));

        String reply = botCommandService.processCommand("aadhaar", MEMBER_A_PHONE, "Dad");
        assertTrue(reply.contains("Pending Admin Approval"));
        assertTrue(reply.contains("Waiting for Master Admin approval"));
    }

    @Test
    void testDeleteRestriction_RegularUserCannotDeleteDocument() {
        DocumentItem item = new DocumentItem(
                "DOC-1001",
                DocumentType.DOCUMENT,
                "passport.pdf",
                "DOC-1001_passport.pdf",
                tempDir.resolve("passport.pdf").toString(),
                "application/pdf",
                1024L,
                "Passport",
                MEMBER_A_PHONE,
                "Dad"
        );
        when(documentRepository.findByDocIdIgnoreCase("DOC-1001")).thenReturn(Optional.of(item));

        // Member attempts to delete document -> BLOCKED!
        String memberReply = botCommandService.processCommand("/delete DOC-1001", MEMBER_A_PHONE, "Dad");
        assertTrue(memberReply.contains("Only the Master Admin can delete documents"),
                "Expected delete block message for regular member but got: " + memberReply);
        assertTrue(memberReply.contains("select, view, and download documents"));

        // Admin attempts to delete document -> ALLOWED!
        String adminReply = botCommandService.processCommand("/delete DOC-1001", ADMIN_PHONE, "Master Admin");
        assertTrue(adminReply.contains("Document Deleted Successfully!"));
    }

    @Test
    void testDownloadCommand_EnforcesOwnershipAndApproval() throws IOException {
        Path filePath = tempDir.resolve("test_doc.pdf");
        Files.writeString(filePath, "DUMMY FILE DATA");

        DocumentItem approvedDoc = new DocumentItem(
                "DOC-1010",
                DocumentType.DOCUMENT,
                "test_doc.pdf",
                "DOC-1010_test_doc.pdf",
                filePath.toString(),
                "application/pdf",
                15L,
                "Approved Doc",
                MEMBER_A_PHONE,
                "Dad"
        );
        approvedDoc.setStatus(DocumentStatus.APPROVED);
        when(documentRepository.findByDocIdIgnoreCase("DOC-1010")).thenReturn(Optional.of(approvedDoc));

        // Member A downloads own approved document
        String downloadReply = botCommandService.processCommand("/download DOC-1010", MEMBER_A_PHONE, "Dad");
        assertTrue(downloadReply.contains("Downloading Document"));
        Mockito.verify(whatsAppProvider).sendDocumentMessage(eq(MEMBER_A_PHONE), any(), eq("test_doc.pdf"), any());

        // Member B attempts to download Member A's document -> NUMBER BIAS DENIED
        String blockedReply = botCommandService.processCommand("/download DOC-1010", MEMBER_B_PHONE, "Mom");
        assertTrue(blockedReply.contains("Access Denied"));
    }

    @Test
    void testAdminApprovalFlow() {
        DocumentItem pendingDoc = new DocumentItem(
                "DOC-2001",
                DocumentType.DOCUMENT,
                "pan_card.jpg",
                "DOC-2001_pan_card.jpg",
                tempDir.resolve("pan_card.jpg").toString(),
                "image/jpeg",
                2048L,
                "Pan Card",
                MEMBER_A_PHONE,
                "Dad"
        );
        pendingDoc.setStatus(DocumentStatus.PENDING_APPROVAL);
        when(documentRepository.findByDocIdIgnoreCase("DOC-2001")).thenReturn(Optional.of(pendingDoc));
        when(documentRepository.findByStatusOrderByCreatedAtDesc(DocumentStatus.PENDING_APPROVAL))
                .thenReturn(List.of(pendingDoc));

        // Admin checks pending approvals
        String pendingList = botCommandService.processCommand("/pending", ADMIN_PHONE, "Admin User");
        assertTrue(pendingList.contains("Documents Awaiting Admin Approval"));
        assertTrue(pendingList.contains("DOC-2001"));

        // Admin approves document
        String approveReply = botCommandService.processCommand("/approve DOC-2001", ADMIN_PHONE, "Admin User");
        assertTrue(approveReply.contains("Document Approved!"));

        // Non-admin attempting to approve is blocked
        String memberApproveReply = botCommandService.processCommand("/approve DOC-2001", MEMBER_A_PHONE, "Dad");
        assertTrue(memberApproveReply.contains("Access Denied"));
    }

    @Test
    void testAdminAnalyticsCommand() {
        // Master Admin requests analytics graph
        String analyticsReply = botCommandService.processCommand("/analytics", ADMIN_PHONE, "Admin User");
        assertTrue(analyticsReply.contains("USER ACTIVITY ANALYSIS & USAGE GRAPH"));
        assertTrue(analyticsReply.contains("User Login Counts"));
        assertTrue(analyticsReply.contains("Services Used Most"));

        // Regular member cannot view analytics
        String memberAnalyticsReply = botCommandService.processCommand("/analytics", MEMBER_A_PHONE, "Dad");
        assertTrue(memberAnalyticsReply.contains("Only the Master Admin can view user analytics"));
    }

    @Test
    void testNumberBiasRestriction_MemberCannotAccessOtherMemberDoc() {
        DocumentItem itemUploadedByMemberB = new DocumentItem(
                "DOC-1005",
                DocumentType.DOCUMENT,
                "secret_b.pdf",
                "DOC-1005_secret_b.pdf",
                tempDir.resolve("secret_b.pdf").toString(),
                "application/pdf",
                1024L,
                "Member B Personal Doc",
                MEMBER_B_PHONE,
                "Member B"
        );

        when(documentRepository.findByDocIdIgnoreCase("DOC-1005"))
                .thenReturn(Optional.of(itemUploadedByMemberB));

        // Member A attempts to get Member B's document
        String responseForMemberA = botCommandService.processCommand("/get DOC-1005", MEMBER_A_PHONE, "Member A");
        assertTrue(responseForMemberA.contains("Access Restricted: Number-Bias Security Active"),
                "Member A must be blocked from accessing Member B's document");
        assertTrue(responseForMemberA.contains("This document belongs to another members you can't access this document."));
    }

    @Test
    void testSaveNoteCommand() {
        String response = botCommandService.processCommand("/note Bank account details", MEMBER_A_PHONE, "Member A");
        assertTrue(response.contains("Note Saved Successfully!"));
        assertTrue(response.contains("Bank account details"));
    }

    @Test
    void testAnyIssuesMenuOption_PromptsUser() {
        // User selects option 9
        String reply9 = botCommandService.processCommand("9", MEMBER_A_PHONE, "Dad");
        assertTrue(reply9.contains("Any Issues? / Report a Problem"));
        assertTrue(reply9.contains("/issue <describe your issue>"));
        assertTrue(reply9.contains("+91 8790208445"));
        assertTrue(reply9.contains("https://wa.me/918790208445"));

        // User types "issue"
        String replyIssue = botCommandService.processCommand("issue", MEMBER_A_PHONE, "Dad");
        assertTrue(replyIssue.contains("Any Issues? / Report a Problem"));
    }

    @Test
    void testReportIssueCommand_NotifiesAdminAndLogs() {
        String issueText = "Unable to view my Driving License photo clearly";
        String response = botCommandService.processCommand("/issue " + issueText, MEMBER_A_PHONE, "Dad");

        assertTrue(response.contains("Issue Reported Successfully!"));
        assertTrue(response.contains("hrudaypreetham10@gmail.com"));
        assertTrue(response.contains("+91 8790208445"));
        assertTrue(response.contains("https://wa.me/918790208445"));
        assertTrue(response.contains("The Master Admin has received an alert"));

        // Verify AdminNotificationService received the issue report
        Mockito.verify(adminNotificationService).notifyAdminUserIssue(eq("Dad"), eq(MEMBER_A_PHONE), eq(issueText));

        // Verify audit log recorded REPORT_ISSUE
        Mockito.verify(auditLogRepository).save(argThat(log ->
                log.getAction().equals("REPORT_ISSUE") &&
                log.getSenderPhoneNumber().equals(MEMBER_A_PHONE)
        ));
    }

    @Test
    void testDirectOptionSelection_WorksWithoutHii() {
        // Direct option "1" without saying "hii" first
        String reply1 = botCommandService.processCommand("1", MEMBER_A_PHONE, "Dad");
        assertTrue(reply1.contains("Aadhaar Card"), "Direct option 1 must trigger Aadhaar service immediately without 'hii'");

        // Direct option "2" (PAN Card)
        String reply2 = botCommandService.processCommand("2", MEMBER_A_PHONE, "Dad");
        assertTrue(reply2.contains("PAN Card"), "Direct option 2 must trigger PAN service immediately without 'hii'");

        // Direct option "3" (Driving License)
        String reply3 = botCommandService.processCommand("3", MEMBER_A_PHONE, "Dad");
        assertTrue(reply3.contains("Driving License"), "Direct option 3 must trigger DL service immediately without 'hii'");
    }

    @Test
    void testDynamicServiceAssignment_AndRestriction() {
        // Initially Member A has access to all default services
        String allowedReply = botCommandService.processCommand("4", MEMBER_A_PHONE, "Dad");
        assertTrue(allowedReply.contains("Passport"));

        // Admin revokes PASSPORT service from Member A
        String removeReply = botCommandService.processCommand("/removeservice " + MEMBER_A_PHONE + " PASSPORT", ADMIN_PHONE, "Master Admin");
        assertTrue(removeReply.contains("Service Removed!"));
        assertTrue(removeReply.contains("PASSPORT"));

        // Now Member A tries to access Passport (option 4) -> RESTRICTED!
        String restrictedReply = botCommandService.processCommand("4", MEMBER_A_PHONE, "Dad");
        assertTrue(restrictedReply.contains("Service Restricted: Not Assigned to Your Account"),
                "User must be blocked when Admin revokes the service");
        assertTrue(restrictedReply.contains("+91 8790208445"));

        // Admin re-adds PASSPORT service to Member A
        String addReply = botCommandService.processCommand("/addservice " + MEMBER_A_PHONE + " PASSPORT", ADMIN_PHONE, "Master Admin");
        assertTrue(addReply.contains("Service Added!"));

        // Member A can now access Passport again!
        String reAllowedReply = botCommandService.processCommand("4", MEMBER_A_PHONE, "Dad");
        assertTrue(reAllowedReply.contains("Passport"));
    }
}
