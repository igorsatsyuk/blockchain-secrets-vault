package lt.satsyuk.blockchainsecretsvault.kms.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionKeyTest {
    
    @Test
    void testEncryptionKeyCreation() {
        byte[] keyMaterial = new byte[32];
        Instant now = Instant.now();
        
        EncryptionKey key = new EncryptionKey("key-id", keyMaterial, 0, KeyStatus.ACTIVE, now, null);
        
        assertEquals("key-id", key.keyId());
        assertArrayEquals(keyMaterial, key.keyMaterial());
        assertEquals(0, key.version());
        assertEquals(KeyStatus.ACTIVE, key.status());
        assertEquals(now, key.createdAt());
        assertNull(key.rotatedAt());
    }
    
    @Test
    void testEncryptionKeyBlankKeyId() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionKey("", new byte[32], 0, KeyStatus.ACTIVE, Instant.now(), null));
        assertThrows(IllegalArgumentException.class, () -> new EncryptionKey("   ", new byte[32], 0, KeyStatus.ACTIVE, Instant.now(), null));
    }
    
    @Test
    void testEncryptionKeyNullKeyId() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionKey(null, new byte[32], 0, KeyStatus.ACTIVE, Instant.now(), null));
    }
    
    @Test
    void testEncryptionKeyNullKeyMaterial() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionKey("key-id", null, 0, KeyStatus.ACTIVE, Instant.now(), null));
    }
    
    @Test
    void testEncryptionKeyEmptyKeyMaterial() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionKey("key-id", new byte[0], 0, KeyStatus.ACTIVE, Instant.now(), null));
    }
    
    @Test
    void testEncryptionKeyNegativeVersion() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionKey("key-id", new byte[32], -1, KeyStatus.ACTIVE, Instant.now(), null));
    }
    
    @Test
    void testEncryptionKeyNullStatus() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionKey("key-id", new byte[32], 0, null, Instant.now(), null));
    }
    
    @Test
    void testEncryptionKeyNullCreatedAt() {
        assertThrows(IllegalArgumentException.class,
            () -> new EncryptionKey("key-id", new byte[32], 0, KeyStatus.ACTIVE, null, null));
    }
    
    @Test
    void testEncryptionKeyWithRotatedAt() {
        Instant created = Instant.now().minusSeconds(3600);
        Instant rotated = Instant.now();
        
        EncryptionKey key = new EncryptionKey("key-id", new byte[32], 1, KeyStatus.ROTATED, created, rotated);
        
        assertEquals(created, key.createdAt());
        assertEquals(rotated, key.rotatedAt());
    }

    @Test
    void testEncryptionKeyEquals() {
        byte[] keyMaterial = new byte[32];
        Instant now = Instant.now();
        
        EncryptionKey key1 = new EncryptionKey("key-id", keyMaterial, 0, KeyStatus.ACTIVE, now, null);
        EncryptionKey key2 = new EncryptionKey("key-id", keyMaterial, 0, KeyStatus.ACTIVE, now, null);
        
        assertEquals(key1, key2);
    }

    @Test
    void testEncryptionKeyNotEquals() {
        byte[] keyMaterial = new byte[32];
        Instant now = Instant.now();
        
        EncryptionKey key1 = new EncryptionKey("key-id", keyMaterial, 0, KeyStatus.ACTIVE, now, null);
        EncryptionKey key2 = new EncryptionKey("key-id", keyMaterial, 1, KeyStatus.ACTIVE, now, null);
        
        assertNotEquals(key1, key2);
    }

    @Test
    void testEncryptionKeyHashCode() {
        byte[] keyMaterial = new byte[32];
        Instant now = Instant.now();
        
        EncryptionKey key1 = new EncryptionKey("key-id", keyMaterial, 0, KeyStatus.ACTIVE, now, null);
        EncryptionKey key2 = new EncryptionKey("key-id", keyMaterial, 0, KeyStatus.ACTIVE, now, null);
        
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void testEncryptionKeyToString() {
        byte[] keyMaterial = new byte[32];
        Instant now = Instant.now();
        
        EncryptionKey key = new EncryptionKey("key-id", keyMaterial, 0, KeyStatus.ACTIVE, now, null);
        String str = key.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("key-id"));
        assertTrue(str.contains("ACTIVE"));
    }
}
