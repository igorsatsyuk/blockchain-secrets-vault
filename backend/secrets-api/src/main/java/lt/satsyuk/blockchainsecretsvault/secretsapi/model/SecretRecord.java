package lt.satsyuk.blockchainsecretsvault.secretsapi.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SecretRecord(
        UUID id,
        String name,
        String description,
        String encryptedPayload,
        String encryptionKeyId,
        int encryptionKeyVersion,
        Set<String> tags,
        Instant createdAt,
        Instant updatedAt
) {
}


