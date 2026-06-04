package lt.satsyuk.blockchainsecretsvault.kms.model;

import java.time.Instant;

public record EncryptionKey(
    String keyId,
    byte[] keyMaterial,
    int version,
    KeyStatus status,
    Instant createdAt,
    Instant rotatedAt
) {
    public EncryptionKey {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId cannot be blank");
        }
        if (keyMaterial == null || keyMaterial.length == 0) {
            throw new IllegalArgumentException("keyMaterial cannot be empty");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt cannot be null");
        }
    }
}
