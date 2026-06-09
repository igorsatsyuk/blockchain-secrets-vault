package lt.satsyuk.blockchainsecretsvault.secretsapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lt.satsyuk.blockchainsecretsvault.secretsapi.model.SecretRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemorySecretRepositoryTest {
    private static final String ALPHA = "alpha";
    private static final Instant TEN_AM = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant ELEVEN_AM = Instant.parse("2026-06-01T11:00:00Z");
    private static final Instant TEN_O_ONE_AM = Instant.parse("2026-06-01T10:01:00Z");
    private static final Instant TEN_O_TWO_AM = Instant.parse("2026-06-01T10:02:00Z");

    private final InMemorySecretRepository repository = new InMemorySecretRepository();

    @Test
    void savesFindsAndDeletesSecret() {
        SecretRecord secret = secret(ALPHA, TEN_AM);

        repository.save(secret);

        assertThat(repository.findById(secret.id())).contains(secret);
        assertThat(repository.findByName("ALPHA")).contains(secret);
        assertThat(repository.deleteById(secret.id())).isTrue();
        assertThat(repository.findById(secret.id())).isEmpty();
        assertThat(repository.deleteById(secret.id())).isFalse();
    }

    @Test
    void listsSecretsByCreationTimeAndCanClearStore() {
        SecretRecord later = secret("later", ELEVEN_AM);
        SecretRecord earlier = secret("earlier", TEN_AM);
        repository.save(later);
        repository.save(earlier);

        assertThat(repository.findAll()).extracting(SecretRecord::name).containsExactly("earlier", "later");

        repository.deleteAll();

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void listsSecretsWithStableIdTieBreakerWhenCreationTimeMatches() {
        SecretRecord second = secret(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "second",
                TEN_AM
        );
        SecretRecord first = secret(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "first",
                TEN_AM
        );
        repository.save(second);
        repository.save(first);

        assertThat(repository.findAll()).extracting(SecretRecord::name).containsExactly("first", "second");
    }

    @Test
    void savesOnlyWhenNameIsAvailable() {
        SecretRecord existing = secret(ALPHA, TEN_AM);
        repository.save(existing);

        SecretRecord duplicate = secret("ALPHA", TEN_O_ONE_AM);
        assertThat(repository.saveIfNameAvailable(duplicate, Optional.empty())).isEmpty();
        assertThat(repository.findAll()).hasSize(1);

        SecretRecord renamedSameId = new SecretRecord(
                existing.id(),
                ALPHA,
                existing.description(),
                existing.encryptedPayload(),
                existing.encryptionKeyId(),
                existing.encryptionKeyVersion(),
                existing.tags(),
                existing.createdAt(),
                TEN_O_TWO_AM
        );
        assertThat(repository.saveIfNameAvailable(renamedSameId, Optional.of(existing.id()))).contains(renamedSameId);
    }

    private static SecretRecord secret(String name, Instant createdAt) {
        return secret(UUID.randomUUID(), name, createdAt);
    }

    private static SecretRecord secret(UUID id, String name, Instant createdAt) {
        return new SecretRecord(
                id,
                name,
                "description",
                "encrypted_payload",
                "default-secret-key",
                0,
                Set.of("prod"),
                createdAt,
                createdAt
        );
    }
}


