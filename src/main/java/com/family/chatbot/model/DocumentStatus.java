package com.family.chatbot.model;

/**
 * Status of a document in the approval lifecycle.
 * - PENDING_APPROVAL: Uploaded by a family member, awaiting Admin verification before syncing to Drive.
 * - APPROVED: Approved by Admin and synchronized to the official Google Drive folder.
 * - REJECTED: Rejected by Admin with an explanation.
 */
public enum DocumentStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
