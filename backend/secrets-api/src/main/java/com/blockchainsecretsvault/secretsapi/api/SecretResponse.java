package com.blockchainsecretsvault.secretsapi.api;

import com.blockchainsecretsvault.secretsapi.model.SecretRecord;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SecretResponse(
        UUID id,
        String name,
        String description,
        Set<String> tags,
        Instant createdAt,
        Instant updatedAt
) {
    public static SecretResponse from(SecretRecord secret) {
        return new SecretResponse(
                secret.id(),
                secret.name(),
                secret.description(),
                secret.tags(),
                secret.createdAt(),
                secret.updatedAt()
        );
    }
}
