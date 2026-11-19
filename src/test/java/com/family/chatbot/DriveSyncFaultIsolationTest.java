package com.family.chatbot;

import com.family.chatbot.event.MediaUploadedEvent;
import com.family.chatbot.model.AuditEventType;
import com.family.chatbot.model.AuditLog;
import com.family.chatbot.model.AuditStatus;
import com.family.chatbot.repository.AuditLogRepository;
import com.family.chatbot.repository.DocumentRepository;
import com.family.chatbot.service.AuditLogService;
import com.family.chatbot.service.sync.DriveSyncEventListener;
import com.family.chatbot.service.sync.DriveSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DriveSyncFaultIsolationTest {

    @Test
    void testFaultIsolation_WhenDriveSyncFails_CoreVaultDoesNotCrash() throws IOException {
        DriveSyncService mockDriveSync = Mockito.mock(DriveSyncService.class);
        DocumentRepository mockDocRepo = Mockito.mock(DocumentRepository.class);
        AuditLogRepository mockAuditRepo = Mockito.mock(AuditLogRepository.class);

        when(mockAuditRepo.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // Simulate complete Google Drive outage or network failure
        when(mockDriveSync.syncFile(any(Path.class), anyString(), anyString()))
                .thenThrow(new IOException("Simulated Google Drive API Outage / Network Timeout"));

        AuditLogService auditLogService = new AuditLogService(mockAuditRepo);
        DriveSyncEventListener listener = new DriveSyncEventListener(mockDriveSync, mockDocRepo, auditLogService);

        MediaUploadedEvent event = new MediaUploadedEvent(
                "DOC-FAIL-1",
                Path.of("./test.pdf"),
                "documents",
                "Dad",
                "919876543211"
        );

        // Fault Isolation Assertion: Must catch error gracefully without throwing exception
        assertDoesNotThrow(() -> listener.onMediaUploaded(event));

        // Verify that failure was recorded in audit log as non-fatal without crashing
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mockAuditRepo).save(captor.capture());

        boolean recordedSyncFailure = captor.getAllValues().stream()
                .anyMatch(l -> l.getEventType() == AuditEventType.DRIVE_SYNC &&
                               l.getStatus() == AuditStatus.ERROR &&
                               l.getAction().equals("ASYNC_DRIVE_SYNC_FAILED"));

        assertTrue(recordedSyncFailure, "Must record non-fatal async sync error in audit logs");
    }
}
