package lt.satsyuk.blockchainsecretsvault.kms.service;

import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptedData;
import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptionKey;

public interface KmsService {
    
    /**
     * Generate a new encryption key
     */
    EncryptionKey generateKey(String keyId);
    
    /**
     * Rotate an existing key - marks old as ROTATED and creates new ACTIVE
     */
    EncryptionKey rotateKey(String keyId);
    
    /**
     * Encrypt data using the specified key
     */
    EncryptedData encrypt(String keyId, byte[] plaintext);
    
    /**
     * Decrypt data using the metadata in EncryptedData
     */
    byte[] decrypt(EncryptedData encryptedData);
    
    /**
     * Get key by ID and version
     */
    EncryptionKey getKey(String keyId, int version);
    
    /**
     * Get active key by ID
     */
    EncryptionKey getActiveKey(String keyId);
    
    /**
     * Mark key as compromised
     */
    EncryptionKey compromiseKey(String keyId);

    /**
     * Mark key as retired and block new encryptions with this keyId
     */
    EncryptionKey retireKey(String keyId);
}
