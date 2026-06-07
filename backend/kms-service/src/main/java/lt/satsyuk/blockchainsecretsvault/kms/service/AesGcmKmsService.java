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
import java.util.Arrays;
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
        validateKeyId(keyId);
        synchronized (lifecycleLock) {
            if (keyVersions.containsKey(keyId)) {
                String message = String.format("Key already exists: %s", keyId);
                throw new IllegalArgumentException(message);
            }
            
            try {
                byte[] keyMaterial = generateRawKeyMaterial();
                
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
        validateKeyId(keyId);
        synchronized (lifecycleLock) {
            EncryptionKey activeKey = getActiveKey(keyId);
            
            try {
                int newVersion = activeKey.version() + 1;
                byte[] keyMaterial = generateRawKeyMaterial();
                
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
                
                keyStore.put(keyVersionKey(keyId, newVersion), newActiveKey);
                keyVersions.put(keyId, newVersion);
                keyStore.put(keyVersionKey(keyId, activeKey.version()), rotatedKey);
                
                return newActiveKey;
            } catch (NoSuchAlgorithmException e) {
                logger.error("Failed to rotate key for keyId: {}", keyId, e);
                throw new EncryptionFailedException("Failed to rotate key", e);
            }
        }
    }
    
    @Override
    public EncryptedData encrypt(String keyId, byte[] plaintext) {
        validateKeyId(keyId);
        if (plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("Plaintext cannot be empty");
        }
        
        EncryptionKey keyEncryptionKey = getActiveKey(keyId);
        
        try {
            byte[] dataEncryptionKey = generateRawKeyMaterial();
            try {
                EncryptionResult payload = encryptWithKey(dataEncryptionKey, plaintext);
                EncryptionResult wrappedDataKey = encryptWithKey(keyEncryptionKey.keyMaterial(), dataEncryptionKey);

                return new EncryptedData(
                    payload.ciphertext(),
                    payload.nonce(),
                    payload.authTag(),
                    keyId,
                    keyEncryptionKey.version(),
                    wrappedDataKey.ciphertext(),
                    wrappedDataKey.nonce(),
                    wrappedDataKey.authTag()
                );
            } finally {
                Arrays.fill(dataEncryptionKey, (byte) 0);
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            logger.error("Encryption failed for keyId: {}", keyId, e);
            throw new EncryptionFailedException("Encryption failed", e);
        }
    }
    
    @Override
    public byte[] decrypt(EncryptedData encryptedData) {
        if (encryptedData == null) {
            throw new IllegalArgumentException("Encrypted data cannot be null");
        }
        EncryptionKey key = getKey(encryptedData.keyId(), encryptedData.keyVersion());
        
        try {
            if (!encryptedData.envelopeEncrypted()) {
                return decryptWithKey(key.keyMaterial(), encryptedData.ciphertext(), encryptedData.nonce(), encryptedData.authTag());
            }

            byte[] dataEncryptionKey = decryptWithKey(
                key.keyMaterial(),
                encryptedData.encryptedDataKey(),
                encryptedData.dataKeyNonce(),
                encryptedData.dataKeyAuthTag()
            );
            try {
                return decryptWithKey(dataEncryptionKey, encryptedData.ciphertext(), encryptedData.nonce(), encryptedData.authTag());
            } finally {
                Arrays.fill(dataEncryptionKey, (byte) 0);
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            logger.error("Decryption failed for keyId: {}, version: {}", encryptedData.keyId(), encryptedData.keyVersion(), e);
            throw new DecryptionFailedException("Decryption failed", e);
        }
    }

    @Override
    public EncryptedData rewrapDataKey(EncryptedData encryptedData) {
        if (encryptedData == null) {
            throw new IllegalArgumentException("Encrypted data cannot be null");
        }
        if (!encryptedData.envelopeEncrypted()) {
            throw new IllegalArgumentException("Encrypted data does not contain an envelope data key");
        }

        EncryptionKey previousKeyEncryptionKey = getKey(encryptedData.keyId(), encryptedData.keyVersion());
        EncryptionKey activeKeyEncryptionKey = getActiveKey(encryptedData.keyId());

        try {
            byte[] dataEncryptionKey = decryptWithKey(
                previousKeyEncryptionKey.keyMaterial(),
                encryptedData.encryptedDataKey(),
                encryptedData.dataKeyNonce(),
                encryptedData.dataKeyAuthTag()
            );
            try {
                EncryptionResult wrappedDataKey = encryptWithKey(activeKeyEncryptionKey.keyMaterial(), dataEncryptionKey);
                return new EncryptedData(
                    encryptedData.ciphertext(),
                    encryptedData.nonce(),
                    encryptedData.authTag(),
                    activeKeyEncryptionKey.keyId(),
                    activeKeyEncryptionKey.version(),
                    wrappedDataKey.ciphertext(),
                    wrappedDataKey.nonce(),
                    wrappedDataKey.authTag()
                );
            } finally {
                Arrays.fill(dataEncryptionKey, (byte) 0);
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            logger.error("Data key re-wrap failed for keyId: {}, version: {}", encryptedData.keyId(), encryptedData.keyVersion(), e);
            throw new EncryptionFailedException("Data key re-wrap failed", e);
        }
    }
    
    @Override
    public EncryptionKey getKey(String keyId, int version) {
        validateKeyId(keyId);
        EncryptionKey key = keyStore.get(keyVersionKey(keyId, version));
        if (key == null) {
            String message = String.format("Key not found: %s version: %d", keyId, version);
            throw new KeyNotFoundException(message);
        }
        return key;
    }
    
    @Override
    public EncryptionKey getActiveKey(String keyId) {
        validateKeyId(keyId);
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
        validateKeyId(keyId);
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
        validateKeyId(keyId);
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

    private void validateKeyId(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId cannot be blank");
        }
    }

    private byte[] generateRawKeyMaterial() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE, secureRandom);
        return keyGen.generateKey().getEncoded();
    }

    private EncryptionResult encryptWithKey(byte[] keyMaterial, byte[] plaintext)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] nonce = new byte[IV_SIZE];
        secureRandom.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(AUTH_TAG_SIZE, nonce);
        SecretKeySpec keySpec = new SecretKeySpec(keyMaterial, 0, keyMaterial.length, ALGORITHM);

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
        byte[] ciphertextWithTag = cipher.doFinal(plaintext);

        byte[] authTag = extractAuthTag(ciphertextWithTag);
        byte[] ciphertext = removeSuffixAuthTag(ciphertextWithTag, authTag.length);
        return new EncryptionResult(ciphertext, nonce, authTag);
    }

    private byte[] decryptWithKey(byte[] keyMaterial, byte[] ciphertext, byte[] nonce, byte[] authTag)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(AUTH_TAG_SIZE, nonce);
        SecretKeySpec keySpec = new SecretKeySpec(keyMaterial, 0, keyMaterial.length, ALGORITHM);

        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

        byte[] ciphertextWithTag = new byte[ciphertext.length + authTag.length];
        System.arraycopy(ciphertext, 0, ciphertextWithTag, 0, ciphertext.length);
        System.arraycopy(authTag, 0, ciphertextWithTag, ciphertext.length, authTag.length);

        return cipher.doFinal(ciphertextWithTag);
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

    private static final class EncryptionResult {
        private final byte[] ciphertext;
        private final byte[] nonce;
        private final byte[] authTag;

        private EncryptionResult(byte[] ciphertext, byte[] nonce, byte[] authTag) {
            this.ciphertext = ciphertext;
            this.nonce = nonce;
            this.authTag = authTag;
        }

        private byte[] ciphertext() {
            return ciphertext;
        }

        private byte[] nonce() {
            return nonce;
        }

        private byte[] authTag() {
            return authTag;
        }
    }
}
