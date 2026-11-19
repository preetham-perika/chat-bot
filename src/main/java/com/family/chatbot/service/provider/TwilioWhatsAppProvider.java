package com.family.chatbot.service.provider;

import com.family.chatbot.config.WhatsAppProperties;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Twilio WhatsApp API Provider.
 * Allows sending messages and fetching media via Twilio REST API.
 */
@Service
public class TwilioWhatsAppProvider implements WhatsAppProvider {

    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppProvider.class);

    private final WhatsAppProperties properties;
    private final OkHttpClient httpClient;

    public TwilioWhatsAppProvider(WhatsAppProperties properties) {
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void sendTextMessage(String recipientPhone, String messageText) {
        if (properties.getTwilioAccountSid() == null || properties.getTwilioAccountSid().isBlank() ||
            properties.getTwilioAuthToken() == null || properties.getTwilioAuthToken().isBlank()) {
            log.warn("[Twilio WhatsApp API] Credentials not configured. Message to {} logged locally: {}",
                    recipientPhone, messageText);
            return;
        }

        String url = String.format("https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json",
                properties.getTwilioAccountSid());

        String formattedRecipient = recipientPhone.startsWith("+") ? "whatsapp:" + recipientPhone : "whatsapp:+" + recipientPhone;
        String formattedSender = properties.getTwilioPhoneNumber().startsWith("whatsapp:")
                ? properties.getTwilioPhoneNumber()
                : "whatsapp:" + properties.getTwilioPhoneNumber();

        RequestBody formBody = new FormBody.Builder()
                .add("To", formattedRecipient)
                .add("From", formattedSender)
                .add("Body", messageText)
                .build();

        String credential = Credentials.basic(properties.getTwilioAccountSid(), properties.getTwilioAuthToken());

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", credential)
                .post(formBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String resp = response.body() != null ? response.body().string() : "";
                log.error("[Twilio WhatsApp API] Failed to send message to {}: HTTP {} - {}",
                        recipientPhone, response.code(), resp);
            } else {
                log.info("[Twilio WhatsApp API] Successfully sent message to {}", recipientPhone);
            }
        } catch (Exception e) {
            log.error("[Twilio WhatsApp API] Error sending message to {}: {}", recipientPhone, e.getMessage(), e);
        }
    }

    @Override
    public byte[] downloadMedia(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return new byte[0];
        }

        Request.Builder reqBuilder = new Request.Builder().url(mediaUrl).get();
        if (properties.getTwilioAccountSid() != null && !properties.getTwilioAccountSid().isBlank()) {
            reqBuilder.addHeader("Authorization",
                    Credentials.basic(properties.getTwilioAccountSid(), properties.getTwilioAuthToken()));
        }

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            } else {
                log.error("[Twilio WhatsApp API] Failed to download media from {}: HTTP {}",
                        mediaUrl, response.code());
            }
        } catch (IOException e) {
            log.error("[Twilio WhatsApp API] Error downloading media from {}: {}", mediaUrl, e.getMessage(), e);
        }

        return new byte[0];
    }

    @Override
    public void sendDocumentMessage(String recipientPhone, java.io.File file, String fileName, String caption) {
        sendTextMessage(recipientPhone, "📄 [Document: " + fileName + "]\n" + (caption != null ? caption : ""));
    }

    @Override
    public String getProviderName() {
        return "twilio";
    }
}
