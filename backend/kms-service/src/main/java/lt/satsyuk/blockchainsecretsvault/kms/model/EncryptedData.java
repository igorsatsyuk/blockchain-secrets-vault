package lt.satsyuk.blockchainsecretsvault.kms.model;

import java.util.Arrays;
import java.util.Objects;

public record EncryptedData(
    byte[] ciphertext,
    byte[] nonce,
    byte[] authTag,
    String keyId,
    int keyVersion,
    byte[] encryptedDataKey,
    byte[] dataKeyNonce,
    byte[] dataKeyAuthTag
) {
    public EncryptedData(byte[] ciphertext, byte[] nonce, byte[] authTag, String keyId, int keyVersion) {
        this(ciphertext, nonce, authTag, keyId, keyVersion, null, null, null);
    }

    public EncryptedData {
        if (ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("ciphertext cannot be empty");
        }
        if (nonce == null || nonce.length == 0) {
            throw new IllegalArgumentException("nonce cannot be empty");
        }
        if (authTag == null || authTag.length == 0) {
            throw new IllegalArgumentException("authTag cannot be empty");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId cannot be blank");
        }
        if (keyVersion < 0) {
            throw new IllegalArgumentException("keyVersion cannot be negative");
        }
        boolean hasEncryptedDataKey = encryptedDataKey != null || dataKeyNonce != null || dataKeyAuthTag != null;
        if (hasEncryptedDataKey) {
            if (encryptedDataKey == null || encryptedDataKey.length == 0) {
                throw new IllegalArgumentException("encryptedDataKey cannot be empty");
            }
            if (dataKeyNonce == null || dataKeyNonce.length == 0) {
                throw new IllegalArgumentException("dataKeyNonce cannot be empty");
            }
            if (dataKeyAuthTag == null || dataKeyAuthTag.length == 0) {
                throw new IllegalArgumentException("dataKeyAuthTag cannot be empty");
            }
        }

        ciphertext = ciphertext.clone();
        nonce = nonce.clone();
        authTag = authTag.clone();
        encryptedDataKey = encryptedDataKey == null ? null : encryptedDataKey.clone();
        dataKeyNonce = dataKeyNonce == null ? null : dataKeyNonce.clone();
        dataKeyAuthTag = dataKeyAuthTag == null ? null : dataKeyAuthTag.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    @Override
    public byte[] authTag() {
        return authTag.clone();
    }

    @Override
    public byte[] encryptedDataKey() {
        return encryptedDataKey == null ? null : encryptedDataKey.clone();
    }

    @Override
    public byte[] dataKeyNonce() {
        return dataKeyNonce == null ? null : dataKeyNonce.clone();
    }

    @Override
    public byte[] dataKeyAuthTag() {
        return dataKeyAuthTag == null ? null : dataKeyAuthTag.clone();
    }

    public boolean envelopeEncrypted() {
        return encryptedDataKey != null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EncryptedData(byte[] ct, byte[] n, byte[] at, String id, int kv, byte[] edk, byte[] dkn, byte[] dkat) &&
               kv == keyVersion &&
               Arrays.equals(ciphertext, ct) &&
               Arrays.equals(nonce, n) &&
               Arrays.equals(authTag, at) &&
               Arrays.equals(encryptedDataKey, edk) &&
               Arrays.equals(dataKeyNonce, dkn) &&
               Arrays.equals(dataKeyAuthTag, dkat) &&
               Objects.equals(keyId, id);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(keyId, keyVersion);
        result = 31 * result + Arrays.hashCode(ciphertext);
        result = 31 * result + Arrays.hashCode(nonce);
        result = 31 * result + Arrays.hashCode(authTag);
        result = 31 * result + Arrays.hashCode(encryptedDataKey);
        result = 31 * result + Arrays.hashCode(dataKeyNonce);
        result = 31 * result + Arrays.hashCode(dataKeyAuthTag);
        return result;
    }

    @Override
    public String toString() {
        return "EncryptedData{" +
                "ciphertextLength=" + ciphertext.length +
                ", nonceLength=" + nonce.length +
                ", authTagLength=" + authTag.length +
                ", keyId='" + keyId + '\'' +
                ", keyVersion=" + keyVersion +
                ", envelopeEncrypted=" + envelopeEncrypted() +
                ", encryptedDataKeyLength=" + (encryptedDataKey == null ? 0 : encryptedDataKey.length) +
                '}';
    }
}
