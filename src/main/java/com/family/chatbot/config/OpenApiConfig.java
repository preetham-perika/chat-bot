package com.family.chatbot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 3 configuration for WhatsApp Document Vault.
 * Interactive documentation available at: http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    public static final String API_KEY_HEADER = "X-API-KEY";
    public static final String CALLER_PHONE_HEADER = "X-Caller-Phone";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("WhatsApp Family Document & Picture Vault API")
                        .version("1.0.0")
                        .description("""
                                ### Private, Secure WhatsApp Document Vault with Number-Bias Isolation
                                
                                **Key Features:**
                                - **AES-256-GCM Encryption at Rest:** Identity files (Aadhaar, PAN, Passports) are encrypted on disk.
                                - **Number-Bias Isolation:** Family members access ONLY their own documents; Master Admin has full management access.
                                - **Duplicate Upload Protection:** SHA-256 binary checksums prevent duplicate files and redundant alerts.
                                - **Cloud Sync:** Direct 100% free Google Apps Script sync into Google Drive under `family_chatbot/{UserName}/`.
                                - **Issue Reporting & Emergency Call:** Direct WhatsApp call link `https://wa.me/918790208445` and dual email/alert notifications.
                                
                                **Authentication:**
                                - `X-API-KEY`: Master REST API secret key (`chat-vault-secret-admin-key-2026`).
                                - `X-Caller-Phone`: Phone number in +91 format (e.g. `918790208445` for Admin, `919876543211` for Member).
                                """)
                        .contact(new Contact()
                                .name("Master Admin (Hruday Preetham)")
                                .email("hrudaypreetham10@gmail.com")
                                .url("https://wa.me/918790208445"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components()
                        .addSecuritySchemes(API_KEY_HEADER, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(API_KEY_HEADER)
                                .description("Master API Key required for REST access (Default: chat-vault-secret-admin-key-2026)"))
                        .addSecuritySchemes(CALLER_PHONE_HEADER, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(CALLER_PHONE_HEADER)
                                .description("Whitelisted caller phone number (e.g. 918790208445 for Admin, 919876543211 for Member)")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(API_KEY_HEADER)
                        .addList(CALLER_PHONE_HEADER));
    }
}
