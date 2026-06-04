package lt.satsyuk.blockchainsecretsvault.kms.service;

import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptedData;
import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptionKey;
import lt.satsyuk.blockchainsecretsvault.kms.model.KeyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AesGcmKmsService implements KmsService {
    private static final Logger logger = LoggerFactory.getLogger(AesGcmKmsService.class);
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int IV_SIZE = 12;
    private static final int AUTH_TAG_SIZE = 128;
    private static final String KEY_VERSION_FORMAT = "%s#%d";
    
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, EncryptionKey> keyStore = new ConcurrentHashMap<>();
    private final Map<String, Integer> keyVersions = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    
    @Override
    public EncryptionKey generateKey(String keyId) {
        synchronized (lifecycleLock) {
            if (keyVersions.containsKey(keyId)) {
                String message = String.format("Key already exists: %s", keyId);
                throw new IllegalArgumentException(message);
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
            } catch (NoSuchAlgorithmException e) {
                logger.error("Failed to generate key for keyId: {}", keyId, e);
                throw new EncryptionFailedException("Failed to generate key", e);
            }
        }
    }
    
    @Override
    public EncryptionKey rotateKey(String keyId) {
        synchronized (lifecycleLock) {
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
            } catch (NoSuchAlgorithmException e) {
                logger.error("Failed to rotate key for keyId: {}", keyId, e);
                throw new EncryptionFailedException("Failed to rotate key", e);
            }
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
            
            byte[] authTag = extractAuthTag(ciphertext);
            byte[] actualCiphertext = removeSuffixAuthTag(ciphertext, authTag.length);
            
            return new EncryptedData(
                actualCiphertext,
                nonce,
                authTag,
                keyId,
                key.version()
            );
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            logger.error("Encryption failed for keyId: {}", keyId, e);
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
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            logger.error("Decryption failed for keyId: {}, version: {}", encryptedData.keyId(), encryptedData.keyVersion(), e);
            throw new DecryptionFailedException("Decryption failed", e);
        }
    }
    
    @Override
    public EncryptionKey getKey(String keyId, int version) {
        EncryptionKey key = keyStore.get(keyVersionKey(keyId, version));
        if (key == null) {
            String message = String.format("Key not found: %s version: %d", keyId, version);
            throw new KeyNotFoundException(message);
        }
        return key;
    }
    
    @Override
    public EncryptionKey getActiveKey(String keyId) {
        Integer version = keyVersions.get(keyId);
        if (version == null) {
            String message = String.format("Key not found: %s", keyId);
            throw new KeyNotFoundException(message);
        }
        EncryptionKey key = getKey(keyId, version);
        if (key.status() != KeyStatus.ACTIVE) {
            String message = String.format("Key is not active: %s status: %s", keyId, key.status());
            throw new IllegalStateException(message);
        }
        return key;
    }
    
    @Override
    public EncryptionKey compromiseKey(String keyId) {
        synchronized (lifecycleLock) {
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
    }

    @Override
    public EncryptionKey retireKey(String keyId) {
        synchronized (lifecycleLock) {
            EncryptionKey activeKey = getActiveKey(keyId);

            EncryptionKey retiredKey = new EncryptionKey(
                activeKey.keyId(),
                activeKey.keyMaterial(),
                activeKey.version(),
                KeyStatus.RETIRED,
                activeKey.createdAt(),
                Instant.now()
            );

            keyStore.put(keyVersionKey(keyId, activeKey.version()), retiredKey);
            return retiredKey;
        }
    }
    
    private String keyVersionKey(String keyId, int version) {
        return String.format(KEY_VERSION_FORMAT, keyId, version);
    }
    
    private byte[] extractAuthTag(byte[] ciphertext) {
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
