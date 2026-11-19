package com.family.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "whatsapp")
public class WhatsAppProperties {

    /**
     * Provider: "meta" (Meta WhatsApp Cloud API), "twilio", or "console"
     */
    private String provider = "meta";

    /**
     * Webhook verification token (used by Meta WhatsApp Cloud API during webhook setup)
     */
    private String verifyToken = "FAMILY_DOC_BOT_VERIFY_TOKEN";

    /**
     * Meta WhatsApp Cloud API Permanent/Temporary Access Token
     */
    private String accessToken = "";

    /**
     * Meta WhatsApp Cloud API Phone Number ID
     */
    private String phoneNumberId = "";

    /**
     * Graph API Version (e.g., v20.0)
     */
    private String apiVersion = "v20.0";

    /**
     * Meta App Secret for HMAC-SHA256 payload signature verification (X-Hub-Signature-256)
     */
    private String appSecret = "";

    /**
     * Twilio configuration (if provider=twilio)
     */
    private String twilioAccountSid = "";
    private String twilioAuthToken = "";
    private String twilioPhoneNumber = "";

    /**
     * Master Admin phone number.
     * The admin has full access to ALL documents across all family members and the Google Drive vault.
     */
    private String adminPhoneNumber = "918790208445";

    /**
     * Master Admin email address for approval notifications.
     */
    private String adminEmail = "hrudaypreetham10@gmail.com";

    private boolean enableEmailNotification = true;

    /**
     * List of whitelisted family phone numbers (Members).
     * Members can only see, search, and access their own documents (number-bias isolation).
     */
    private List<String> whitelist = new ArrayList<>();

    /**
     * Whether to send an access denied message to non-whitelisted numbers (true) or drop silently (false).
     */
    private boolean notifyUnauthorized = true;

    private String unauthorizedMessage = "⛔ Access Denied: This WhatsApp bot is private and restricted to authorized family members only.";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(String verifyToken) {
        this.verifyToken = verifyToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getTwilioAccountSid() {
        return twilioAccountSid;
    }

    public void setTwilioAccountSid(String twilioAccountSid) {
        this.twilioAccountSid = twilioAccountSid;
    }

    public String getTwilioAuthToken() {
        return twilioAuthToken;
    }

    public void setTwilioAuthToken(String twilioAuthToken) {
        this.twilioAuthToken = twilioAuthToken;
    }

    public String getTwilioPhoneNumber() {
        return twilioPhoneNumber;
    }

    public void setTwilioPhoneNumber(String twilioPhoneNumber) {
        this.twilioPhoneNumber = twilioPhoneNumber;
    }

    public String getAdminPhoneNumber() {
        return adminPhoneNumber;
    }

    public void setAdminPhoneNumber(String adminPhoneNumber) {
        this.adminPhoneNumber = adminPhoneNumber;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public boolean isEnableEmailNotification() {
        return enableEmailNotification;
    }

    public void setEnableEmailNotification(boolean enableEmailNotification) {
        this.enableEmailNotification = enableEmailNotification;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }

    public boolean isNotifyUnauthorized() {
        return notifyUnauthorized;
    }

    public void setNotifyUnauthorized(boolean notifyUnauthorized) {
        this.notifyUnauthorized = notifyUnauthorized;
    }

    public String getUnauthorizedMessage() {
        return unauthorizedMessage;
    }

    public void setUnauthorizedMessage(String unauthorizedMessage) {
        this.unauthorizedMessage = unauthorizedMessage;
    }
}
