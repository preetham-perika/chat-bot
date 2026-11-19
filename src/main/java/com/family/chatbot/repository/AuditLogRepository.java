package com.family.chatbot.repository;

import com.family.chatbot.model.AuditEventType;
import com.family.chatbot.model.AuditLog;
import com.family.chatbot.model.AuditStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop20ByOrderByTimestampDesc();

    List<AuditLog> findTop20ByStatusOrderByTimestampDesc(AuditStatus status);

    List<AuditLog> findBySenderPhoneNumberOrderByTimestampDesc(String senderPhoneNumber);

    List<AuditLog> findByEventTypeOrderByTimestampDesc(AuditEventType eventType);

    long countByStatus(AuditStatus status);

    List<AuditLog> findByActionOrderByTimestampDesc(String action);

    long countByAction(String action);

    long countByActionIn(List<String> actions);

    long countBySenderPhoneNumberAndAction(String senderPhoneNumber, String action);

    @Query("SELECT a.senderPhoneNumber, COUNT(a) FROM AuditLog a WHERE a.action IN ('GREETING', 'LOGIN') GROUP BY a.senderPhoneNumber ORDER BY COUNT(a) DESC")
    List<Object[]> countLoginsBySender();

    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.action LIKE 'SERVICE_%' OR a.action IN ('DOWNLOAD_DOCUMENT', 'GET_DOCUMENT', 'STORE_FILE', 'SAVE_NOTE', 'SEARCH_DOCUMENTS') GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> countServiceUsage();

    @Query("SELECT a.senderPhoneNumber, COUNT(a) FROM AuditLog a GROUP BY a.senderPhoneNumber ORDER BY COUNT(a) DESC")
    List<Object[]> countTotalActivityBySender();
}
