package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptedData;
import lt.satsyuk.blockchainsecretsvault.kms.service.KmsService;
import lt.satsyuk.blockchainsecretsvault.secretsapi.api.CreateSecretRequest;
import lt.satsyuk.blockchainsecretsvault.secretsapi.api.UpdateSecretRequest;
import lt.satsyuk.blockchainsecretsvault.secretsapi.model.SecretRecord;
import lt.satsyuk.blockchainsecretsvault.secretsapi.repository.SecretRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SecretsService {

    private final SecretRepository secretRepository;
    private final KmsService kmsService;
    private final Clock clock;
    private static final String DEFAULT_KEY_ID = "default-secret-key";

    public SecretsService(SecretRepository secretRepository, KmsService kmsService, Clock clock) {
        this.secretRepository = secretRepository;
        this.kmsService = kmsService;
        this.clock = clock;
        initializeDefaultKey();
    }

    private void initializeDefaultKey() {
        try {
            kmsService.getActiveKey(DEFAULT_KEY_ID);
        } catch (Exception e) {
            kmsService.generateKey(DEFAULT_KEY_ID);
        }
    }

    public Mono<SecretRecord> create(CreateSecretRequest request) {
        return Mono.fromSupplier(() -> {
            String normalizedName = normalizeName(request.name());
            Instant now = Instant.now(clock);
            
            EncryptedData encrypted = kmsService.encrypt(DEFAULT_KEY_ID, request.payload().getBytes());
            
            SecretRecord secret = new SecretRecord(
                    UUID.randomUUID(),
                    normalizedName,
                    normalizeNullable(request.description()),
                    new String(encrypted.ciphertext()),
                    DEFAULT_KEY_ID,
                    encrypted.keyVersion(),
                    normalizeTags(request.tags()),
                    now,
                    now
            );
            return secretRepository.saveIfNameAvailable(secret, Optional.empty())
                    .orElseThrow(() -> new DuplicateSecretNameException(normalizedName));
        });
    }

    public Flux<SecretRecord> list() {
        return Flux.defer(() -> Flux.fromIterable(secretRepository.findAll()));
    }

    public Mono<SecretRecord> get(UUID id) {
        return Mono.fromSupplier(() -> secretRepository.findById(id)
                .orElseThrow(() -> new SecretNotFoundException(id)));
    }

    public Mono<SecretRecord> update(UUID id, UpdateSecretRequest request) {
        return Mono.fromSupplier(() -> {
            if (request.isEmpty()) {
                throw new EmptySecretUpdateException();
            }

            SecretRecord existing = secretRepository.findById(id)
                    .orElseThrow(() -> new SecretNotFoundException(id));

            String nextName = chooseString(request.name(), existing.name());
            
            String nextEncryptedPayload = existing.encryptedPayload();
            int nextKeyVersion = existing.encryptionKeyVersion();
            
            if (request.payload() != null) {
                EncryptedData encrypted = kmsService.encrypt(DEFAULT_KEY_ID, request.payload().getBytes());
                nextEncryptedPayload = new String(encrypted.ciphertext());
                nextKeyVersion = encrypted.keyVersion();
            }
            
            SecretRecord updated = new SecretRecord(
                    existing.id(),
                    nextName,
                    request.description() == null ? existing.description() : normalizeNullable(request.description()),
                    nextEncryptedPayload,
                    DEFAULT_KEY_ID,
                    nextKeyVersion,
                    request.tags() == null ? existing.tags() : normalizeTags(request.tags()),
                    existing.createdAt(),
                    Instant.now(clock)
            );
            return secretRepository.saveIfNameAvailable(updated, Optional.of(id))
                    .orElseThrow(() -> new DuplicateSecretNameException(nextName));
        });
    }

    public Mono<Void> delete(UUID id) {
        return Mono.fromRunnable(() -> {
            if (!secretRepository.deleteById(id)) {
                throw new SecretNotFoundException(id);
            }
        });
    }

    private static String chooseString(String candidate, String fallback) {
        if (candidate == null) {
            return fallback;
        }
        return normalizeName(candidate, fallback);
    }

    private static String normalizeName(String value) {
        return normalizeName(value, null);
    }

    private static String normalizeName(String value, String fallback) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Set<String> normalizeTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                normalized.add(tag.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(normalized);
    }
}

