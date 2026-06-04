package lt.satsyuk.blockchainsecretsvault.kms.model;

import java.util.Arrays;
import java.util.Objects;

public record EncryptedData(
    byte[] ciphertext,
    byte[] nonce,
    byte[] authTag,
    String keyId,
    int keyVersion
) {
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
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EncryptedData(byte[] ct, byte[] n, byte[] at, String id, int kv) &&
               kv == keyVersion &&
               Arrays.equals(ciphertext, ct) &&
               Arrays.equals(nonce, n) &&
               Arrays.equals(authTag, at) &&
               Objects.equals(keyId, id);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(keyId, keyVersion);
        result = 31 * result + Arrays.hashCode(ciphertext);
        result = 31 * result + Arrays.hashCode(nonce);
        result = 31 * result + Arrays.hashCode(authTag);
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
                '}';
    }
}
