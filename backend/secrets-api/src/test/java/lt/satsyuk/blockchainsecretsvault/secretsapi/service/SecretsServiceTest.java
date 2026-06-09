package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import lt.satsyuk.blockchainsecretsvault.kms.model.EncryptedData;
import lt.satsyuk.blockchainsecretsvault.kms.service.KmsService;
import lt.satsyuk.blockchainsecretsvault.secretsapi.api.CreateSecretRequest;
import lt.satsyuk.blockchainsecretsvault.secretsapi.api.UpdateSecretRequest;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclException;
import lt.satsyuk.blockchainsecretsvault.secretsapi.model.SecretRecord;
import lt.satsyuk.blockchainsecretsvault.secretsapi.repository.InMemorySecretRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class SecretsServiceTest {
    private static final String ALPHA = "alpha";
    private static final String PAYLOAD = "payload";
    private static final String DESCRIPTION = "description";
    private static final String DEFAULT_KEY_ID = "default-secret-key";
    private static final String ACCOUNT = "0x1111111111111111111111111111111111111111";
    private static final String GRANT_TX = "0xgrant";
    private static final String REVOKE_TX = "0xrevoke";

    private final InMemorySecretRepository repository = new InMemorySecretRepository();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final KmsService kmsService = createMockKmsService();
    private final BlockchainAclClient blockchainAclClient = mock(BlockchainAclClient.class);
    private final AuditWriter auditWriter = new AuditWriter(blockchainAclClient, new AuditEventHasher(), clock);
    private final SecretsService service = new SecretsService(
            repository,
            kmsService,
            blockchainAclClient,
            auditWriter,
            clock
    );

    private static KmsService createMockKmsService() {
        KmsService mock = mock(KmsService.class);
        EncryptedData mockEncrypted = new EncryptedData(
            "encrypted".getBytes(),
            new byte[12],
            new byte[16],
            DEFAULT_KEY_ID,
            0
        );
        when(mock.encrypt(anyString(), any(byte[].class))).thenReturn(mockEncrypted);
        return mock;
    }

    @Test
    void createsSecretWithNormalizedFields() {
        CreateSecretRequest request = new CreateSecretRequest(
                "  payment-api  ",
                "  tokens  ",
                "secret-value",
                Set.of(" PROD ", "api")
        );

        StepVerifier.create(service.create(request))
                .assertNext(secret -> {
                    assertThat(secret.id()).isNotNull();
                    assertThat(secret.name()).isEqualTo("payment-api");
                    assertThat(secret.description()).isEqualTo("tokens");
                    assertThat(secret.encryptionKeyId()).isEqualTo(DEFAULT_KEY_ID);
                    assertThat(secret.tags()).containsExactlyInAnyOrder("prod", "api");
                    assertThat(secret.createdAt()).isEqualTo(clock.instant());
                    assertThat(secret.updatedAt()).isEqualTo(clock.instant());
                })
                .verifyComplete();
    }

    @Test
    void normalizesTagsWithStableLocale() {
        Locale previousDefault = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            CreateSecretRequest request = new CreateSecretRequest(ALPHA, null, PAYLOAD, Set.of("IDENTITY"));

            StepVerifier.create(service.create(request))
                    .assertNext(secret -> assertThat(secret.tags()).containsExactly("identity"))
                    .verifyComplete();
        } finally {
            Locale.setDefault(previousDefault);
        }
    }

    @Test
    void rejectsDuplicateCreateByNameIgnoringCase() {
        service.create(new CreateSecretRequest("payment-api", null, "one", Set.of())).block();

        StepVerifier.create(service.create(new CreateSecretRequest("PAYMENT-API", null, "two", Set.of())))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DuplicateSecretNameException.class)
                        .hasMessageContaining("PAYMENT-API"))
                .verify();
    }

    @Test
    void rejectsDuplicateCreateWhenNameDiffersOnlyByWhitespace() {
        service.create(new CreateSecretRequest(ALPHA, null, "one", Set.of())).block();

        StepVerifier.create(service.create(new CreateSecretRequest("  alpha  ", null, "two", Set.of())))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DuplicateSecretNameException.class)
                        .hasMessageContaining(ALPHA))
                .verify();
    }

    @Test
    void listsAndGetsExistingSecrets() {
        SecretRecord created = service.create(new CreateSecretRequest(ALPHA, null, PAYLOAD, Set.of())).block();

        StepVerifier.create(service.list())
                .expectNext(created)
                .verifyComplete();

        StepVerifier.create(service.get(created.id()))
                .expectNext(created)
                .verifyComplete();
    }

    @Test
    void failsWhenGettingMissingSecret() {
        UUID missing = UUID.randomUUID();

        StepVerifier.create(service.get(missing))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SecretNotFoundException.class)
                        .hasMessageContaining(missing.toString()))
                .verify();
    }

    @Test
    void updatesOnlyProvidedFields() {
        SecretRecord created = service.create(new CreateSecretRequest(
                ALPHA,
                DESCRIPTION,
                PAYLOAD,
                Set.of("one")
        )).block();

        UpdateSecretRequest request = new UpdateSecretRequest(" beta ", null, "new-payload", Set.of(" TWO "));

        StepVerifier.create(service.update(created.id(), request))
                .assertNext(updated -> {
                    assertThat(updated.id()).isEqualTo(created.id());
                    assertThat(updated.name()).isEqualTo("beta");
                    assertThat(updated.description()).isEqualTo(DESCRIPTION);
                    assertThat(updated.encryptionKeyId()).isEqualTo(DEFAULT_KEY_ID);
                    assertThat(updated.tags()).containsExactly("two");
                    assertThat(updated.createdAt()).isEqualTo(created.createdAt());
                    assertThat(updated.updatedAt()).isEqualTo(clock.instant());
                })
                .verifyComplete();
    }

    @Test
    void updateCanKeepNameWhenMissingAndClearDescriptionAndNormalizeTags() {
        SecretRecord created = service.create(new CreateSecretRequest(
                ALPHA,
                DESCRIPTION,
                PAYLOAD,
                Set.of("one")
        )).block();

        UpdateSecretRequest request = new UpdateSecretRequest(null, "  ", null, Set.of(" TWO "));

        StepVerifier.create(service.update(created.id(), request))
                .assertNext(updated -> {
                    assertThat(updated.name()).isEqualTo(ALPHA);
                    assertThat(updated.description()).isNull();
                    assertThat(updated.encryptionKeyId()).isEqualTo(DEFAULT_KEY_ID);
                    assertThat(updated.tags()).containsExactly("two");
                })
                .verifyComplete();
    }

    @Test
    void updateAllowsKeepingSameNameAndFailsWhenMissingSecret() {
        SecretRecord created = service.create(new CreateSecretRequest(ALPHA, null, PAYLOAD, null)).block();

        StepVerifier.create(service.update(created.id(), new UpdateSecretRequest("ALPHA", null, null, null)))
                .assertNext(updated -> assertThat(updated.name()).isEqualTo("ALPHA"))
                .verifyComplete();

        UUID missing = UUID.randomUUID();
        StepVerifier.create(service.update(missing, new UpdateSecretRequest("missing", null, null, null)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SecretNotFoundException.class)
                        .hasMessageContaining(missing.toString()))
                .verify();
    }

    @Test
    void rejectsEmptyUpdateAndDuplicateRename() {
        SecretRecord first = service.create(new CreateSecretRequest("first", null, PAYLOAD, Set.of())).block();
        service.create(new CreateSecretRequest("second", null, PAYLOAD, Set.of())).block();

        StepVerifier.create(service.update(first.id(), new UpdateSecretRequest(null, null, null, null)))
                .expectError(EmptySecretUpdateException.class)
                .verify();

        StepVerifier.create(service.update(first.id(), new UpdateSecretRequest("second", null, null, null)))
                .expectError(DuplicateSecretNameException.class)
                .verify();
    }

    @Test
    void deletesExistingSecretAndFailsForMissingSecret() {
        SecretRecord created = service.create(new CreateSecretRequest(ALPHA, null, PAYLOAD, Set.of())).block();

        StepVerifier.create(service.delete(created.id())).verifyComplete();
        StepVerifier.create(service.delete(created.id()))
                .expectError(SecretNotFoundException.class)
                .verify();
    }

    @Test
    void detectsEmptyUpdateRequestOnlyWhenAllFieldsAreMissing() {
        assertThat(new UpdateSecretRequest(null, null, null, null).isEmpty()).isTrue();
        assertThat(new UpdateSecretRequest("name", null, null, null).isEmpty()).isFalse();
        assertThat(new UpdateSecretRequest(null, DESCRIPTION, null, null).isEmpty()).isFalse();
        assertThat(new UpdateSecretRequest(null, null, PAYLOAD, null).isEmpty()).isFalse();
        assertThat(new UpdateSecretRequest(null, null, null, Set.of("tag")).isEmpty()).isFalse();
    }

    @Test
    void grantsRevokesAndChecksBlockchainAcl() {
        SecretRecord created = service.create(new CreateSecretRequest(ALPHA, null, PAYLOAD, Set.of())).block();
        String account = ACCOUNT;

        when(blockchainAclClient.grantAccess(created.id(), account, true, false)).thenReturn(GRANT_TX);
        when(blockchainAclClient.revokeAccess(created.id(), account)).thenReturn(REVOKE_TX);
        when(blockchainAclClient.auditEvent(any(), anyString(), any(), anyString())).thenReturn("0xaudit");
        when(blockchainAclClient.canRead(created.id(), account)).thenReturn(true);
        when(blockchainAclClient.canWrite(created.id(), account)).thenReturn(false);

        StepVerifier.create(service.grantAccess(created.id(), account, true, false))
                .assertNext(transaction -> {
                    assertThat(transaction.account()).isEqualTo(account);
                    assertThat(transaction.transactionHash()).isEqualTo(GRANT_TX);
                })
                .verifyComplete();

        StepVerifier.create(service.checkAccess(created.id(), account))
                .assertNext(grant -> {
                    assertThat(grant.account()).isEqualTo(account);
                    assertThat(grant.canRead()).isTrue();
                    assertThat(grant.canWrite()).isFalse();
                })
                .verifyComplete();

        StepVerifier.create(service.revokeAccess(created.id(), account))
                .assertNext(transaction -> {
                    assertThat(transaction.account()).isEqualTo(account);
                    assertThat(transaction.transactionHash()).isEqualTo(REVOKE_TX);
                })
                .verifyComplete();

        verify(blockchainAclClient).grantAccess(created.id(), account, true, false);
        verify(blockchainAclClient).auditEvent(
                created.id(),
                account,
                AccessAuditAction.GRANT,
                new AuditEventHasher().hash(
                        created.id(),
                        account,
                        AccessAuditAction.GRANT,
                        clock.instant(),
                        "canRead=true;canWrite=false"
                )
        );
        verify(blockchainAclClient).revokeAccess(created.id(), account);
        verify(blockchainAclClient).auditEvent(
                created.id(),
                account,
                AccessAuditAction.REVOKE,
                new AuditEventHasher().hash(created.id(), account, AccessAuditAction.REVOKE, clock.instant(), "")
        );

        StepVerifier.create(service.listAudit(created.id(), "grant", "1111"))
                .assertNext(event -> {
                    assertThat(event.action()).isEqualTo(AccessAuditAction.GRANT);
                    assertThat(event.account()).isEqualTo(account);
                    assertThat(event.transactionHash()).isEqualTo("0xaudit");
                })
                .verifyComplete();

        StepVerifier.create(service.listAudit(created.id(), null, "2222"))
                .verifyComplete();
    }

    @Test
    void grantAccessReturnsAclTransactionWhenAuditPublishFails() {
        SecretRecord created = service.create(new CreateSecretRequest(ALPHA, null, PAYLOAD, Set.of())).block();
        String account = ACCOUNT;

        when(blockchainAclClient.grantAccess(created.id(), account, true, false)).thenReturn(GRANT_TX);
        when(blockchainAclClient.auditEvent(any(), anyString(), any(), anyString()))
                .thenThrow(new BlockchainAclException("audit unavailable"));

        StepVerifier.create(service.grantAccess(created.id(), account, true, false))
                .assertNext(transaction -> {
                    assertThat(transaction.account()).isEqualTo(account);
                    assertThat(transaction.transactionHash()).isEqualTo(GRANT_TX);
                })
                .verifyComplete();

        verify(blockchainAclClient).grantAccess(created.id(), account, true, false);
    }

    @Test
    void grantAccessReturnsAclTransactionWhenAuditPublishFailsUnexpectedly() {
        SecretRecord created = service.create(new CreateSecretRequest(ALPHA, null, PAYLOAD, Set.of())).block();
        String account = ACCOUNT;

        when(blockchainAclClient.grantAccess(created.id(), account, true, false)).thenReturn(GRANT_TX);
        when(blockchainAclClient.auditEvent(any(), anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException("hashing unavailable"));

        StepVerifier.create(service.grantAccess(created.id(), account, true, false))
                .assertNext(transaction -> {
                    assertThat(transaction.account()).isEqualTo(account);
                    assertThat(transaction.transactionHash()).isEqualTo(GRANT_TX);
                })
                .verifyComplete();

        verify(blockchainAclClient).grantAccess(created.id(), account, true, false);
    }

    @Test
    void revokeAccessReturnsAclTransactionWhenAuditPublishFails() {
        SecretRecord created = service.create(new CreateSecretRequest(ALPHA, null, PAYLOAD, Set.of())).block();
        String account = ACCOUNT;

        when(blockchainAclClient.revokeAccess(created.id(), account)).thenReturn(REVOKE_TX);
        when(blockchainAclClient.auditEvent(any(), anyString(), any(), anyString()))
                .thenThrow(new BlockchainAclException("audit unavailable"));

        StepVerifier.create(service.revokeAccess(created.id(), account))
                .assertNext(transaction -> {
                    assertThat(transaction.account()).isEqualTo(account);
                    assertThat(transaction.transactionHash()).isEqualTo(REVOKE_TX);
                })
                .verifyComplete();

        verify(blockchainAclClient).revokeAccess(created.id(), account);
    }

    @Test
    void blockchainAclOperationsValidateSecretAndAccount() {
        UUID missing = UUID.randomUUID();

        StepVerifier.create(service.grantAccess(missing, ACCOUNT, true, true))
                .expectError(SecretNotFoundException.class)
                .verify();

        StepVerifier.create(service.listAudit(missing, null, null))
                .expectError(SecretNotFoundException.class)
                .verify();

        SecretRecord created = service.create(new CreateSecretRequest(ALPHA, null, PAYLOAD, Set.of())).block();

        StepVerifier.create(service.checkAccess(created.id(), "not-an-address"))
                .expectError(InvalidBlockchainAccountException.class)
                .verify();

        StepVerifier.create(service.listAudit(created.id(), "bad-action", null))
                .expectError(InvalidAuditActionException.class)
                .verify();
    }

    @Test
    void checksBlockchainPermissionsWithCombinedResult() {
        SecretRecord created = service.create(new CreateSecretRequest(ALPHA, null, PAYLOAD, Set.of())).block();
        String account = ACCOUNT;

        when(blockchainAclClient.canRead(created.id(), account)).thenReturn(true);
        when(blockchainAclClient.canWrite(created.id(), account)).thenReturn(false);

        StepVerifier.create(service.checkAccess(created.id(), account))
                .assertNext(grant -> {
                    assertThat(grant.canRead()).isTrue();
                    assertThat(grant.canWrite()).isFalse();
                })
                .verifyComplete();

        verify(blockchainAclClient).canRead(created.id(), account);
        verify(blockchainAclClient).canWrite(created.id(), account);
    }

}

