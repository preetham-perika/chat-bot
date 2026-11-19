package com.family.chatbot.service;

import com.family.chatbot.config.SecurityProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Military-grade AES-256-GCM File Encryption Service.
 * Ensures zero plaintext storage of sensitive documents (Aadhaar, PAN, Passports) on disk.
 * Supports backward-compatible decryption for unencrypted legacy or test files via a magic header.
 */
@Service
public class FileEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(FileEncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // 128 bits
    private static final int IV_LENGTH = 12;       // 12 bytes recommended for GCM
    private static final byte[] MAGIC_HEADER = "ENC_AES256_GCM_V1:".getBytes(StandardCharsets.UTF_8);

    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec secretKeySpec;

    public FileEncryptionService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @PostConstruct
    public void init() {
        try {
            String rawKey = securityProperties.getEncryptionKey();
            if (rawKey == null || rawKey.isBlank()) {
                rawKey = "ChatBot-Vault-2026-Secret-Key-AES256";
            }
            // Derive a 256-bit (32-byte) key using SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            this.secretKeySpec = new SecretKeySpec(keyBytes, "AES");
            log.info("FileEncryptionService initialized with AES-256-GCM encryption at rest.");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize AES-256 encryption service", e);
        }
    }

    /**
     * Encrypts plaintext file data with AES-256-GCM.
     * Output format: [MAGIC_HEADER (18 bytes)] [IV (12 bytes)] [CIPHERTEXT + TAG]
     *
     * @param plainData raw file bytes
     * @return encrypted file bytes
     */
    public byte[] encrypt(byte[] plainData) {
        if (plainData == null || plainData.length == 0) {
            return plainData;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, spec);

            byte[] cipherText = cipher.doFinal(plainData);

            byte[] result = new byte[MAGIC_HEADER.length + iv.length + cipherText.length];
            System.arraycopy(MAGIC_HEADER, 0, result, 0, MAGIC_HEADER.length);
            System.arraycopy(iv, 0, result, MAGIC_HEADER.length, iv.length);
            System.arraycopy(cipherText, 0, result, MAGIC_HEADER.length + iv.length, cipherText.length);

            return result;
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new SecurityException("Failed to encrypt document data: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts file data. If the file is not encrypted (e.g. legacy/test file),
     * it returns the raw bytes untouched for seamless backward compatibility.
     *
     * @param storedData bytes loaded from disk
     * @return original plaintext bytes
     */
    public byte[] decrypt(byte[] storedData) {
        if (storedData == null || storedData.length == 0) {
            return storedData;
        }

        if (!isEncrypted(storedData)) {
            // Unencrypted legacy or test file -> return as-is
            return storedData;
        }

        try {
            int ivStart = MAGIC_HEADER.length;
            int cipherStart = ivStart + IV_LENGTH;

            byte[] iv = Arrays.copyOfRange(storedData, ivStart, cipherStart);
            byte[] cipherText = Arrays.copyOfRange(storedData, cipherStart, storedData.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, spec);

            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            throw new SecurityException("Failed to decrypt document data: invalid key or tampered file", e);
        }
    }

    /**
     * Checks if the given data starts with the encryption magic header.
     */
    public boolean isEncrypted(byte[] data) {
        if (data == null || data.length < MAGIC_HEADER.length + IV_LENGTH) {
            return false;
        }
        for (int i = 0; i < MAGIC_HEADER.length; i++) {
            if (data[i] != MAGIC_HEADER[i]) {
                return false;
            }
        }
        return true;
    }
}
