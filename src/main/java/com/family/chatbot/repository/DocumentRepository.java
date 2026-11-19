package com.family.chatbot.repository;

import com.family.chatbot.model.DocumentItem;
import com.family.chatbot.model.DocumentStatus;
import com.family.chatbot.model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentItem, Long> {

    Optional<DocumentItem> findByDocIdIgnoreCase(String docId);

    Optional<DocumentItem> findByFileHash(String fileHash);

    // Category-specific lookup for user (e.g., AADHAAR, PAN, DRIVING_LICENSE)
    Optional<DocumentItem> findTop1BySenderPhoneNumberAndCategoryTagIgnoreCaseOrderByCreatedAtDesc(String senderPhoneNumber, String categoryTag);

    List<DocumentItem> findBySenderPhoneNumberAndCategoryTagIgnoreCaseOrderByCreatedAtDesc(String senderPhoneNumber, String categoryTag);

    // Approval Workflow queries
    List<DocumentItem> findByStatusOrderByCreatedAtDesc(DocumentStatus status);

    List<DocumentItem> findBySenderPhoneNumberAndStatusOrderByCreatedAtDesc(String senderPhoneNumber, DocumentStatus status);

    long countByStatus(DocumentStatus status);

    // Global (Admin)
    List<DocumentItem> findTop10ByOrderByCreatedAtDesc();

    List<DocumentItem> findAllByOrderByCreatedAtDesc();

    long countByType(DocumentType type);

    @Query("SELECT d FROM DocumentItem d WHERE " +
           "LOWER(d.captionOrNote) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(d.originalFileName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(d.docId) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY d.createdAt DESC")
    List<DocumentItem> searchByKeyword(@Param("keyword") String keyword);

    // Number-Bias Restricted (Individual Family Member Isolation)
    List<DocumentItem> findTop10BySenderPhoneNumberOrderByCreatedAtDesc(String senderPhoneNumber);

    List<DocumentItem> findBySenderPhoneNumberOrderByCreatedAtDesc(String senderPhoneNumber);

    long countBySenderPhoneNumberAndType(String senderPhoneNumber, DocumentType type);

    long countBySenderPhoneNumber(String senderPhoneNumber);

    @Query("SELECT d FROM DocumentItem d WHERE d.senderPhoneNumber = :sender AND (" +
           "LOWER(d.captionOrNote) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(d.originalFileName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(d.docId) LIKE LOWER(CONCAT('%', :keyword, '%')) ) " +
           "ORDER BY d.createdAt DESC")
    List<DocumentItem> searchByKeywordAndSender(@Param("keyword") String keyword,
                                                @Param("sender") String sender);

    // High-performance DB-level aggregations (eliminates in-memory entity bloat)
    @Query("SELECT COALESCE(SUM(d.fileSizeBytes), 0) FROM DocumentItem d")
    long sumTotalBytes();

    @Query("SELECT COALESCE(SUM(d.fileSizeBytes), 0) FROM DocumentItem d WHERE d.senderPhoneNumber = :sender")
    long sumTotalBytesBySender(@Param("sender") String sender);

    @Query("SELECT COALESCE(MAX(d.id), 0) FROM DocumentItem d")
    long findMaxId();
}
