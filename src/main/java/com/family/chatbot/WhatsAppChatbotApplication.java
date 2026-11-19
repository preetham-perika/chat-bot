package com.family.chatbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main Application Entry Point for the Family WhatsApp Document & Picture Vault Chatbot.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WhatsAppChatbotApplication {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppChatbotApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(WhatsAppChatbotApplication.class, args);
        log.info("""
                =================================================================================
                🚀 WhatsApp Family Document Vault Chatbot Started Successfully!
                🔒 Security: Whitelist Active | Number-Bias Isolation Active | Master Admin Enabled
                📂 Storage: 100% On-Premise Local Drive Vault (Zero External Leak)
                🌐 Webhook Endpoint: http://localhost:8080/webhook
                📋 Health Endpoint:  http://localhost:8080/api/health
                =================================================================================
                """);
    }
}
