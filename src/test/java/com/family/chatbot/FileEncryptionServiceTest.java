package com.family.chatbot;

import com.family.chatbot.config.SecurityProperties;
import com.family.chatbot.service.FileEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FileEncryptionServiceTest {

    private FileEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.setEncryptionKey("Test-Secret-Key-AES256-Custom-Key-12345");
        encryptionService = new FileEncryptionService(properties);
        encryptionService.init();
    }

    @Test
    void testEncryptDecrypt_RoundTrip_Success() {
        String originalText = "Aadhaar Number: 1234-5678-9012, Confidential Citizen Identity";
        byte[] originalBytes = originalText.getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = encryptionService.encrypt(originalBytes);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > originalBytes.length);
        assertTrue(encryptionService.isEncrypted(encrypted));

        // Ensure ciphertext does not expose plaintext
        String cipherString = new String(encrypted, StandardCharsets.UTF_8);
        assertFalse(cipherString.contains(originalText));

        // Decrypt
        byte[] decrypted = encryptionService.decrypt(encrypted);
        assertEquals(originalText, new String(decrypted, StandardCharsets.UTF_8));
    }

    @Test
    void testDecrypt_LegacyUnencryptedData_ReturnsOriginalBytes() {
        String legacyDoc = "Unencrypted legacy doc from before encryption was introduced";
        byte[] legacyBytes = legacyDoc.getBytes(StandardCharsets.UTF_8);

        assertFalse(encryptionService.isEncrypted(legacyBytes));

        byte[] result = encryptionService.decrypt(legacyBytes);
        assertArrayEquals(legacyBytes, result, "Legacy unencrypted data should be returned untouched");
    }

    @Test
    void testTamperedEncryptedData_ThrowsSecurityException() {
        byte[] original = "Confidential Banking Statement".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = encryptionService.encrypt(original);

        // Tamper with ciphertext byte
        encrypted[encrypted.length - 1] ^= 0x5A;

        assertThrows(SecurityException.class, () -> encryptionService.decrypt(encrypted),
                "Tampered ciphertext should fail GCM authentication check");
    }

    @Test
    void testEmptyOrNullHandling() {
        assertNull(encryptionService.encrypt(null));
        assertNull(encryptionService.decrypt(null));
        assertEquals(0, encryptionService.encrypt(new byte[0]).length);
        assertEquals(0, encryptionService.decrypt(new byte[0]).length);
    }
}
