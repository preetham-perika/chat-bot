package com.family.chatbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing any stored document, photo, or documentation note.
 */
@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_doc_id", columnList = "docId", unique = true),
    @Index(name = "idx_sender_phone", columnList = "senderPhoneNumber")
})
public class DocumentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String docId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentType type;

    @Column(length = 255)
    private String originalFileName;

    @Column(length = 255)
    private String storedFileName;

    @Column(length = 1000)
    private String storedPath;

    @Column(length = 100)
    private String mimeType;

    private Long fileSizeBytes;

    @Column(length = 4000)
    private String captionOrNote;

    @Column(nullable = false, length = 50)
    private String senderPhoneNumber;

    @Column(length = 100)
    private String senderName;

    private boolean syncedToDrive;

    @Column(length = 1000)
    private String driveSyncPath;

    @Column(length = 64)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DocumentStatus status = DocumentStatus.PENDING_APPROVAL;

    @Column(length = 50)
    private String categoryTag = "OTHER";

    @Column(length = 50)
    private String approvedBy;

    private LocalDateTime approvalTimestamp;

    @Column(length = 1000)
    private String rejectionReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public DocumentItem() {
        this.status = DocumentStatus.PENDING_APPROVAL;
        this.categoryTag = "OTHER";
        this.createdAt = LocalDateTime.now();
    }

    public DocumentItem(String docId, DocumentType type, String originalFileName,
                        String storedFileName, String storedPath, String mimeType,
                        Long fileSizeBytes, String captionOrNote, String senderPhoneNumber,
                        String senderName) {
        this.docId = docId;
        this.type = type;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.storedPath = storedPath;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.captionOrNote = captionOrNote;
        this.senderPhoneNumber = senderPhoneNumber;
        this.senderName = senderName;
        this.syncedToDrive = false;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public DocumentType getType() {
        return type;
    }

    public void setType(DocumentType type) {
        this.type = type;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getStoredFileName() {
        return storedFileName;
    }

    public void setStoredFileName(String storedFileName) {
        this.storedFileName = storedFileName;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getCaptionOrNote() {
        return captionOrNote;
    }

    public void setCaptionOrNote(String captionOrNote) {
        this.captionOrNote = captionOrNote;
    }

    public String getSenderPhoneNumber() {
        return senderPhoneNumber;
    }

    public void setSenderPhoneNumber(String senderPhoneNumber) {
        this.senderPhoneNumber = senderPhoneNumber;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public boolean isSyncedToDrive() {
        return syncedToDrive;
    }

    public void setSyncedToDrive(boolean syncedToDrive) {
        this.syncedToDrive = syncedToDrive;
    }

    public String getDriveSyncPath() {
        return driveSyncPath;
    }

    public void setDriveSyncPath(String driveSyncPath) {
        this.driveSyncPath = driveSyncPath;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public String getCategoryTag() {
        return categoryTag;
    }

    public void setCategoryTag(String categoryTag) {
        this.categoryTag = categoryTag;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovalTimestamp() {
        return approvalTimestamp;
    }

    public void setApprovalTimestamp(LocalDateTime approvalTimestamp) {
        this.approvalTimestamp = approvalTimestamp;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
