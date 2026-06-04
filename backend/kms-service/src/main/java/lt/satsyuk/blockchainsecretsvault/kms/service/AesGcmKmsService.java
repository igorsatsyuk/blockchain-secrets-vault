package lt.satsyuk.blockchainsecretsvault.kms.service;

import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptedData;
import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptionKey;
import lt.satsyuk.blockchainsecretsvault.kms.model.KeyStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class AesGcmKmsService implements KmsService {
    
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int IV_SIZE = 12;
    private static final int AUTH_TAG_SIZE = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, EncryptionKey> keyStore = new HashMap<>();
    private final Map<String, Integer> keyVersions = new HashMap<>();
    
    @Override
    public EncryptionKey generateKey(String keyId) {
        if (keyVersions.containsKey(keyId)) {
            throw new IllegalArgumentException("Key already exists: " + keyId);
        }
        
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE, secureRandom);
            byte[] keyMaterial = keyGen.generateKey().getEncoded();
            
            EncryptionKey key = new EncryptionKey(
                keyId,
                keyMaterial,
                0,
                KeyStatus.ACTIVE,
                Instant.now(),
                null
            );
            
            keyStore.put(keyVersionKey(keyId, 0), key);
            keyVersions.put(keyId, 0);
            
            return key;
        } catch (Exception e) {
            throw new EncryptionFailedException("Failed to generate key", e);
        }
    }
    
    @Override
    public EncryptionKey rotateKey(String keyId) {
        EncryptionKey activeKey = getActiveKey(keyId);
        
        try {
            int newVersion = activeKey.version() + 1;
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE, secureRandom);
            byte[] keyMaterial = keyGen.generateKey().getEncoded();
            
            EncryptionKey rotatedKey = new EncryptionKey(
                activeKey.keyId(),
                activeKey.keyMaterial(),
                activeKey.version(),
                KeyStatus.ROTATED,
                activeKey.createdAt(),
                Instant.now()
            );
            
            EncryptionKey newActiveKey = new EncryptionKey(
                keyId,
                keyMaterial,
                newVersion,
                KeyStatus.ACTIVE,
                Instant.now(),
                null
            );
            
            keyStore.put(keyVersionKey(keyId, activeKey.version()), rotatedKey);
            keyStore.put(keyVersionKey(keyId, newVersion), newActiveKey);
            keyVersions.put(keyId, newVersion);
            
            return newActiveKey;
        } catch (Exception e) {
            throw new EncryptionFailedException("Failed to rotate key", e);
        }
    }
    
    @Override
    public EncryptedData encrypt(String keyId, byte[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("Plaintext cannot be empty");
        }
        
        EncryptionKey key = getActiveKey(keyId);
        
        try {
            byte[] nonce = new byte[IV_SIZE];
            secureRandom.nextBytes(nonce);
            
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(AUTH_TAG_SIZE, nonce);
            SecretKeySpec keySpec = new SecretKeySpec(key.keyMaterial(), 0, key.keyMaterial().length, ALGORITHM);
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
            byte[] ciphertext = cipher.doFinal(plaintext);
            
            byte[] authTag = extractAuthTag(cipher, ciphertext);
            byte[] actualCiphertext = removeSuffixAuthTag(ciphertext, authTag.length);
            
            return new EncryptedData(
                actualCiphertext,
                nonce,
                authTag,
                keyId,
                key.version()
            );
        } catch (Exception e) {
            throw new EncryptionFailedException("Encryption failed", e);
        }
    }
    
    @Override
    public byte[] decrypt(EncryptedData encryptedData) {
        EncryptionKey key = getKey(encryptedData.keyId(), encryptedData.keyVersion());
        
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(AUTH_TAG_SIZE, encryptedData.nonce());
            SecretKeySpec keySpec = new SecretKeySpec(key.keyMaterial(), 0, key.keyMaterial().length, ALGORITHM);
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            
            byte[] ciphertextWithTag = new byte[encryptedData.ciphertext().length + encryptedData.authTag().length];
            System.arraycopy(encryptedData.ciphertext(), 0, ciphertextWithTag, 0, encryptedData.ciphertext().length);
            System.arraycopy(encryptedData.authTag(), 0, ciphertextWithTag, encryptedData.ciphertext().length, encryptedData.authTag().length);
            
            return cipher.doFinal(ciphertextWithTag);
        } catch (Exception e) {
            throw new DecryptionFailedException("Decryption failed", e);
        }
    }
    
    @Override
    public EncryptionKey getKey(String keyId, int version) {
        EncryptionKey key = keyStore.get(keyVersionKey(keyId, version));
        if (key == null) {
            throw new KeyNotFoundException("Key not found: " + keyId + " version: " + version);
        }
        return key;
    }
    
    @Override
    public EncryptionKey getActiveKey(String keyId) {
        Integer version = keyVersions.get(keyId);
        if (version == null) {
            throw new KeyNotFoundException("Key not found: " + keyId);
        }
        return getKey(keyId, version);
    }
    
    @Override
    public EncryptionKey compromiseKey(String keyId) {
        EncryptionKey activeKey = getActiveKey(keyId);
        
        EncryptionKey compromisedKey = new EncryptionKey(
            activeKey.keyId(),
            activeKey.keyMaterial(),
            activeKey.version(),
            KeyStatus.COMPROMISED,
            activeKey.createdAt(),
            Instant.now()
        );
        
        keyStore.put(keyVersionKey(keyId, activeKey.version()), compromisedKey);
        return compromisedKey;
    }
    
    private String keyVersionKey(String keyId, int version) {
        return keyId + "#" + version;
    }
    
    private byte[] extractAuthTag(Cipher cipher, byte[] ciphertext) {
        byte[] authTag = new byte[AUTH_TAG_SIZE / 8];
        System.arraycopy(ciphertext, ciphertext.length - authTag.length, authTag, 0, authTag.length);
        return authTag;
    }
    
    private byte[] removeSuffixAuthTag(byte[] ciphertext, int authTagLength) {
        byte[] result = new byte[ciphertext.length - authTagLength];
        System.arraycopy(ciphertext, 0, result, 0, result.length);
        return result;
    }
}
