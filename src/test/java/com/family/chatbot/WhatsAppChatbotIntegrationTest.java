package com.family.chatbot;

import com.family.chatbot.model.DocumentItem;
import com.family.chatbot.model.DocumentType;
import com.family.chatbot.model.UserRole;
import com.family.chatbot.repository.AuditLogRepository;
import com.family.chatbot.repository.DocumentRepository;
import com.family.chatbot.repository.FamilyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class WhatsAppChatbotIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private com.family.chatbot.service.WhitelistService whitelistService;

    private final String ADMIN_PHONE = "918790208445";
    private final String MEMBER_PHONE = "919876543211";
    private final String STRANGER_PHONE = "919999999999";
    private final String API_KEY = "chat-vault-secret-admin-key-2026";

    @BeforeEach
    void setUp() {
        if (!familyMemberRepository.existsByPhoneNumber(ADMIN_PHONE)) {
            whitelistService.addFamilyMember("Master Admin", ADMIN_PHONE, UserRole.ADMIN);
        }
        if (!familyMemberRepository.existsByPhoneNumber(MEMBER_PHONE)) {
            whitelistService.addFamilyMember("Dad", MEMBER_PHONE, UserRole.MEMBER);
        }
        whitelistService.updateUserServices(MEMBER_PHONE, java.util.Set.of("AADHAAR", "PAN", "DRIVING_LICENSE", "PASSPORT", "HEALTH", "OTHER"));
    }

    @Test
    void testEndToEnd_MetaWebhookVerification() throws Exception {
        mockMvc.perform(get("/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "FAMILY_DOC_BOT_VERIFY_TOKEN")
                        .param("hub.challenge", "TEST_CHALLENGE_999"))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("TEST_CHALLENGE_999", result.getResponse().getContentAsString()));
    }

    @Test
    void testEndToEnd_SwaggerOpenApiEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("WhatsApp Family Document & Picture Vault API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"));
    }

    @Test
    void testEndToEnd_MissingApiKey_RejectedWith401() throws Exception {
        String requestJson = """
                {
                  "senderPhone": "919876543211",
                  "message": "hii"
                }
                """;

        // Without X-API-KEY header -> must be rejected with 401
        mockMvc.perform(post("/api/test/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testEndToEnd_LocalTestChat_GreetingFlow() throws Exception {
        // Test "hii" greeting for Dad
        String requestJson = """
                {
                  "senderPhone": "919876543211",
                  "message": "hii"
                }
                """;

        mockMvc.perform(post("/api/test/chat")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderPhone").value("919876543211"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.botReply").value(org.hamcrest.Matchers.containsString("Hii")))
                .andExpect(jsonPath("$.botReply").value(org.hamcrest.Matchers.containsString("1️⃣ *Aadhaar Card*")))
                .andExpect(jsonPath("$.botReply").value(org.hamcrest.Matchers.containsString("2️⃣ *PAN Card*")))
                .andExpect(jsonPath("$.botReply").value(org.hamcrest.Matchers.containsString("3️⃣ *Driving License*")));
    }

    @Test
    void testEndToEnd_LocalTestChat_UnauthorizedRejection() throws Exception {
        String requestJson = """
                {
                  "senderPhone": "919999999999",
                  "message": "hii"
                }
                """;

        mockMvc.perform(post("/api/test/chat")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.botReply").value(org.hamcrest.Matchers.containsString("Access Denied")));
    }

    @Test
    void testEndToEnd_AdminUserManagement_AddUserViaBot_DynamicGeneration() throws Exception {
        // Clean up any previous test artifact for Sister
        familyMemberRepository.findByPhoneNumber("919876543215").ifPresent(familyMemberRepository::delete);

        // Admin starts session with 'hii'
        mockMvc.perform(post("/api/test/chat")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderPhone\": \"918790208445\", \"message\": \"hii\"}"))
                .andExpect(status().isOk());

        // Admin registers a new family member "Sister" dynamically
        String requestJson = """
                {
                  "senderPhone": "918790208445",
                  "message": "/adduser Sister +919876543215"
                }
                """;

        mockMvc.perform(post("/api/test/chat")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.botReply").value(org.hamcrest.Matchers.containsString("Family Member Created Successfully!")))
                .andExpect(jsonPath("$.botReply").value(org.hamcrest.Matchers.containsString("drive/chatbot_documents/Sister/")));

        // Verify Sister is now registered dynamically in the database
        assertTrue(familyMemberRepository.existsByPhoneNumber("919876543215"));
    }

    @Test
    void testEndToEnd_AdminUserManagement_DuplicateUserRejected() throws Exception {
        // Admin starts session with 'hii'
        mockMvc.perform(post("/api/test/chat")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderPhone\": \"918790208445\", \"message\": \"hii\"}"))
                .andExpect(status().isOk());

        // Attempting to add an already registered member dynamically must fail with duplicate warning
        String requestJson = """
                {
                  "senderPhone": "918790208445",
                  "message": "/adduser Dad2 +919876543211"
                }
                """;

        mockMvc.perform(post("/api/test/chat")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.botReply").value(org.hamcrest.Matchers.containsString("Duplicate phone number detected")));
    }

    @Test
    void testEndToEnd_UserDeleteBlocked_OnlyAdminAllowed() throws Exception {
        // Regular member starts session with 'hii'
        mockMvc.perform(post("/api/test/chat")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderPhone\": \"919876543211\", \"message\": \"hii\"}"))
                .andExpect(status().isOk());

        // Regular member attempts to delete a document -> BLOCKED!
        String userDeleteJson = """
                {
                  "senderPhone": "919876543211",
                  "message": "/delete DOC-999"
                }
                """;

        mockMvc.perform(post("/api/test/chat")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userDeleteJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.botReply").value(org.hamcrest.Matchers.containsString("Only the Master Admin can delete documents")))
                .andExpect(jsonPath("$.botReply").value(org.hamcrest.Matchers.containsString("select, view, and download documents")));
    }

    @Test
    void testEndToEnd_AdminAnalyticsEndpoint() throws Exception {
        // Admin requests analytics JSON
        mockMvc.perform(get("/api/analytics")
                        .header("X-API-KEY", API_KEY)
                        .header("X-Caller-Phone", ADMIN_PHONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userLogins").isArray())
                .andExpect(jsonPath("$.serviceUsage").isArray());

        // Non-admin request must be rejected
        mockMvc.perform(get("/api/analytics")
                        .header("X-API-KEY", API_KEY)
                        .header("X-Caller-Phone", MEMBER_PHONE))
                .andExpect(status().isForbidden());
    }

    @Test
    void testEndToEnd_NonAdminCannotManageUsers() throws Exception {
        // Regular member starts session with 'hii'
        mockMvc.perform(post("/api/test/chat")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderPhone\": \"919876543211\", \"message\": \"hii\"}"))
                .andExpect(status().isOk());

        // Regular member tries to add user
        String requestJson = """
                {
                  "senderPhone": "919876543211",
                  "message": "/adduser Hacker +919876543299"
                }
                """;

        mockMvc.perform(post("/api/test/chat")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.botReply").value(org.hamcrest.Matchers.containsString("Only the Master Admin")));

        assertFalse(familyMemberRepository.existsByPhoneNumber("919876543299"));
    }

    @Test
    void testEndToEnd_NumberBiasIsolation_RestApiDownloadBlocked() throws Exception {
        String docId = "DOC-IT-" + System.nanoTime();
        DocumentItem item = new DocumentItem(
                docId,
                DocumentType.DOCUMENT,
                "secret_will.pdf",
                docId + "_secret_will.pdf",
                "./data/storage/documents/dummy.pdf",
                "application/pdf",
                500L,
                "Confidential Will",
                MEMBER_PHONE,
                "Dad"
        );
        documentRepository.save(item);

        // Another member or stranger attempts to download Member's file via REST API
        mockMvc.perform(get("/api/documents/" + docId + "/download")
                        .header("X-API-KEY", API_KEY)
                        .header("X-Caller-Phone", STRANGER_PHONE))
                .andExpect(status().isForbidden());
    }
}
