package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptedData;
import lt.satsyuk.blockchainsecretsvault.kms.service.AesGcmKmsService;
import lt.satsyuk.blockchainsecretsvault.kms.service.KmsService;
import lt.satsyuk.blockchainsecretsvault.secretsapi.api.CreateSecretRequest;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import lt.satsyuk.blockchainsecretsvault.secretsapi.model.SecretRecord;
import lt.satsyuk.blockchainsecretsvault.secretsapi.repository.InMemorySecretRepository;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class SecretsServiceKeyRotationTest {
    private static final String ALPHA = "alpha";
    private static final String DEFAULT_KEY_ID = "default-secret-key";
    private static final String EXTERNAL_KEY_ID = "external-key";
    private static final String EXTERNAL_NAME = "external";

    private final InMemorySecretRepository repository = new InMemorySecretRepository();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final KmsService kmsService = new AesGcmKmsService();
    private final BlockchainAclClient blockchainAclClient = mock(BlockchainAclClient.class);
    private final AuditWriter auditWriter = new AuditWriter(blockchainAclClient, new AuditEventHasher(), clock);
    private final SecretsService service = new SecretsService(
            repository,
            kmsService,
            blockchainAclClient,
            auditWriter,
            clock
    );

    @Test
    void rotatesDefaultKeyAndRewrapsEnvelopeEncryptedSecrets() {
        SecretRecord created = service.create(new CreateSecretRequest(ALPHA, null, "initial-value", Set.of())).block();
        SecretRecord beforeRotation = repository.findById(created.id()).orElseThrow();
        String payloadBeforeRotation = decryptPayload(beforeRotation);

        StepVerifier.create(service.rotateEncryptionKey())
                .assertNext(result -> {
                    assertThat(result.keyId()).isEqualTo(DEFAULT_KEY_ID);
                    assertThat(result.previousKeyVersion()).isZero();
                    assertThat(result.newKeyVersion()).isEqualTo(1);
                    assertThat(result.reEncryptedSecrets()).isEqualTo(1);
                })
                .verifyComplete();

        SecretRecord afterRotation = repository.findById(created.id()).orElseThrow();
        assertThat(afterRotation.encryptionKeyVersion()).isEqualTo(1);
        assertThat(afterRotation.encryptedPayload()).isNotEqualTo(beforeRotation.encryptedPayload());
        assertThat(payloadCiphertextBase64(afterRotation)).isEqualTo(payloadCiphertextBase64(beforeRotation));
        assertThat(decryptPayload(afterRotation)).isEqualTo(payloadBeforeRotation);
    }

    @Test
    void rotationSucceedsWhenNoSecretsExist() {
        StepVerifier.create(service.rotateEncryptionKey())
                .assertNext(result -> {
                    assertThat(result.previousKeyVersion()).isZero();
                    assertThat(result.newKeyVersion()).isEqualTo(1);
                    assertThat(result.reEncryptedSecrets()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void rotationReEncryptsOnlyDefaultKeySecrets() {
        SecretRecord defaultKeySecret = service.create(new CreateSecretRequest(ALPHA, null, "payload", Set.of())).block();
        kmsService.generateKey(EXTERNAL_KEY_ID);
        EncryptedData externalEncrypted = kmsService.encrypt(EXTERNAL_KEY_ID, EXTERNAL_NAME.getBytes());
        SecretRecord externalSecret = new SecretRecord(
                UUID.randomUUID(),
                EXTERNAL_NAME,
                null,
                encodeEncryptedData(externalEncrypted),
                EXTERNAL_KEY_ID,
                externalEncrypted.keyVersion(),
                Set.of(),
                clock.instant(),
                clock.instant()
        );
        repository.save(externalSecret);

        StepVerifier.create(service.rotateEncryptionKey())
                .assertNext(result -> assertThat(result.reEncryptedSecrets()).isEqualTo(1))
                .verifyComplete();

        SecretRecord updatedDefault = repository.findById(defaultKeySecret.id()).orElseThrow();
        SecretRecord unchangedExternal = repository.findById(externalSecret.id()).orElseThrow();
        assertThat(updatedDefault.encryptionKeyVersion()).isEqualTo(1);
        assertThat(unchangedExternal.encryptionKeyVersion()).isZero();
        assertThat(decryptPayload(unchangedExternal)).isEqualTo(EXTERNAL_NAME);
    }

    private String decryptPayload(SecretRecord secret) {
        EncryptedData encryptedData = decodeEncryptedData(secret);
        return new String(kmsService.decrypt(encryptedData), StandardCharsets.UTF_8);
    }

    private String payloadCiphertextBase64(SecretRecord secret) {
        return Base64.getEncoder().encodeToString(decodeEncryptedData(secret).ciphertext());
    }

    private EncryptedData decodeEncryptedData(SecretRecord secret) {
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(secret.encryptedPayload()));
        byte[] ciphertext = readSegment(buffer);
        byte[] nonce = readSegment(buffer);
        byte[] authTag = readSegment(buffer);
        byte[] encryptedDataKey = null;
        byte[] dataKeyNonce = null;
        byte[] dataKeyAuthTag = null;
        if (buffer.hasRemaining()) {
            encryptedDataKey = readSegment(buffer);
            dataKeyNonce = readSegment(buffer);
            dataKeyAuthTag = readSegment(buffer);
        }
        return new EncryptedData(
                ciphertext,
                nonce,
                authTag,
                secret.encryptionKeyId(),
                secret.encryptionKeyVersion(),
                encryptedDataKey,
                dataKeyNonce,
                dataKeyAuthTag
        );
    }

    private static String encodeEncryptedData(EncryptedData encrypted) {
        int size = encodedSegmentSize(encrypted.ciphertext())
                + encodedSegmentSize(encrypted.nonce())
                + encodedSegmentSize(encrypted.authTag());
        if (encrypted.envelopeEncrypted()) {
            size += encodedSegmentSize(encrypted.encryptedDataKey())
                    + encodedSegmentSize(encrypted.dataKeyNonce())
                    + encodedSegmentSize(encrypted.dataKeyAuthTag());
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        writeSegment(buffer, encrypted.ciphertext());
        writeSegment(buffer, encrypted.nonce());
        writeSegment(buffer, encrypted.authTag());
        if (encrypted.envelopeEncrypted()) {
            writeSegment(buffer, encrypted.encryptedDataKey());
            writeSegment(buffer, encrypted.dataKeyNonce());
            writeSegment(buffer, encrypted.dataKeyAuthTag());
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private static int encodedSegmentSize(byte[] segment) {
        return Integer.BYTES + segment.length;
    }

    private static void writeSegment(ByteBuffer buffer, byte[] segment) {
        buffer.putInt(segment.length);
        buffer.put(segment);
    }

    private static byte[] readSegment(ByteBuffer buffer) {
        int length = buffer.getInt();
        byte[] segment = new byte[length];
        buffer.get(segment);
        return segment;
    }
}
