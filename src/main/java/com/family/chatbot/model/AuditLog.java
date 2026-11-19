package com.family.chatbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing an audit log entry for security and operational tracking.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_phone", columnList = "senderPhoneNumber"),
    @Index(name = "idx_audit_type", columnList = "eventType"),
    @Index(name = "idx_audit_status", columnList = "status")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditStatus status;

    @Column(length = 50)
    private String senderPhoneNumber;

    @Column(length = 30)
    private String userRole;

    @Column(length = 100)
    private String action;

    @Column(length = 32)
    private String targetDocId;

    @Column(length = 2000)
    private String details;

    public AuditLog() {
        this.timestamp = LocalDateTime.now();
    }

    public AuditLog(AuditEventType eventType, AuditStatus status, String senderPhoneNumber,
                    String userRole, String action, String targetDocId, String details) {
        this.timestamp = LocalDateTime.now();
        this.eventType = eventType;
        this.status = status;
        this.senderPhoneNumber = senderPhoneNumber;
        this.userRole = userRole;
        this.action = action;
        this.targetDocId = targetDocId;
        this.details = details;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public void setEventType(AuditEventType eventType) {
        this.eventType = eventType;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public void setStatus(AuditStatus status) {
        this.status = status;
    }

    public String getSenderPhoneNumber() {
        return senderPhoneNumber;
    }

    public void setSenderPhoneNumber(String senderPhoneNumber) {
        this.senderPhoneNumber = senderPhoneNumber;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetDocId() {
        return targetDocId;
    }

    public void setTargetDocId(String targetDocId) {
        this.targetDocId = targetDocId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
