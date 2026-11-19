package com.family.chatbot.service.sync;

import com.family.chatbot.event.MediaUploadedEvent;
import com.family.chatbot.model.AuditEventType;
import com.family.chatbot.model.AuditStatus;
import com.family.chatbot.model.DocumentItem;
import com.family.chatbot.repository.DocumentRepository;
import com.family.chatbot.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Isolated Microservice Event Listener for Google Drive Synchronization.
 * Runs in an isolated background thread pool (driveSyncExecutor).
 * Failure here is completely isolated and NEVER affects document storage or WhatsApp message delivery.
 */
@Component
public class DriveSyncEventListener {

    private static final Logger log = LoggerFactory.getLogger(DriveSyncEventListener.class);

    private final DriveSyncService driveSyncService;
    private final DocumentRepository documentRepository;
    private final AuditLogService auditLogService;

    public DriveSyncEventListener(DriveSyncService driveSyncService,
                                  DocumentRepository documentRepository,
                                  AuditLogService auditLogService) {
        this.driveSyncService = driveSyncService;
        this.documentRepository = documentRepository;
        this.auditLogService = auditLogService;
    }

    @Async("driveSyncExecutor")
    @EventListener
    public void onMediaUploaded(MediaUploadedEvent event) {
        log.info("[DriveSyncWorker] Starting isolated background sync for docId: {} in folder [{}]",
                event.docId(), event.folderName());

        try {
            Path syncedPath = driveSyncService.syncFile(event.filePath(), event.category(), event.folderName());

            if (syncedPath != null) {
                Optional<DocumentItem> opt = documentRepository.findByDocIdIgnoreCase(event.docId());
                if (opt.isPresent()) {
                    DocumentItem item = opt.get();
                    item.setSyncedToDrive(true);
                    item.setDriveSyncPath(syncedPath.toAbsolutePath().toString());
                    documentRepository.save(item);
                }

                auditLogService.record(
                        AuditEventType.DRIVE_SYNC,
                        AuditStatus.SUCCESS,
                        event.senderPhone(),
                        "SYSTEM",
                        "ASYNC_DRIVE_SYNC",
                        event.docId(),
                        "Successfully synced to Google Drive: " + syncedPath.getFileName()
                );
                log.info("✅ [DriveSyncWorker] Successfully mirrored docId: {} to Drive", event.docId());
            }

        } catch (Exception e) {
            // Fault Isolation: Log failure and record in audit, but never crash the core application
            log.error("⚠️ [DriveSyncWorker] Drive sync failed for docId {}: {}. Core vault unaffected.",
                    event.docId(), e.getMessage());

            auditLogService.record(
                    AuditEventType.DRIVE_SYNC,
                    AuditStatus.ERROR,
                    event.senderPhone(),
                    "SYSTEM",
                    "ASYNC_DRIVE_SYNC_FAILED",
                    event.docId(),
                    "Non-fatal sync error: " + e.getMessage()
            );
        }
    }
}
