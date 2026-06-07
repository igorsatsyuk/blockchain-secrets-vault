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
        validateRequiredSegment(ciphertext, "ciphertext");
        validateRequiredSegment(nonce, "nonce");
        validateRequiredSegment(authTag, "authTag");
        validateKeyMetadata(keyId, keyVersion);
        validateEnvelopeMetadata(encryptedDataKey, dataKeyNonce, dataKeyAuthTag);

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

    private static void validateKeyMetadata(String keyId, int keyVersion) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId cannot be blank");
        }
        if (keyVersion < 0) {
            throw new IllegalArgumentException("keyVersion cannot be negative");
        }
    }

    private static void validateEnvelopeMetadata(byte[] encryptedDataKey, byte[] dataKeyNonce, byte[] dataKeyAuthTag) {
        if (!hasEnvelopeMetadata(encryptedDataKey, dataKeyNonce, dataKeyAuthTag)) {
            return;
        }
        validateRequiredSegment(encryptedDataKey, "encryptedDataKey");
        validateRequiredSegment(dataKeyNonce, "dataKeyNonce");
        validateRequiredSegment(dataKeyAuthTag, "dataKeyAuthTag");
    }

    private static boolean hasEnvelopeMetadata(byte[] encryptedDataKey, byte[] dataKeyNonce, byte[] dataKeyAuthTag) {
        return encryptedDataKey != null || dataKeyNonce != null || dataKeyAuthTag != null;
    }

    private static void validateRequiredSegment(byte[] segment, String name) {
        if (segment == null || segment.length == 0) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
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
