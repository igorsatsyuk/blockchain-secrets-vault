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

        keyMaterial = keyMaterial.clone();
    }

    @Override
    public byte[] keyMaterial() {
        return keyMaterial.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EncryptionKey(String id, byte[] km, int v, KeyStatus s, Instant c, Instant r) &&
                v == version &&
                Objects.equals(keyId, id) &&
                Arrays.equals(keyMaterial, km) &&
                s == status &&
                Objects.equals(createdAt, c) &&
                Objects.equals(rotatedAt, r);
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
                ", keyMaterialLength=" + keyMaterial.length +
                ", version=" + version +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", rotatedAt=" + rotatedAt +
                '}';
    }
}
