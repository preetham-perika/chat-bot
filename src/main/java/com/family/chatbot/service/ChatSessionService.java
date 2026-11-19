package com.family.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing user session lifecycle.
 * Enforces strict session initiation with 'hii' (case-insensitive)
 * and sliding expiration for inactive sessions.
 */
@Service
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    /**
     * Session idle timeout: 20 minutes of inactivity.
     */
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(20);

    private final ConcurrentHashMap<String, Instant> activeSessions = new ConcurrentHashMap<>();

    /**
     * Check if a session is currently active for the given phone number.
     * Automatically invalidates expired sessions.
     */
    public boolean isSessionActive(String rawPhoneNumber) {
        String phone = normalize(rawPhoneNumber);
        if (phone.isBlank()) return false;

        Instant lastActive = activeSessions.get(phone);
        if (lastActive == null) {
            return false;
        }

        if (Duration.between(lastActive, Instant.now()).compareTo(SESSION_TIMEOUT) > 0) {
            activeSessions.remove(phone);
            log.info("Session expired due to inactivity for phone: +{}", phone);
            return false;
        }

        return true;
    }

    /**
     * Start or renew a session upon receiving 'hii'.
     */
    public void startSession(String rawPhoneNumber) {
        String phone = normalize(rawPhoneNumber);
        if (!phone.isBlank()) {
            activeSessions.put(phone, Instant.now());
            log.info("Session started/renewed with 'hii' for phone: +{}", phone);
        }
    }

    /**
     * Refresh the session's sliding expiration window.
     */
    public void refreshSession(String rawPhoneNumber) {
        String phone = normalize(rawPhoneNumber);
        if (activeSessions.containsKey(phone)) {
            activeSessions.put(phone, Instant.now());
        }
    }

    /**
     * Explicitly terminate a user session (e.g. on 'bye', 'exit', '/exit').
     */
    public void endSession(String rawPhoneNumber) {
        String phone = normalize(rawPhoneNumber);
        activeSessions.remove(phone);
        log.info("Session ended for phone: +{}", phone);
    }

    /**
     * Clear all sessions (used for system reset or testing).
     */
    public void clearAllSessions() {
        activeSessions.clear();
    }

    private String normalize(String phone) {
        if (phone == null) return "";
        String clean = phone.trim().toLowerCase();
        if (clean.startsWith("whatsapp:")) {
            clean = clean.substring("whatsapp:".length()).trim();
        }
        return clean.replaceAll("[^0-9]", "");
    }
}
