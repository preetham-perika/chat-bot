package com.family.chatbot.service;

import com.family.chatbot.model.AuditEventType;
import com.family.chatbot.model.AuditLog;
import com.family.chatbot.model.AuditStatus;
import com.family.chatbot.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Enterprise Audit and Operational Logging Service.
 * Records all security access, document operations, and system events
 * in both structured log files and persistent database audit storage.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final AuditLogRepository auditRepository;

    public AuditLogService(AuditLogRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Record an audit event in database and structured logs.
     */
    @Transactional
    public AuditLog record(AuditEventType type,
                           AuditStatus status,
                           String senderPhone,
                           String userRole,
                           String action,
                           String targetDocId,
                           String details) {

        AuditLog entry = new AuditLog(type, status, senderPhone, userRole, action, targetDocId, details);
        AuditLog saved = auditRepository.save(entry);

        String maskedPhone = maskPhoneNumber(senderPhone);
        String logMessage = String.format("[%s] [%s] Phone: +%s | Role: %s | Action: %s | Target: %s | Details: %s",
                type, status, maskedPhone, userRole, action, targetDocId != null ? targetDocId : "-", details);

        if (status == AuditStatus.DENIED || type == AuditEventType.UNAUTHORIZED_ACCESS || type == AuditEventType.NUMBER_BIAS_VIOLATION) {
            securityLog.warn("🚨 [SECURITY EVENT] {}", logMessage);
            log.warn("🚨 [SECURITY EVENT] {}", logMessage);
        } else {
            log.info("{}", logMessage);
        }

        return saved;
    }

    public List<AuditLog> getRecentLogs() {
        return auditRepository.findTop20ByOrderByTimestampDesc();
    }

    public List<AuditLog> getReportedIssues() {
        return auditRepository.findByActionOrderByTimestampDesc("REPORT_ISSUE");
    }

    public long getReportedIssueCount() {
        return auditRepository.countByAction("REPORT_ISSUE");
    }

    public long getUserIssueCount(String senderPhone) {
        return auditRepository.countBySenderPhoneNumberAndAction(senderPhone, "REPORT_ISSUE");
    }

    public long getContactCount() {
        return auditRepository.countByActionIn(List.of("CONTACT_ADMIN", "CALL_ADMIN", "HELP_CONTACT"));
    }

    public long getLoginCount() {
        return auditRepository.countByActionIn(List.of("LOGIN", "GREETING"));
    }

    public long getUserLoginCount(String senderPhone) {
        return auditRepository.countBySenderPhoneNumberAndAction(senderPhone, "LOGIN");
    }

    public List<AuditLog> getSecurityViolations() {
        return auditRepository.findTop20ByStatusOrderByTimestampDesc(AuditStatus.DENIED);
    }

    public long getSecurityViolationCount() {
        return auditRepository.countByStatus(AuditStatus.DENIED);
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 6) return "******";
        int len = phone.length();
        return phone.substring(0, 3) + "****" + phone.substring(len - 3);
    }
}
