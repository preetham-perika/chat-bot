package com.family.chatbot.model;

/**
 * Normalized model for messages received from WhatsApp.
 */
public class IncomingMessage {

    private String senderPhone;
    private String senderName;
    private String messageId;
    private String text;
    private boolean hasMedia;
    private DocumentType mediaType;
    private String mediaId;       // Meta WhatsApp media ID or Twilio MediaUrl
    private String mimeType;
    private String originalFileName;
    private byte[] mediaData;

    public IncomingMessage() {
    }

    public IncomingMessage(String senderPhone, String senderName, String messageId, String text) {
        this.senderPhone = senderPhone;
        this.senderName = senderName;
        this.messageId = messageId;
        this.text = text;
        this.hasMedia = false;
    }

    public String getSenderPhone() {
        return senderPhone;
    }

    public void setSenderPhone(String senderPhone) {
        this.senderPhone = senderPhone;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isHasMedia() {
        return hasMedia;
    }

    public void setHasMedia(boolean hasMedia) {
        this.hasMedia = hasMedia;
    }

    public DocumentType getMediaType() {
        return mediaType;
    }

    public void setMediaType(DocumentType mediaType) {
        this.mediaType = mediaType;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public byte[] getMediaData() {
        return mediaData;
    }

    public void setMediaData(byte[] mediaData) {
        this.mediaData = mediaData;
    }
}
