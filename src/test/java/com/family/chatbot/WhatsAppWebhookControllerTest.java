package com.family.chatbot;

import com.family.chatbot.config.WhatsAppProperties;
import com.family.chatbot.controller.WhatsAppWebhookController;
import com.family.chatbot.model.IncomingMessage;
import com.family.chatbot.service.WhatsAppMessageDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WhatsAppWebhookControllerTest {

    private MockMvc mockMvc;
    private WhatsAppProperties properties;
    private WhatsAppMessageDispatcher dispatcher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppProperties();
        properties.setVerifyToken("SECRET_VERIFY_TOKEN_123");

        dispatcher = Mockito.mock(WhatsAppMessageDispatcher.class);
        objectMapper = new ObjectMapper();

        WhatsAppWebhookController controller = new WhatsAppWebhookController(properties, dispatcher, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testMetaWebhookVerificationSuccess() throws Exception {
        mockMvc.perform(get("/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "SECRET_VERIFY_TOKEN_123")
                        .param("hub.challenge", "CHALLENGE_CODE_98765"))
                .andExpect(status().isOk())
                .andExpect(content().string("CHALLENGE_CODE_98765"));
    }

    @Test
    void testMetaWebhookVerificationFailure_InvalidToken() throws Exception {
        mockMvc.perform(get("/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "WRONG_TOKEN")
                        .param("hub.challenge", "CHALLENGE_CODE_98765"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testMetaInboundTextMessageWebhook() throws Exception {
        String metaJsonPayload = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "10001",
                      "changes": [
                        {
                          "value": {
                            "messaging_product": "whatsapp",
                            "contacts": [
                              {
                                "profile": { "name": "Family Member" },
                                "wa_id": "919876543211"
                              }
                            ],
                            "messages": [
                              {
                                "from": "919876543211",
                                "id": "wamid.HBgL",
                                "type": "text",
                                "text": { "body": "menu" }
                              }
                            ]
                          },
                          "field": "messages"
                        }
                      ]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metaJsonPayload))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));

        Mockito.verify(dispatcher).dispatch(any(IncomingMessage.class));
    }
}
