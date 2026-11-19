package com.family.chatbot.model;

/**
 * Roles for access control:
 * - ADMIN: Master family administrator. Full access to all family documents, search, and Google Drive vault.
 * - MEMBER: Whitelisted family member. Isolated access only to their own uploaded documents and photos.
 * - UNAUTHORIZED: Non-family number. Access strictly denied.
 */
public enum UserRole {
    ADMIN,
    MEMBER,
    UNAUTHORIZED
}
