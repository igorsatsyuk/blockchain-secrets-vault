package lt.satsyuk.blockchainsecretsvault.kms.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedDataTest {
    
    @Test
    void testEncryptedDataCreation() {
        byte[] ciphertext = new byte[32];
        byte[] nonce = new byte[12];
        byte[] authTag = new byte[16];
        String keyId = "test-key";
        int keyVersion = 0;
        
        EncryptedData data = new EncryptedData(ciphertext, nonce, authTag, keyId, keyVersion);
        
        assertArrayEquals(ciphertext, data.ciphertext());
        assertArrayEquals(nonce, data.nonce());
        assertArrayEquals(authTag, data.authTag());
        assertEquals(keyId, data.keyId());
        assertEquals(keyVersion, data.keyVersion());
        assertFalse(data.envelopeEncrypted());
        assertNull(data.encryptedDataKey());
    }

    @Test
    void testEnvelopeEncryptedDataCreation() {
        byte[] ciphertext = new byte[32];
        byte[] nonce = new byte[12];
        byte[] authTag = new byte[16];
        byte[] encryptedDataKey = new byte[32];
        byte[] dataKeyNonce = new byte[12];
        byte[] dataKeyAuthTag = new byte[16];

        EncryptedData data = new EncryptedData(
                ciphertext,
                nonce,
                authTag,
                "test-key",
                1,
                encryptedDataKey,
                dataKeyNonce,
                dataKeyAuthTag
        );

        assertTrue(data.envelopeEncrypted());
        assertArrayEquals(encryptedDataKey, data.encryptedDataKey());
        assertArrayEquals(dataKeyNonce, data.dataKeyNonce());
        assertArrayEquals(dataKeyAuthTag, data.dataKeyAuthTag());
    }
    
    @Test
    void testEncryptedDataEquality() {
        byte[] ciphertext = new byte[32];
        byte[] nonce = new byte[12];
        byte[] authTag = new byte[16];
        
        EncryptedData data1 = new EncryptedData(ciphertext, nonce, authTag, "key", 0);
        EncryptedData data2 = new EncryptedData(ciphertext, nonce, authTag, "key", 0);
        
        assertEquals(data1, data2);
        assertEquals(data1.hashCode(), data2.hashCode());
    }
    
    @Test
    void testEncryptedDataInequality() {
        byte[] ciphertext = new byte[32];
        byte[] nonce = new byte[12];
        byte[] authTag = new byte[16];
        
        EncryptedData data1 = new EncryptedData(ciphertext, nonce, authTag, "key1", 0);
        EncryptedData data2 = new EncryptedData(ciphertext, nonce, authTag, "key2", 0);
        
        assertNotEquals(data1, data2);
    }
    
    @Test
    void testEncryptedDataNullCiphertext() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(null, new byte[12], new byte[16], "key", 0));
    }
    
    @Test
    void testEncryptedDataEmptyCiphertext() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(new byte[0], new byte[12], new byte[16], "key", 0));
    }
    
    @Test
    void testEncryptedDataNullNonce() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(new byte[32], null, new byte[16], "key", 0));
    }
    
    @Test
    void testEncryptedDataEmptyNonce() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(new byte[32], new byte[0], new byte[16], "key", 0));
    }
    
    @Test
    void testEncryptedDataNullAuthTag() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(new byte[32], new byte[12], null, "key", 0));
    }
    
    @Test
    void testEncryptedDataEmptyAuthTag() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(new byte[32], new byte[12], new byte[0], "key", 0));
    }
    
    @Test
    void testEncryptedDataBlankKeyId() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(new byte[32], new byte[12], new byte[16], "", 0));
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(new byte[32], new byte[12], new byte[16], "   ", 0));
    }
    
    @Test
    void testEncryptedDataNullKeyId() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(new byte[32], new byte[12], new byte[16], null, 0));
    }
    
    @Test
    void testEncryptedDataNegativeVersion() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(new byte[32], new byte[12], new byte[16], "key", -1));
    }

    @Test
    void testEncryptedDataToString() {
        byte[] ciphertext = new byte[32];
        byte[] nonce = new byte[12];
        byte[] authTag = new byte[16];
        
        EncryptedData data = new EncryptedData(ciphertext, nonce, authTag, "key", 0);
        String str = data.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("key"));
        assertTrue(str.contains("0"));
        assertTrue(str.contains("ciphertextLength=32"));
        assertTrue(str.contains("nonceLength=12"));
        assertTrue(str.contains("authTagLength=16"));
        assertFalse(str.contains("ciphertext=["));
        assertFalse(str.contains("nonce=["));
        assertFalse(str.contains("authTag=["));
        assertFalse(str.contains("encryptedDataKey=["));
    }

    @Test
    void testEncryptedDataDefensiveCopyOnConstruction() {
        byte[] ciphertext = new byte[32];
        byte[] nonce = new byte[12];
        byte[] authTag = new byte[16];
        ciphertext[0] = 1;
        nonce[0] = 2;
        authTag[0] = 3;

        EncryptedData data = new EncryptedData(ciphertext, nonce, authTag, "key", 0);
        ciphertext[0] = 9;
        nonce[0] = 9;
        authTag[0] = 9;

        assertEquals(1, data.ciphertext()[0]);
        assertEquals(2, data.nonce()[0]);
        assertEquals(3, data.authTag()[0]);
    }

    @Test
    void testEncryptedDataDefensiveCopyOnAccessor() {
        EncryptedData data = new EncryptedData(
                new byte[32],
                new byte[12],
                new byte[16],
                "key",
                0,
                new byte[32],
                new byte[12],
                new byte[16]
        );

        byte[] ciphertext = data.ciphertext();
        byte[] nonce = data.nonce();
        byte[] authTag = data.authTag();
        byte[] encryptedDataKey = data.encryptedDataKey();
        byte[] dataKeyNonce = data.dataKeyNonce();
        byte[] dataKeyAuthTag = data.dataKeyAuthTag();
        ciphertext[0] = 1;
        nonce[0] = 1;
        authTag[0] = 1;
        encryptedDataKey[0] = 1;
        dataKeyNonce[0] = 1;
        dataKeyAuthTag[0] = 1;

        assertEquals(0, data.ciphertext()[0]);
        assertEquals(0, data.nonce()[0]);
        assertEquals(0, data.authTag()[0]);
        assertEquals(0, data.encryptedDataKey()[0]);
        assertEquals(0, data.dataKeyNonce()[0]);
        assertEquals(0, data.dataKeyAuthTag()[0]);
    }

    @Test
    void testEnvelopeMetadataValidation() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(new byte[32], new byte[12], new byte[16], "key", 0, new byte[0], new byte[12], new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(new byte[32], new byte[12], new byte[16], "key", 0, new byte[32], new byte[0], new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new EncryptedData(new byte[32], new byte[12], new byte[16], "key", 0, new byte[32], new byte[12], new byte[0]));
    }
}
