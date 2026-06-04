package lt.satsyuk.blockchainsecretsvault.secretsapi.repository;

import lt.satsyuk.blockchainsecretsvault.secretsapi.model.SecretRecord;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemorySecretRepository implements SecretRepository {

    private final ConcurrentMap<UUID, SecretRecord> secrets = new ConcurrentHashMap<>();

    @Override
    public SecretRecord save(SecretRecord secret) {
        secrets.put(secret.id(), secret);
        return secret;
    }

    @Override
    public synchronized Optional<SecretRecord> saveIfNameAvailable(
            SecretRecord secret,
            Optional<UUID> existingId
    ) {
        Optional<SecretRecord> duplicate = findByName(secret.name())
                .filter(found -> existingId.isEmpty() || !found.id().equals(existingId.get()));
        if (duplicate.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(save(secret));
    }

    @Override
    public Optional<SecretRecord> findById(UUID id) {
        return Optional.ofNullable(secrets.get(id));
    }

    @Override
    public Optional<SecretRecord> findByName(String name) {
        return secrets.values().stream()
                .filter(secret -> secret.name().equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public Collection<SecretRecord> findAll() {
        return secrets.values().stream()
                .sorted(Comparator.comparing(SecretRecord::createdAt)
                        .thenComparing(SecretRecord::id))
                .toList();
    }

    @Override
    public boolean deleteById(UUID id) {
        return secrets.remove(id) != null;
    }

    @Override
    public void deleteAll() {
        secrets.clear();
    }
}

