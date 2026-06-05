package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptedData;
import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptionKey;
import lt.satsyuk.blockchainsecretsvault.kms.service.KmsService;
import lt.satsyuk.blockchainsecretsvault.kms.service.KeyNotFoundException;
import lt.satsyuk.blockchainsecretsvault.secretsapi.api.CreateSecretRequest;
import lt.satsyuk.blockchainsecretsvault.secretsapi.api.UpdateSecretRequest;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AclTransaction;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessGrant;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import lt.satsyuk.blockchainsecretsvault.secretsapi.model.SecretRecord;
import lt.satsyuk.blockchainsecretsvault.secretsapi.repository.SecretRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.crypto.WalletUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class SecretsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecretsService.class);
    private final SecretRepository secretRepository;
    private final KmsService kmsService;
    private final BlockchainAclClient blockchainAclClient;
    private final AuditWriter auditWriter;
    private final Clock clock;
    private final ReentrantReadWriteLock mutationLock = new ReentrantReadWriteLock();
    private static final String DEFAULT_KEY_ID = "default-secret-key";

    public SecretsService(
            SecretRepository secretRepository,
            KmsService kmsService,
            BlockchainAclClient blockchainAclClient,
            AuditWriter auditWriter,
            Clock clock
    ) {
        this.secretRepository = secretRepository;
        this.kmsService = kmsService;
        this.blockchainAclClient = blockchainAclClient;
        this.auditWriter = auditWriter;
        this.clock = clock;
        initializeDefaultKey();
    }

    private void initializeDefaultKey() {
        try {
            kmsService.getActiveKey(DEFAULT_KEY_ID);
        } catch (KeyNotFoundException _) {
            kmsService.generateKey(DEFAULT_KEY_ID);
        }
    }

    private String encodeEncryptedData(EncryptedData encrypted) {
        int size = encrypted.ciphertext().length + encrypted.nonce().length + encrypted.authTag().length + 12;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.putInt(encrypted.ciphertext().length);
        buffer.put(encrypted.ciphertext());
        buffer.putInt(encrypted.nonce().length);
        buffer.put(encrypted.nonce());
        buffer.putInt(encrypted.authTag().length);
        buffer.put(encrypted.authTag());
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    public Mono<SecretRecord> create(CreateSecretRequest request) {
        return Mono.fromSupplier(() -> withMutationReadLock(() -> {
            String normalizedName = normalizeName(request.name());
            Instant now = Instant.now(clock);

            EncryptedData encrypted = kmsService.encrypt(
                    DEFAULT_KEY_ID,
                    request.payload().getBytes(StandardCharsets.UTF_8)
            );

            SecretRecord secret = new SecretRecord(
                    UUID.randomUUID(),
                    normalizedName,
                    normalizeNullable(request.description()),
                    encodeEncryptedData(encrypted),
                    DEFAULT_KEY_ID,
                    encrypted.keyVersion(),
                    normalizeTags(request.tags()),
                    now,
                    now
            );
            return secretRepository.saveIfNameAvailable(secret, Optional.empty())
                    .orElseThrow(() -> new DuplicateSecretNameException(normalizedName));
        }));
    }

    public Flux<SecretRecord> list() {
        return Flux.defer(() -> Flux.fromIterable(secretRepository.findAll()));
    }

    public Mono<SecretRecord> get(UUID id) {
        return Mono.fromSupplier(() -> secretRepository.findById(id)
                .orElseThrow(() -> new SecretNotFoundException(id)));
    }

    public Mono<SecretRecord> update(UUID id, UpdateSecretRequest request) {
        return Mono.fromSupplier(() -> withMutationReadLock(() -> {
            if (request.isEmpty()) {
                throw new EmptySecretUpdateException();
            }

            SecretRecord existing = secretRepository.findById(id)
                    .orElseThrow(() -> new SecretNotFoundException(id));

            String nextName = chooseString(request.name(), existing.name());
            
            String nextEncryptedPayload = existing.encryptedPayload();
            int nextKeyVersion = existing.encryptionKeyVersion();
            
            if (request.payload() != null) {
                EncryptedData encrypted = kmsService.encrypt(DEFAULT_KEY_ID, 
                    request.payload().getBytes(StandardCharsets.UTF_8));
                nextEncryptedPayload = encodeEncryptedData(encrypted);
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
        }));
    }

    public Mono<Void> delete(UUID id) {
        return Mono.fromRunnable(() -> withMutationReadLock(() -> {
            if (!secretRepository.deleteById(id)) {
                throw new SecretNotFoundException(id);
            }
        }));
    }

    public Mono<KeyRotationResult> rotateEncryptionKey() {
        return Mono.fromSupplier(() -> withMutationWriteLock(() -> {
            EncryptionKey previousActiveKey = kmsService.getActiveKey(DEFAULT_KEY_ID);
            int previousVersion = previousActiveKey.version();

            java.util.List<SecretRecord> secretsToRotate = secretRepository.findAll().stream()
                    .filter(secret -> DEFAULT_KEY_ID.equals(secret.encryptionKeyId()))
                    .filter(secret -> secret.encryptionKeyVersion() <= previousVersion)
                    .toList();

            Map<UUID, byte[]> plaintextBySecretId = new HashMap<>();
            for (SecretRecord secret : secretsToRotate) {
                plaintextBySecretId.put(secret.id(), kmsService.decrypt(decodeEncryptedData(secret)));
            }

            EncryptionKey newActiveKey = kmsService.rotateKey(DEFAULT_KEY_ID);
            Instant now = Instant.now(clock);

            for (SecretRecord secret : secretsToRotate) {
                EncryptedData reEncrypted = kmsService.encrypt(DEFAULT_KEY_ID, plaintextBySecretId.get(secret.id()));
                SecretRecord updated = new SecretRecord(
                        secret.id(),
                        secret.name(),
                        secret.description(),
                        encodeEncryptedData(reEncrypted),
                        secret.encryptionKeyId(),
                        reEncrypted.keyVersion(),
                        secret.tags(),
                        secret.createdAt(),
                        now
                );
                secretRepository.save(updated);
            }

            return new KeyRotationResult(
                    DEFAULT_KEY_ID,
                    previousVersion,
                    newActiveKey.version(),
                    secretsToRotate.size()
            );
        }));
    }

    public Mono<AclTransaction> grantAccess(UUID id, String account, boolean canRead, boolean canWrite) {
        return Mono.fromSupplier(() -> {
            requireExistingSecret(id);
            String normalizedAccount = normalizeAccount(account);
            String transactionHash = blockchainAclClient.grantAccess(id, normalizedAccount, canRead, canWrite);
            publishAuditEvent(
                    id,
                    normalizedAccount,
                    AccessAuditAction.GRANT,
                    "canRead=%s;canWrite=%s".formatted(canRead, canWrite),
                    transactionHash
            );
            return new AclTransaction(normalizedAccount, transactionHash);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<AclTransaction> revokeAccess(UUID id, String account) {
        return Mono.fromSupplier(() -> {
            requireExistingSecret(id);
            String normalizedAccount = normalizeAccount(account);
            String transactionHash = blockchainAclClient.revokeAccess(id, normalizedAccount);
            publishAuditEvent(id, normalizedAccount, AccessAuditAction.REVOKE, "", transactionHash);
            return new AclTransaction(normalizedAccount, transactionHash);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void publishAuditEvent(
            UUID id,
            String account,
            AccessAuditAction action,
            String details,
            String aclTransactionHash
    ) {
        try {
            auditWriter.publish(id, account, action, details);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "ACL {} transaction {} was submitted, but audit publish failed for secret {} and account {}",
                    action,
                    aclTransactionHash,
                    id,
                    account,
                    exception
            );
        }
    }

    public Mono<AccessGrant> checkAccess(UUID id, String account) {
        return Mono.fromSupplier(() -> {
            requireExistingSecret(id);
            return normalizeAccount(account);
        }).flatMap(normalizedAccount -> {
            Mono<Boolean> canRead = Mono.fromSupplier(() -> blockchainAclClient.canRead(id, normalizedAccount))
                    .subscribeOn(Schedulers.boundedElastic());
            Mono<Boolean> canWrite = Mono.fromSupplier(() -> blockchainAclClient.canWrite(id, normalizedAccount))
                    .subscribeOn(Schedulers.boundedElastic());
            return Mono.zip(canRead, canWrite)
                    .map(permissions -> new AccessGrant(
                            normalizedAccount,
                            permissions.getT1(),
                            permissions.getT2()
                    ));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<AuditEventRecord> listAudit(UUID id, String action, String account) {
        return Flux.defer(() -> {
            requireExistingSecret(id);
            return Flux.fromIterable(auditWriter.history(
                    id,
                    parseAuditAction(action),
                    Optional.ofNullable(account).map(String::trim).filter(value -> !value.isBlank())
            ));
        });
    }

    private SecretRecord requireExistingSecret(UUID id) {
        return secretRepository.findById(id)
                .orElseThrow(() -> new SecretNotFoundException(id));
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

    private static String normalizeAccount(String account) {
        if (account == null || !WalletUtils.isValidAddress(account)) {
            throw new InvalidBlockchainAccountException(String.valueOf(account));
        }
        return account.toLowerCase(Locale.ROOT);
    }

    private static Optional<AccessAuditAction> parseAuditAction(String action) {
        if (action == null || action.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AccessAuditAction.valueOf(action.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException _) {
            throw new InvalidAuditActionException(action);
        }
    }

    private EncryptedData decodeEncryptedData(SecretRecord secret) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(secret.encryptedPayload()));
            byte[] ciphertext = readEncodedSegment(buffer, "ciphertext");
            byte[] nonce = readEncodedSegment(buffer, "nonce");
            byte[] authTag = readEncodedSegment(buffer, "authTag");
            if (buffer.hasRemaining()) {
                throw new IllegalArgumentException(
                        "Encrypted payload contains trailing bytes for secret " + secret.id()
                );
            }
            return new EncryptedData(
                    ciphertext,
                    nonce,
                    authTag,
                    secret.encryptionKeyId(),
                    secret.encryptionKeyVersion()
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Stored encrypted payload is malformed for secret " + secret.id(), exception);
        }
    }

    private static byte[] readEncodedSegment(ByteBuffer buffer, String segmentName) {
        if (buffer.remaining() < Integer.BYTES) {
            throw new IllegalArgumentException("Missing length prefix for " + segmentName);
        }
        int length = buffer.getInt();
        if (length <= 0) {
            throw new IllegalArgumentException("Invalid length for " + segmentName + ": " + length);
        }
        if (buffer.remaining() < length) {
            throw new IllegalArgumentException("Invalid payload length for " + segmentName + ": " + length);
        }
        byte[] segment = new byte[length];
        buffer.get(segment);
        return segment;
    }

    private <T> T withMutationReadLock(Supplier<T> action) {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private void withMutationReadLock(Runnable action) {
        Lock lock = mutationLock.readLock();
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    private <T> T withMutationWriteLock(Supplier<T> action) {
        Lock lock = mutationLock.writeLock();
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
