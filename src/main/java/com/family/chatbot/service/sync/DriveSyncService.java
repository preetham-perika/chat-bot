package com.family.chatbot.service.sync;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for synchronizing stored documents with Google Drive / external folder.
 * Organizes files into member-specific directories so the Admin has a clean, organized view in Drive.
 */
public interface DriveSyncService {

    /**
     * Synchronize a file to the drive folder under the member's specific subfolder.
     *
     * @param sourceFile  path of the stored file on disk
     * @param category    subfolder category (e.g. "documents", "images", "notes")
     * @param folderName  user or member folder name (e.g. "Dad", "Hruday")
     * @return target synced Path
     */
    Path syncFile(Path sourceFile, String category, String folderName) throws IOException;

    /**
     * Check if the drive sync destination is accessible.
     */
    boolean isAvailable();

    /**
     * Lists all files physically present in the member's Drive subfolder.
     */
    default java.util.List<Path> listUserDriveFiles(String folderName) {
        return java.util.Collections.emptyList();
    }

    /**
     * Pre-creates the member folder in Google Drive cloud and local mirror.
     * Ensures chatbot_documents and chatbot_documents/{folderName} exist immediately upon user creation.
     */
    default void createMemberFolder(String folderName) {
        // default no-op
    }
}
