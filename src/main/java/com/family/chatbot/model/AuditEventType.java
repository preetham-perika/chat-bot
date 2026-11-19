package com.family.chatbot.model;

/**
 * Types of audit and operational events logged by the system.
 */
public enum AuditEventType {
    UNAUTHORIZED_ACCESS,       // Attempt from non-whitelisted number
    NUMBER_BIAS_VIOLATION,     // Member trying to access another member's document
    FILE_UPLOAD,               // Storing document or photo
    FILE_ACCESS,               // Fetching/retrieving document details
    NOTE_CREATED,              // Documentation note saved
    SEARCH,                    // Search query executed
    COMMAND,                   // Menu or bot command executed
    DRIVE_SYNC,                // File synchronized to Google Drive folder
    SYSTEM                     // System initialization or configuration event
}
