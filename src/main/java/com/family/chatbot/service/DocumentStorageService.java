package com.family.chatbot.service;

import com.family.chatbot.config.StorageProperties;
import com.family.chatbot.event.MediaUploadedEvent;
import com.family.chatbot.model.DocumentItem;
import com.family.chatbot.model.DocumentStatus;
import com.family.chatbot.model.DocumentType;
import com.family.chatbot.repository.DocumentRepository;
import com.family.chatbot.service.notification.AdminNotificationService;
import com.family.chatbot.service.sync.DriveSyncService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance, local privacy-first storage service for documents, photos, and notes.
 * Stores all files locally on disk and indexes metadata in the local embedded database.
 * Supports number-bias isolation: family members only access their own files; Admin accesses all.
 */
@Service
public class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final StorageProperties properties;
    private final DocumentRepository repository;
    private final DriveSyncService driveSyncService;
    private final ApplicationEventPublisher eventPublisher;
    private final FileEncryptionService encryptionService;
    private final AtomicLong idSequence = new AtomicLong(1000);

    public DocumentStorageService(StorageProperties properties,
                                  DocumentRepository repository,
                                  DriveSyncService driveSyncService) {
        this(properties, repository, driveSyncService, null, null);
    }

    public DocumentStorageService(StorageProperties properties,
                                  DocumentRepository repository,
                                  DriveSyncService driveSyncService,
                                  ApplicationEventPublisher eventPublisher) {
        this(properties, repository, driveSyncService, eventPublisher, null);
    }

    @Autowired
    public DocumentStorageService(StorageProperties properties,
                                  DocumentRepository repository,
                                  DriveSyncService driveSyncService,
                                  @Autowired(required = false) ApplicationEventPublisher eventPublisher,
                                  @Autowired(required = false) FileEncryptionService encryptionService) {
        this.properties = properties;
        this.repository = repository;
        this.driveSyncService = driveSyncService;
        this.eventPublisher = eventPublisher;
        if (encryptionService != null) {
            this.encryptionService = encryptionService;
        } else {
            FileEncryptionService fallback = new FileEncryptionService(new com.family.chatbot.config.SecurityProperties());
            fallback.init();
            this.encryptionService = fallback;
        }
    }

    @PostConstruct
    public void init() throws IOException {
        Path basePath = properties.getBasePath();
        Files.createDirectories(basePath.resolve("documents"));
        Files.createDirectories(basePath.resolve("images"));
        Files.createDirectories(basePath.resolve("notes"));
        Files.createDirectories(basePath.resolve("other"));

        long maxId = repository.findMaxId();
        idSequence.set(1000 + maxId);
        log.info("DocumentStorageService initialized at: {}. Max item ID: {}", basePath, maxId);
    }

    /**
     * Store an incoming media file (Document, Image, Audio) received via WhatsApp.
     */
    @Transactional
    public DocumentItem storeMediaFile(byte[] data,
                                       String originalFileName,
                                       String mimeType,
                                       DocumentType type,
                                       String caption,
                                       String senderPhone,
                                       String senderName) throws IOException {
        return storeMediaFile(data, originalFileName, mimeType, type, caption, senderPhone, senderName, senderPhone, false);
    }

    /**
     * Store an incoming media file (Document, Image, Audio) received via WhatsApp with custom folder.
     */
    @Transactional
    public DocumentItem storeMediaFile(byte[] data,
                                       String originalFileName,
                                       String mimeType,
                                       DocumentType type,
                                       String caption,
                                       String senderPhone,
                                       String senderName,
                                       String folderName) throws IOException {
        return storeMediaFile(data, originalFileName, mimeType, type, caption, senderPhone, senderName, folderName, false);
    }

    /**
     * Store an incoming media file with Admin approval workflow and category detection.
     */
    @Transactional
    public DocumentItem storeMediaFile(byte[] data,
                                       String originalFileName,
                                       String mimeType,
                                       DocumentType type,
                                       String caption,
                                       String senderPhone,
                                       String senderName,
                                       String folderName,
                                       boolean isAdmin) throws IOException {

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Cannot store empty file data");
        }

        if (data.length > properties.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException("File size exceeds maximum allowed limit of " +
                    (properties.getMaxFileSizeBytes() / (1024 * 1024)) + " MB");
        }

        String prefix = switch (type) {
            case IMAGE -> "IMG";
            case DOCUMENT -> "DOC";
            case AUDIO -> "AUD";
            default -> "FILE";
        };

        String docId = prefix + "-" + idSequence.incrementAndGet();
        String subfolder = switch (type) {
            case IMAGE -> "images";
            case DOCUMENT -> "documents";
            default -> "other";
        };

        String extension = extractExtension(originalFileName, mimeType);
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String categoryTag = detectCategoryTag(originalFileName, caption);
        String cleanDocName = buildCleanDocName(originalFileName, categoryTag, docId, extension);

        String storedFileName = docId + "_" + timestamp + "_" + cleanDocName;
        Path targetDir = properties.getBasePath().resolve(subfolder);
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(storedFileName);

        String fileHash = calculateSha256(data);
        Optional<DocumentItem> existingDuplicate = repository.findByFileHash(fileHash);
        if (existingDuplicate.isPresent()) {
            log.info("Duplicate file detected with hash {}: existing docId {}", fileHash, existingDuplicate.get().getDocId());
        }

        // Military-grade AES-256-GCM encryption at rest
        byte[] dataToWrite = (encryptionService != null) ? encryptionService.encrypt(data) : data;
        Files.write(targetFile, dataToWrite, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        DocumentItem item = new DocumentItem(
                docId,
                type,
                cleanDocName,
                storedFileName,
                targetFile.toAbsolutePath().toString(),
                mimeType,
                (long) data.length,
                caption,
                senderPhone,
                senderName
        );
        item.setFileHash(fileHash);
        item.setCategoryTag(categoryTag);

        // All authorized uploads are auto-approved and synchronized directly into Google Drive
        item.setStatus(DocumentStatus.APPROVED);
        item.setApprovedBy(isAdmin ? senderPhone : "MEMBER_UPLOAD");
        item.setApprovalTimestamp(LocalDateTime.now());

        // Mirror to Google Drive sync folder directly under family_chatbot/{targetFolder}/
        String targetFolder = (folderName != null && !folderName.isBlank()) ? folderName : senderPhone;
        if (properties.isEnableDriveSync()) {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new MediaUploadedEvent(docId, targetFile, subfolder, targetFolder, senderPhone));
            } else {
                try {
                    Path syncedPath = driveSyncService.syncFile(targetFile, subfolder, targetFolder);
                    if (syncedPath != null) {
                        item.setSyncedToDrive(true);
                        item.setDriveSyncPath(syncedPath.toAbsolutePath().toString());
                    }
                } catch (Exception e) {
                    log.warn("Drive sync failed for {}: {}", docId, e.getMessage());
                }
            }
        }

        return repository.save(item);
    }

    /**
     * Store a text note or documentation entry.
     */
    @Transactional
    public DocumentItem storeNote(String noteContent,
                                  String title,
                                  String senderPhone,
                                  String senderName) throws IOException {
        return storeNote(noteContent, title, senderPhone, senderName, senderPhone);
    }

    @Transactional
    public DocumentItem storeNote(String noteContent,
                                  String title,
                                  String senderPhone,
                                  String senderName,
                                  String folderName) throws IOException {

        String docId = "NOTE-" + idSequence.incrementAndGet();
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String safeTitle = (title != null && !title.isBlank()) ? sanitizeFileName(title) : "note";
        String storedFileName = docId + "_" + timestamp + "_" + safeTitle + ".txt";

        Path targetDir = properties.getBasePath().resolve("notes");
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(storedFileName);

        byte[] noteRawBytes = (noteContent != null ? noteContent : "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] noteDataToWrite = (encryptionService != null) ? encryptionService.encrypt(noteRawBytes) : noteRawBytes;
        Files.write(targetFile, noteDataToWrite, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        DocumentItem item = new DocumentItem(
                docId,
                DocumentType.NOTE,
                storedFileName,
                storedFileName,
                targetFile.toAbsolutePath().toString(),
                "text/plain",
                (long) noteContent.getBytes().length,
                noteContent,
                senderPhone,
                senderName
        );

        String targetFolder = (folderName != null && !folderName.isBlank()) ? folderName : senderPhone;
        if (properties.isEnableDriveSync()) {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new MediaUploadedEvent(docId, targetFile, "notes", targetFolder, senderPhone));
            } else {
                try {
                    Path syncedPath = driveSyncService.syncFile(targetFile, "notes", targetFolder);
                    if (syncedPath != null) {
                        item.setSyncedToDrive(true);
                        item.setDriveSyncPath(syncedPath.toAbsolutePath().toString());
                    }
                } catch (Exception e) {
                    log.warn("Drive sync failed for note {}: {}", docId, e.getMessage());
                }
            }
        }

        return repository.save(item);
    }

    public Optional<DocumentItem> findByDocId(String docId) {
        return repository.findByDocIdIgnoreCase(docId);
    }

    /**
     * Category lookup for individual user (Aadhaar, PAN, DL, Passport, Health).
     */
    public Optional<DocumentItem> findCategoryDoc(String senderPhone, String categoryTag) {
        return repository.findTop1BySenderPhoneNumberAndCategoryTagIgnoreCaseOrderByCreatedAtDesc(senderPhone, categoryTag);
    }

    /**
     * Retrieve all documents for an individual user in a specific category (e.g. all OTHER docs or multi-page Aadhaar).
     */
    public List<DocumentItem> findCategoryDocs(String senderPhone, String categoryTag) {
        return repository.findBySenderPhoneNumberAndCategoryTagIgnoreCaseOrderByCreatedAtDesc(senderPhone, categoryTag);
    }

    /**
     * Check if an identical file was already uploaded by this sender using SHA-256 hash.
     */
    public Optional<DocumentItem> findDuplicate(byte[] data, String senderPhone) {
        if (data == null || data.length == 0 || senderPhone == null) {
            return Optional.empty();
        }
        String hash = calculateSha256(data);
        return repository.findByFileHash(hash)
                .filter(d -> d.getSenderPhoneNumber().equals(senderPhone));
    }

    /**
     * Pending approval documents lookup for Admin.
     */
    public List<DocumentItem> findPendingApprovals() {
        return repository.findByStatusOrderByCreatedAtDesc(DocumentStatus.PENDING_APPROVAL);
    }

    /**
     * Approve a document: moves to APPROVED status and synchronizes to Google Drive vault.
     */
    @Transactional
    public DocumentItem approveDocument(String docId, String adminPhone, String folderName) throws IOException {
        Optional<DocumentItem> opt = repository.findByDocIdIgnoreCase(docId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Document not found: " + docId);
        }

        DocumentItem item = opt.get();
        item.setStatus(DocumentStatus.APPROVED);
        item.setApprovedBy(adminPhone);
        item.setApprovalTimestamp(LocalDateTime.now());

        if (properties.isEnableDriveSync()) {
            Path sourceFile = Path.of(item.getStoredPath());
            if (Files.exists(sourceFile)) {
                String targetFolder = (folderName != null && !folderName.isBlank()) ? folderName : item.getSenderPhoneNumber();
                Path syncedPath = driveSyncService.syncFile(sourceFile, "documents", targetFolder);
                if (syncedPath != null) {
                    item.setSyncedToDrive(true);
                    item.setDriveSyncPath(syncedPath.toAbsolutePath().toString());
                }
            }
        }

        log.info("Document {} approved by Admin +{}", docId, adminPhone);
        return repository.save(item);
    }

    /**
     * Reject a document with a reason.
     */
    @Transactional
    public DocumentItem rejectDocument(String docId, String reason, String adminPhone) {
        Optional<DocumentItem> opt = repository.findByDocIdIgnoreCase(docId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Document not found: " + docId);
        }

        DocumentItem item = opt.get();
        item.setStatus(DocumentStatus.REJECTED);
        item.setApprovedBy(adminPhone);
        item.setRejectionReason(reason != null && !reason.isBlank() ? reason : "Document details could not be verified by Admin.");
        item.setApprovalTimestamp(LocalDateTime.now());

        log.info("Document {} rejected by Admin +{}. Reason: {}", docId, adminPhone, item.getRejectionReason());
        return repository.save(item);
    }

    /**
     * Delete a document.
     * RESTRICTED: Only Master Admin can delete documents! Regular users can only select, view, and download.
     */
    @Transactional
    public boolean deleteDocument(String docId, String callerPhone, boolean isAdmin) {
        if (!isAdmin) {
            throw new SecurityException("Access Denied: Only Master Admin can delete documents. Users can only select, view, and download documents.");
        }

        Optional<DocumentItem> opt = repository.findByDocIdIgnoreCase(docId);
        if (opt.isEmpty()) {
            return false;
        }

        DocumentItem item = opt.get();

        // 1. Delete local stored file
        if (item.getStoredPath() != null) {
            try {
                Files.deleteIfExists(Path.of(item.getStoredPath()));
            } catch (IOException e) {
                log.warn("Could not delete local file for {}: {}", docId, e.getMessage());
            }
        }

        // 2. Delete Drive sync file if present
        if (item.getDriveSyncPath() != null) {
            try {
                Files.deleteIfExists(Path.of(item.getDriveSyncPath()));
            } catch (IOException e) {
                log.warn("Could not delete Drive sync file for {}: {}", docId, e.getMessage());
            }
        }

        // 3. Delete DB record
        repository.delete(item);
        log.info("Admin +{} permanently deleted document {}", callerPhone, docId);
        return true;
    }

    /**
     * Automatic case-insensitive document category detection based on filename and caption.
     */
    public static String detectCategoryTag(String fileName, String caption) {
        String combined = ((fileName != null ? fileName : "") + " " + (caption != null ? caption : "")).toLowerCase();
        if (combined.contains("aadhaar") || combined.contains("aadhar") || combined.contains("adhar") || combined.contains("uidai")) {
            return "AADHAAR";
        }
        if (combined.contains("pan") || combined.contains("pancard")) {
            return "PAN";
        }
        if (combined.contains("driving") || combined.contains("license") || combined.contains("licence") || combined.matches(".*\\b(dl)\\b.*")) {
            return "DRIVING_LICENSE";
        }
        if (combined.contains("passport") || combined.contains("visa")) {
            return "PASSPORT";
        }
        if (combined.contains("health") || combined.contains("insurance") || combined.contains("mediclaim") || combined.contains("medical") || combined.contains("hospital")) {
            return "HEALTH";
        }
        return "OTHER";
    }

    /**
     * Build clean, standardized document names for Google Drive vault.
     */
    public static String buildCleanDocName(String originalFileName, String categoryTag, String docId, String extension) {
        String ext = (extension != null && !extension.isBlank()) ? (extension.startsWith(".") ? extension : "." + extension) : "";
        if (originalFileName != null && !originalFileName.isBlank() && !originalFileName.equalsIgnoreCase("uploaded_document") && !originalFileName.equalsIgnoreCase("blob")) {
            String sanitized = originalFileName.replaceFirst("\\.[^.]+$", "").replaceAll("[^a-zA-Z0-9_-]", "_");
            return sanitized + ext;
        }
        String baseName = switch (categoryTag != null ? categoryTag.toUpperCase() : "OTHER") {
            case "AADHAAR" -> "Aadhaar";
            case "PAN" -> "PAN";
            case "DRIVING_LICENSE" -> "Driving_License";
            case "PASSPORT" -> "Passport";
            case "HEALTH" -> "Health_Insurance";
            default -> docId;
        };
        return baseName + ext;
    }

    /**
     * Recent documents query with number-bias check:
     * - Admin sees recent documents across all family members.
     * - Member sees only their own recent documents.
     */
    public List<DocumentItem> findRecentDocuments(String senderPhone, boolean isAdmin) {
        if (isAdmin) {
            return repository.findTop10ByOrderByCreatedAtDesc();
        }
        return repository.findTop10BySenderPhoneNumberOrderByCreatedAtDesc(senderPhone);
    }

    /**
     * Retrieve all documents for a specific user phone number.
     */
    public List<DocumentItem> findDocumentsByUser(String senderPhone) {
        return repository.findBySenderPhoneNumberOrderByCreatedAtDesc(senderPhone);
    }

    /**
     * Retrieve all documents across all users (Admin view).
     */
    public List<DocumentItem> findAllDocuments() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * List all physical files present in a user's Google Drive folder.
     */
    public List<Path> listDriveFiles(String folderName) {
        if (driveSyncService != null) {
            return driveSyncService.listUserDriveFiles(folderName);
        }
        return Collections.emptyList();
    }

    /**
     * Search with number-bias check:
     * - Admin searches across all family documents.
     * - Member searches strictly within their own documents.
     */
    public List<DocumentItem> search(String keyword, String senderPhone, boolean isAdmin) {
        if (isAdmin) {
            return repository.searchByKeyword(keyword);
        }
        return repository.searchByKeywordAndSender(keyword, senderPhone);
    }

    /**
     * Verify if a user is permitted to access a specific document.
     */
    public boolean canAccess(DocumentItem item, String senderPhone, boolean isAdmin) {
        if (item == null) return false;
        if (isAdmin) return true;
        return item.getSenderPhoneNumber().equals(senderPhone);
    }

    /**
     * Storage statistics:
     * - Admin gets system-wide stats.
     * - Member gets their personal quota/usage stats.
     */
    public StorageStats getStats(String senderPhone, boolean isAdmin) {
        if (isAdmin) {
            long totalDocs = repository.countByType(DocumentType.DOCUMENT);
            long totalImgs = repository.countByType(DocumentType.IMAGE);
            long totalNotes = repository.countByType(DocumentType.NOTE);
            long totalAll = repository.count();
            long totalBytes = repository.findAll().stream()
                    .mapToLong(d -> d.getFileSizeBytes() != null ? d.getFileSizeBytes() : 0L)
                    .sum();
            return new StorageStats(totalAll, totalDocs, totalImgs, totalNotes, totalBytes, true);
        } else {
            long totalDocs = repository.countBySenderPhoneNumberAndType(senderPhone, DocumentType.DOCUMENT);
            long totalImgs = repository.countBySenderPhoneNumberAndType(senderPhone, DocumentType.IMAGE);
            long totalNotes = repository.countBySenderPhoneNumberAndType(senderPhone, DocumentType.NOTE);
            long totalAll = repository.countBySenderPhoneNumber(senderPhone);
            long totalBytes = repository.findBySenderPhoneNumberOrderByCreatedAtDesc(senderPhone).stream()
                    .mapToLong(d -> d.getFileSizeBytes() != null ? d.getFileSizeBytes() : 0L)
                    .sum();
            return new StorageStats(totalAll, totalDocs, totalImgs, totalNotes, totalBytes, false);
        }
    }

    private String extractExtension(String filename, String mimeType) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf("."));
        }
        if (mimeType != null) {
            return switch (mimeType.toLowerCase()) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "application/pdf" -> ".pdf";
                case "text/plain" -> ".txt";
                case "application/msword" -> ".doc";
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
                case "application/vnd.ms-excel" -> ".xls";
                case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx";
                default -> "";
            };
        }
        return "";
    }

    public static String calculateSha256(byte[] data) {
        if (data == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        // Remove path components (e.g., ../../../foo.pdf -> foo.pdf or C:\foo\bar -> bar)
        String baseName = name.replaceAll("^.*[\\\\/]", "");
        // Remove sequences of dots to eliminate directory traversal like ..
        baseName = baseName.replaceAll("\\.{2,}", "_");
        // Keep only alphanumeric, single dots, underscores, dashes
        String sanitized = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "file" : sanitized;
    }

    /**
     * Load decrypted bytes of a stored document, photo, or note.
     * If unencrypted (e.g. legacy/test file), returns raw bytes.
     */
    public byte[] loadDecryptedBytes(DocumentItem item) throws IOException {
        if (item == null || item.getStoredPath() == null) {
            throw new IllegalArgumentException("Document item or stored path is null");
        }
        Path path = Path.of(item.getStoredPath());
        if (!Files.exists(path)) {
            throw new IOException("File not found on disk: " + item.getStoredPath());
        }
        byte[] rawBytes = Files.readAllBytes(path);
        return (encryptionService != null) ? encryptionService.decrypt(rawBytes) : rawBytes;
    }

    /**
     * Obtains a temporary decrypted file handle for sending via WhatsApp or export.
     * The temp file is marked for deletion on JVM exit.
     */
    public java.io.File getDecryptedTempFile(DocumentItem item) throws IOException {
        byte[] decrypted = loadDecryptedBytes(item);
        String prefix = item.getDocId() != null ? item.getDocId() : "vault";
        String orig = item.getOriginalFileName();
        String suffix = (orig != null && orig.contains("."))
                ? orig.substring(orig.lastIndexOf("."))
                : ".tmp";
        Path tempPath = Files.createTempFile(prefix + "_decrypted_", suffix);
        Files.write(tempPath, decrypted);
        java.io.File tempFile = tempPath.toFile();
        tempFile.deleteOnExit();
        return tempFile;
    }

    public record StorageStats(long totalItems, long totalDocuments, long totalImages,
                               long totalNotes, long totalSizeBytes, boolean isAdminView) {
        public String getFormattedSize() {
            if (totalSizeBytes < 1024) return totalSizeBytes + " B";
            if (totalSizeBytes < 1024 * 1024) return String.format("%.2f KB", totalSizeBytes / 1024.0);
            if (totalSizeBytes < 1024 * 1024 * 1024) return String.format("%.2f MB", totalSizeBytes / (1024.0 * 1024.0));
            return String.format("%.2f GB", totalSizeBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
