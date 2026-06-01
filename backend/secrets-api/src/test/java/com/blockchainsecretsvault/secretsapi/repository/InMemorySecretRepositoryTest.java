package com.blockchainsecretsvault.secretsapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.blockchainsecretsvault.secretsapi.model.SecretRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemorySecretRepositoryTest {

    private final InMemorySecretRepository repository = new InMemorySecretRepository();

    @Test
    void savesFindsAndDeletesSecret() {
        SecretRecord secret = secret("alpha", Instant.parse("2026-06-01T10:00:00Z"));

        repository.save(secret);

        assertThat(repository.findById(secret.id())).contains(secret);
        assertThat(repository.findByName("ALPHA")).contains(secret);
        assertThat(repository.deleteById(secret.id())).isTrue();
        assertThat(repository.findById(secret.id())).isEmpty();
        assertThat(repository.deleteById(secret.id())).isFalse();
    }

    @Test
    void listsSecretsByCreationTimeAndCanClearStore() {
        SecretRecord later = secret("later", Instant.parse("2026-06-01T11:00:00Z"));
        SecretRecord earlier = secret("earlier", Instant.parse("2026-06-01T10:00:00Z"));
        repository.save(later);
        repository.save(earlier);

        assertThat(repository.findAll()).extracting(SecretRecord::name).containsExactly("earlier", "later");

        repository.deleteAll();

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void savesOnlyWhenNameIsAvailable() {
        SecretRecord existing = secret("alpha", Instant.parse("2026-06-01T10:00:00Z"));
        repository.save(existing);

        SecretRecord duplicate = secret("ALPHA", Instant.parse("2026-06-01T10:01:00Z"));
        assertThat(repository.saveIfNameAvailable(duplicate, Optional.empty())).isEmpty();
        assertThat(repository.findAll()).hasSize(1);

        SecretRecord renamedSameId = new SecretRecord(
                existing.id(),
                "alpha",
                existing.description(),
                existing.payload(),
                existing.tags(),
                existing.createdAt(),
                Instant.parse("2026-06-01T10:02:00Z")
        );
        assertThat(repository.saveIfNameAvailable(renamedSameId, Optional.of(existing.id()))).contains(renamedSameId);
    }

    private static SecretRecord secret(String name, Instant createdAt) {
        return new SecretRecord(
                UUID.randomUUID(),
                name,
                "description",
                "payload",
                Set.of("prod"),
                createdAt,
                createdAt
        );
    }
}
