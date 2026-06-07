package lt.satsyuk.blockchainsecretsvault.kms.service;

import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptedData;
import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptionKey;
import lt.satsyuk.blockchainsecretsvault.kms.model.KeyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmKmsServiceTest {
    
    private AesGcmKmsService kmsService;
    private static final String TEST_KEY_ID = "test-key";
    private static final byte[] TEST_PLAINTEXT = "Secret data to encrypt".getBytes();
    private static final Instant FIXED_INSTANT = Instant.parse("2024-01-01T00:00:00Z");
    
    @BeforeEach
    void setUp() {
        kmsService = new AesGcmKmsService();
    }
    
    @Test
    void testGenerateKey() {
        EncryptionKey key = kmsService.generateKey(TEST_KEY_ID);
        
        assertNotNull(key);
        assertEquals(TEST_KEY_ID, key.keyId());
        assertEquals(0, key.version());
        assertEquals(KeyStatus.ACTIVE, key.status());
        assertNotNull(key.keyMaterial());
        assertTrue(key.keyMaterial().length > 0);
        assertNotNull(key.createdAt());
        assertNull(key.rotatedAt());
    }
    
    @Test
    void testGenerateKeyDuplicate() {
        kmsService.generateKey(TEST_KEY_ID);
        
        assertThrows(IllegalArgumentException.class, () -> {
            kmsService.generateKey(TEST_KEY_ID);
        });
    }

    @Test
    void testGenerateKeyNullId() {
        assertThrows(IllegalArgumentException.class, () -> kmsService.generateKey(null));
    }

    @Test
    void testGenerateKeyBlankId() {
        assertThrows(IllegalArgumentException.class, () -> kmsService.generateKey("   "));
    }
    
    @Test
    void testEncrypt() {
        kmsService.generateKey(TEST_KEY_ID);
        
        EncryptedData encrypted = kmsService.encrypt(TEST_KEY_ID, TEST_PLAINTEXT);
        
        assertNotNull(encrypted);
        assertEquals(TEST_KEY_ID, encrypted.keyId());
        assertEquals(0, encrypted.keyVersion());
        assertNotNull(encrypted.ciphertext());
        assertNotNull(encrypted.nonce());
        assertNotNull(encrypted.authTag());
        assertNotNull(encrypted.encryptedDataKey());
        assertNotNull(encrypted.dataKeyNonce());
        assertNotNull(encrypted.dataKeyAuthTag());
        assertTrue(encrypted.envelopeEncrypted());
        assertTrue(encrypted.ciphertext().length > 0);
        assertEquals(12, encrypted.nonce().length);
        assertEquals(16, encrypted.authTag().length);
        assertEquals(32, encrypted.encryptedDataKey().length);
        assertEquals(12, encrypted.dataKeyNonce().length);
        assertEquals(16, encrypted.dataKeyAuthTag().length);
    }
    
    @Test
    void testEncryptWithEmptyPlaintext() {
        kmsService.generateKey(TEST_KEY_ID);
        
        assertThrows(IllegalArgumentException.class, () -> {
            kmsService.encrypt(TEST_KEY_ID, new byte[0]);
        });
    }
    
    @Test
    void testEncryptWithNullPlaintext() {
        kmsService.generateKey(TEST_KEY_ID);
        
        assertThrows(IllegalArgumentException.class, () -> {
            kmsService.encrypt(TEST_KEY_ID, null);
        });
    }
    
    @Test
    void testEncryptWithNonExistentKey() {
        assertThrows(KeyNotFoundException.class, () -> {
            kmsService.encrypt("non-existent", TEST_PLAINTEXT);
        });
    }
    
    @Test
    void testDecrypt() {
        kmsService.generateKey(TEST_KEY_ID);
        
        EncryptedData encrypted = kmsService.encrypt(TEST_KEY_ID, TEST_PLAINTEXT);
        byte[] decrypted = kmsService.decrypt(encrypted);
        
        assertArrayEquals(TEST_PLAINTEXT, decrypted);
    }

    @Test
    void testDecryptNullEncryptedData() {
        assertThrows(IllegalArgumentException.class, () -> kmsService.decrypt(null));
    }
    
    @Test
    void testDecryptWithCorruptedCiphertext() {
        kmsService.generateKey(TEST_KEY_ID);
        
        EncryptedData encrypted = kmsService.encrypt(TEST_KEY_ID, TEST_PLAINTEXT);
        byte[] corruptedCiphertext = new byte[encrypted.ciphertext().length];
        System.arraycopy(encrypted.ciphertext(), 0, corruptedCiphertext, 0, encrypted.ciphertext().length);
        corruptedCiphertext[0] ^= 0xFF;
        
        EncryptedData corrupted = new EncryptedData(
            corruptedCiphertext,
            encrypted.nonce(),
            encrypted.authTag(),
            encrypted.keyId(),
            encrypted.keyVersion()
        );
        
        assertThrows(DecryptionFailedException.class, () -> {
            kmsService.decrypt(corrupted);
        });
    }
    
    @Test
    void testDecryptWithWrongKey() {
        kmsService.generateKey(TEST_KEY_ID);
        kmsService.generateKey("another-key");
        
        EncryptedData encrypted = kmsService.encrypt(TEST_KEY_ID, TEST_PLAINTEXT);
        
        EncryptedData wrongKeyData = new EncryptedData(
            encrypted.ciphertext(),
            encrypted.nonce(),
            encrypted.authTag(),
            "another-key",
            0
        );
        
        assertThrows(DecryptionFailedException.class, () -> {
            kmsService.decrypt(wrongKeyData);
        });
    }
    
    @Test
    void testRotateKey() {
        kmsService.generateKey(TEST_KEY_ID);
        
        EncryptionKey rotatedNewKey = kmsService.rotateKey(TEST_KEY_ID);
        
        assertNotNull(rotatedNewKey);
        assertEquals(TEST_KEY_ID, rotatedNewKey.keyId());
        assertEquals(1, rotatedNewKey.version());
        assertEquals(KeyStatus.ACTIVE, rotatedNewKey.status());
        assertNotNull(rotatedNewKey.createdAt());
        assertNull(rotatedNewKey.rotatedAt());
    }
    
    @Test
    void testEncryptWithOldKeyAfterRotation() {
        kmsService.generateKey(TEST_KEY_ID);
        EncryptedData encryptedWithV0 = kmsService.encrypt(TEST_KEY_ID, TEST_PLAINTEXT);
        
        kmsService.rotateKey(TEST_KEY_ID);
        
        byte[] decrypted = kmsService.decrypt(encryptedWithV0);
        assertArrayEquals(TEST_PLAINTEXT, decrypted);
    }

    @Test
    void testRewrapDataKeyAfterRotationDoesNotChangeSecretCiphertext() {
        kmsService.generateKey(TEST_KEY_ID);
        EncryptedData encryptedWithV0 = kmsService.encrypt(TEST_KEY_ID, TEST_PLAINTEXT);

        kmsService.rotateKey(TEST_KEY_ID);
        EncryptedData rewrapped = kmsService.rewrapDataKey(encryptedWithV0);

        assertEquals(1, rewrapped.keyVersion());
        assertArrayEquals(encryptedWithV0.ciphertext(), rewrapped.ciphertext());
        assertArrayEquals(encryptedWithV0.nonce(), rewrapped.nonce());
        assertArrayEquals(encryptedWithV0.authTag(), rewrapped.authTag());
        assertFalse(java.util.Arrays.equals(encryptedWithV0.encryptedDataKey(), rewrapped.encryptedDataKey()));
        assertArrayEquals(TEST_PLAINTEXT, kmsService.decrypt(rewrapped));
    }

    @Test
    void testRewrapRejectsLegacyEncryptedData() {
        kmsService.generateKey(TEST_KEY_ID);
        EncryptedData legacy = new EncryptedData(new byte[32], new byte[12], new byte[16], TEST_KEY_ID, 0);

        assertThrows(IllegalArgumentException.class, () -> kmsService.rewrapDataKey(legacy));
    }

    @Test
    void testRewrapNullEncryptedData() {
        assertThrows(IllegalArgumentException.class, () -> kmsService.rewrapDataKey(null));
    }
    
    @Test
    void testGetKey() {
        kmsService.generateKey(TEST_KEY_ID);
        
        EncryptionKey key = kmsService.getKey(TEST_KEY_ID, 0);
        
        assertNotNull(key);
        assertEquals(TEST_KEY_ID, key.keyId());
        assertEquals(0, key.version());
    }
    
    @Test
    void testGetKeyNotFound() {
        assertThrows(KeyNotFoundException.class, () -> {
            kmsService.getKey(TEST_KEY_ID, 0);
        });
    }
    
    @Test
    void testGetKeyWrongVersion() {
        kmsService.generateKey(TEST_KEY_ID);
        
        assertThrows(KeyNotFoundException.class, () -> {
            kmsService.getKey(TEST_KEY_ID, 99);
        });
    }
    
    @Test
    void testGetActiveKey() {
        EncryptionKey generatedKey = kmsService.generateKey(TEST_KEY_ID);
        
        EncryptionKey activeKey = kmsService.getActiveKey(TEST_KEY_ID);
        
        assertNotNull(activeKey);
        assertEquals(generatedKey.keyId(), activeKey.keyId());
        assertEquals(generatedKey.version(), activeKey.version());
        assertEquals(KeyStatus.ACTIVE, activeKey.status());
    }
    
    @Test
    void testGetActiveKeyAfterRotation() {
        kmsService.generateKey(TEST_KEY_ID);
        EncryptionKey rotatedKey = kmsService.rotateKey(TEST_KEY_ID);
        
        EncryptionKey activeKey = kmsService.getActiveKey(TEST_KEY_ID);
        
        assertEquals(rotatedKey.version(), activeKey.version());
        assertEquals(KeyStatus.ACTIVE, activeKey.status());
    }
    
    @Test
    void testGetActiveKeyNotFound() {
        assertThrows(KeyNotFoundException.class, () -> {
            kmsService.getActiveKey("non-existent");
        });
    }
    
    @Test
    void testCompromiseKey() {
        kmsService.generateKey(TEST_KEY_ID);
        
        EncryptionKey compromisedKey = kmsService.compromiseKey(TEST_KEY_ID);
        
        assertNotNull(compromisedKey);
        assertEquals(KeyStatus.COMPROMISED, compromisedKey.status());
        assertNotNull(compromisedKey.rotatedAt());
    }

    @Test
    void testGetActiveKeyAfterCompromise() {
        kmsService.generateKey(TEST_KEY_ID);
        kmsService.compromiseKey(TEST_KEY_ID);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> kmsService.getActiveKey(TEST_KEY_ID));
        assertTrue(exception.getMessage().contains("not active"));
    }

    @Test
    void testEncryptAfterCompromise() {
        kmsService.generateKey(TEST_KEY_ID);
        kmsService.compromiseKey(TEST_KEY_ID);

        assertThrows(IllegalStateException.class, () -> kmsService.encrypt(TEST_KEY_ID, TEST_PLAINTEXT));
    }

    @Test
    void testDecryptAfterCompromise() {
        kmsService.generateKey(TEST_KEY_ID);
        EncryptedData encryptedBeforeCompromise = kmsService.encrypt(TEST_KEY_ID, TEST_PLAINTEXT);

        kmsService.compromiseKey(TEST_KEY_ID);

        byte[] decrypted = kmsService.decrypt(encryptedBeforeCompromise);
        assertArrayEquals(TEST_PLAINTEXT, decrypted);
    }
    
    @Test
    void testCompromiseNonExistentKey() {
        assertThrows(KeyNotFoundException.class, () -> {
            kmsService.compromiseKey("non-existent");
        });
    }

    @Test
    void testRetireKey() {
        kmsService.generateKey(TEST_KEY_ID);

        EncryptionKey retiredKey = kmsService.retireKey(TEST_KEY_ID);

        assertEquals(KeyStatus.RETIRED, retiredKey.status());
        assertNotNull(retiredKey.rotatedAt());
    }

    @Test
    void testEncryptAfterRetire() {
        kmsService.generateKey(TEST_KEY_ID);
        kmsService.retireKey(TEST_KEY_ID);

        assertThrows(IllegalStateException.class, () -> kmsService.encrypt(TEST_KEY_ID, TEST_PLAINTEXT));
    }

    @Test
    void testRotateAfterRetire() {
        kmsService.generateKey(TEST_KEY_ID);
        kmsService.retireKey(TEST_KEY_ID);

        assertThrows(IllegalStateException.class, () -> kmsService.rotateKey(TEST_KEY_ID));
    }

    @Test
    void testDecryptAfterRetire() {
        kmsService.generateKey(TEST_KEY_ID);
        EncryptedData encryptedBeforeRetire = kmsService.encrypt(TEST_KEY_ID, TEST_PLAINTEXT);

        kmsService.retireKey(TEST_KEY_ID);

        byte[] decrypted = kmsService.decrypt(encryptedBeforeRetire);
        assertArrayEquals(TEST_PLAINTEXT, decrypted);
    }

    @Test
    void testRetireNonExistentKey() {
        assertThrows(KeyNotFoundException.class, () -> kmsService.retireKey("non-existent"));
    }
    
    @Test
    void testMultipleEncryptionsProduceDifferentCiphertexts() {
        kmsService.generateKey(TEST_KEY_ID);
        
        EncryptedData encrypted1 = kmsService.encrypt(TEST_KEY_ID, TEST_PLAINTEXT);
        EncryptedData encrypted2 = kmsService.encrypt(TEST_KEY_ID, TEST_PLAINTEXT);
        
        assertFalse(java.util.Arrays.equals(encrypted1.nonce(), encrypted2.nonce()));
        assertFalse(java.util.Arrays.equals(encrypted1.ciphertext(), encrypted2.ciphertext()));
    }
    
    @Test
    void testEncryptDecryptMultipleTimes() {
        kmsService.generateKey(TEST_KEY_ID);
        
        for (int i = 0; i < 5; i++) {
            byte[] plaintext = ("Test plaintext " + i).getBytes();
            EncryptedData encrypted = kmsService.encrypt(TEST_KEY_ID, plaintext);
            byte[] decrypted = kmsService.decrypt(encrypted);
            
            assertArrayEquals(plaintext, decrypted);
        }
    }
    
    @Test
    void testRotateKeyMultipleTimes() {
        kmsService.generateKey(TEST_KEY_ID);
        
        for (int i = 0; i < 3; i++) {
            EncryptionKey rotatedKey = kmsService.rotateKey(TEST_KEY_ID);
            assertEquals(i + 1, rotatedKey.version());
            assertEquals(KeyStatus.ACTIVE, rotatedKey.status());
        }
    }
    
    @Test
    void testEncryptedDataValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[0], new byte[12], new byte[16], "key", 0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[10], new byte[0], new byte[16], "key", 0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[10], new byte[12], new byte[0], "key", 0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[10], new byte[12], new byte[16], "", 0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[10], new byte[12], new byte[16], "key", -1);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[10], new byte[12], new byte[16], "key", 0, new byte[32], null, new byte[16]);
        });
    }
    
    @Test
    void testEncryptionKeyValidation() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionKey("", new byte[32], 0, KeyStatus.ACTIVE, FIXED_INSTANT, null));
        assertThrows(IllegalArgumentException.class, () -> new EncryptionKey("key", new byte[0], 0, KeyStatus.ACTIVE, FIXED_INSTANT, null));
        assertThrows(IllegalArgumentException.class, () -> new EncryptionKey("key", new byte[32], -1, KeyStatus.ACTIVE, FIXED_INSTANT, null));
    }
}
