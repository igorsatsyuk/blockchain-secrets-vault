package lt.satsyuk.blockchainsecretsvault.kms.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        return o instanceof EncryptionKey that &&
                version == that.version &&
                Objects.equals(keyId, that.keyId) &&
                Arrays.equals(keyMaterial, that.keyMaterial) &&
                status == that.status &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(rotatedAt, that.rotatedAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(keyId, version, status, createdAt, rotatedAt);
        result = 31 * result + Arrays.hashCode(keyMaterial);
        return result;
    }

    @Override
    public String toString() {
        return "EncryptionKey{" +
                "keyId='" + keyId + '\'' +
                ", keyMaterial=" + Arrays.toString(keyMaterial) +
                ", version=" + version +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", rotatedAt=" + rotatedAt +
                '}';
    }
}

