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
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(null, new byte[12], new byte[16], "key", 0);
        });
    }
    
    @Test
    void testEncryptedDataEmptyCiphertext() {
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[0], new byte[12], new byte[16], "key", 0);
        });
    }
    
    @Test
    void testEncryptedDataNullNonce() {
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[32], null, new byte[16], "key", 0);
        });
    }
    
    @Test
    void testEncryptedDataEmptyNonce() {
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[32], new byte[0], new byte[16], "key", 0);
        });
    }
    
    @Test
    void testEncryptedDataNullAuthTag() {
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[32], new byte[12], null, "key", 0);
        });
    }
    
    @Test
    void testEncryptedDataEmptyAuthTag() {
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[32], new byte[12], new byte[0], "key", 0);
        });
    }
    
    @Test
    void testEncryptedDataBlankKeyId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[32], new byte[12], new byte[16], "", 0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[32], new byte[12], new byte[16], "   ", 0);
        });
    }
    
    @Test
    void testEncryptedDataNullKeyId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[32], new byte[12], new byte[16], null, 0);
        });
    }
    
    @Test
    void testEncryptedDataNegativeVersion() {
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptedData(new byte[32], new byte[12], new byte[16], "key", -1);
        });
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
    }
}
