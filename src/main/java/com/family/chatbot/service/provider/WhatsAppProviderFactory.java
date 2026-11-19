package com.family.chatbot.service.provider;

import com.family.chatbot.config.WhatsAppProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Primary WhatsApp service that dynamically delegates calls to the active provider
 * configured in application.yml (meta, twilio, or console).
 */
@Service
@Primary
public class WhatsAppProviderFactory implements WhatsAppProvider {

    private final WhatsAppProperties properties;
    private final List<WhatsAppProvider> providers;

    public WhatsAppProviderFactory(WhatsAppProperties properties, List<WhatsAppProvider> providers) {
        this.properties = properties;
        this.providers = providers;
    }

    private WhatsAppProvider getActiveProvider() {
        String configured = properties.getProvider() != null ? properties.getProvider().trim().toLowerCase() : "meta";
        return providers.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(configured))
                .findFirst()
                .orElseGet(() -> providers.stream()
                        .filter(p -> p instanceof ConsoleWhatsAppProvider)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No WhatsApp provider available")));
    }

    @Override
    public void sendTextMessage(String recipientPhone, String messageText) {
        getActiveProvider().sendTextMessage(recipientPhone, messageText);
    }

    @Override
    public void sendDocumentMessage(String recipientPhone, java.io.File file, String fileName, String caption) {
        getActiveProvider().sendDocumentMessage(recipientPhone, file, fileName, caption);
    }

    @Override
    public byte[] downloadMedia(String mediaIdentifier) {
        return getActiveProvider().downloadMedia(mediaIdentifier);
    }

    @Override
    public String getProviderName() {
        return getActiveProvider().getProviderName();
    }
}
