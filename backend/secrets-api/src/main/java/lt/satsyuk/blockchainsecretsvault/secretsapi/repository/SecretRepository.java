package lt.satsyuk.blockchainsecretsvault.secretsapi.repository;

import lt.satsyuk.blockchainsecretsvault.secretsapi.model.SecretRecord;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SecretRepository {

    SecretRecord save(SecretRecord secret);

    Optional<SecretRecord> saveIfNameAvailable(SecretRecord secret, Optional<UUID> existingId);

    Optional<SecretRecord> findById(UUID id);

    Optional<SecretRecord> findByName(String name);

    Collection<SecretRecord> findAll();

    boolean deleteById(UUID id);

    void deleteAll();
}

