package com.blockchainsecretsvault.secretsapi.service;

import com.blockchainsecretsvault.secretsapi.api.CreateSecretRequest;
import com.blockchainsecretsvault.secretsapi.api.UpdateSecretRequest;
import com.blockchainsecretsvault.secretsapi.model.SecretRecord;
import com.blockchainsecretsvault.secretsapi.repository.SecretRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SecretsService {

    private final SecretRepository secretRepository;
    private final Clock clock;

    public SecretsService(SecretRepository secretRepository, Clock clock) {
        this.secretRepository = secretRepository;
        this.clock = clock;
    }

    public Mono<SecretRecord> create(CreateSecretRequest request) {
        return Mono.fromSupplier(() -> {
            secretRepository.findByName(request.name())
                    .ifPresent(existing -> {
                        throw new DuplicateSecretNameException(request.name());
                    });

            Instant now = Instant.now(clock);
            SecretRecord secret = new SecretRecord(
                    UUID.randomUUID(),
                    request.name().trim(),
                    normalizeNullable(request.description()),
                    request.payload(),
                    normalizeTags(request.tags()),
                    now,
                    now
            );
            return secretRepository.save(secret);
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
            secretRepository.findByName(nextName)
                    .filter(found -> !found.id().equals(id))
                    .ifPresent(found -> {
                        throw new DuplicateSecretNameException(nextName);
                    });

            SecretRecord updated = new SecretRecord(
                    existing.id(),
                    nextName,
                    request.description() == null ? existing.description() : normalizeNullable(request.description()),
                    request.payload() == null ? existing.payload() : request.payload(),
                    request.tags() == null ? existing.tags() : normalizeTags(request.tags()),
                    existing.createdAt(),
                    Instant.now(clock)
            );
            return secretRepository.save(updated);
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
        String trimmed = candidate.trim();
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
                normalized.add(tag.trim().toLowerCase());
            }
        }
        return Set.copyOf(normalized);
    }
}
