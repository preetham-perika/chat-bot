package com.family.chatbot.service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Local Console / Mock WhatsApp Provider for local testing and debugging without any API credentials.
 */
@Service
public class ConsoleWhatsAppProvider implements WhatsAppProvider {

    private static final Logger log = LoggerFactory.getLogger(ConsoleWhatsAppProvider.class);

    @Override
    public void sendTextMessage(String recipientPhone, String messageText) {
        log.info("\n================ [SIMULATED WHATSAPP OUTBOUND MESSAGE] ================\n" +
                 "To: +{}\n" +
                 "Message:\n{}\n" +
                 "========================================================================\n",
                recipientPhone, messageText);
    }

    @Override
    public void sendDocumentMessage(String recipientPhone, java.io.File file, String fileName, String caption) {
        log.info("\n================ [SIMULATED WHATSAPP OUTBOUND DOCUMENT] ================\n" +
                 "To: +{}\n" +
                 "File: {} ({})\n" +
                 "Caption: {}\n" +
                 "========================================================================\n",
                recipientPhone, fileName, file != null ? file.length() + " bytes" : "null", caption);
    }

    @Override
    public byte[] downloadMedia(String mediaIdentifier) {
        log.info("[ConsoleWhatsAppProvider] Simulating media download for ID: {}", mediaIdentifier);
        return "SIMULATED_FILE_CONTENT".getBytes();
    }

    @Override
    public String getProviderName() {
        return "console";
    }
}
