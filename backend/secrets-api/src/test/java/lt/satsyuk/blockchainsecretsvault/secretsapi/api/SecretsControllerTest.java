package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import lt.satsyuk.blockchainsecretsvault.secretsapi.repository.SecretRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(properties = {
        "secrets.auth.password=change-me",
        "secrets.auth.jwt-secret=test-jwt-secret-with-enough-entropy"
})
@AutoConfigureWebTestClient
@SuppressWarnings("java:S2068")
class SecretsControllerTest {
    private static final String LOGIN_URI = "/api/v1/auth/login";
    private static final String SECRETS_URI = "/api/v1/secrets";
    private static final String SECRET_BY_ID_URI = "/api/v1/secrets/{id}";
    private static final String SECRET_ACL_URI = "/api/v1/secrets/{id}/acl/{account}";
    private static final String REQUEST_VALIDATION_FAILED = "Request validation failed";
    private static final String ALPHA = "alpha";
    private static final String PAYLOAD_FIELD = "payload";
    private static final String PAYMENT_API = "payment-api";
    private static final String TEST_PASSWORD = "change-me";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private SecretRepository secretRepository;

    @MockitoBean
    private BlockchainAclClient blockchainAclClient;

    @BeforeEach
    void clearRepository() {
        secretRepository.deleteAll();
        webTestClient = webTestClient.mutate()
                .defaultHeaders(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.setBearerAuth(login());
                })
                .build();
    }


    @Test
    void createsListsGetsUpdatesAndDeletesSecret() {
        SecretResponse created = webTestClient.post()
                .uri(SECRETS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", PAYMENT_API,
                        "description", "API token",
                        PAYLOAD_FIELD, "secret-value",
                        "tags", new String[]{"prod", "payments"}
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/api/v1/secrets/.+")
                .expectBody(SecretResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo(PAYMENT_API);
        assertThat(created.tags()).containsExactlyInAnyOrder("prod", "payments");

        webTestClient.get()
                .uri(SECRETS_URI)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(SecretResponse.class)
                .hasSize(1);

        webTestClient.get()
                .uri(SECRET_BY_ID_URI, created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(SecretResponse.class)
                .value(secret -> assertThat(secret.name()).isEqualTo(PAYMENT_API));

        webTestClient.put()
                .uri(SECRET_BY_ID_URI, created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "payment-api-renamed", PAYLOAD_FIELD, "rotated-value"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SecretResponse.class)
                .value(secret -> assertThat(secret.name()).isEqualTo("payment-api-renamed"));

        webTestClient.delete()
                .uri(SECRET_BY_ID_URI, created.id())
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        webTestClient.get()
                .uri(SECRET_BY_ID_URI, created.id())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).contains(created.id().toString()));
    }

    @Test
    void returnsBadRequestForInvalidCreateAndEmptyUpdate() {
        webTestClient.post()
                .uri(SECRETS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "", PAYLOAD_FIELD, ""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> {
                    assertThat(error.message()).isEqualTo(REQUEST_VALIDATION_FAILED);
                    assertThat(error.details()).containsKeys("name", PAYLOAD_FIELD);
                });

        SecretResponse created = create(ALPHA);

        webTestClient.put()
                .uri(SECRET_BY_ID_URI, created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).contains("At least one"));
    }

    @Test
    void returnsBadRequestForBlankUpdateNameAndPayload() {
        SecretResponse created = create(ALPHA);

        webTestClient.put()
                .uri(SECRET_BY_ID_URI, created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "  ", PAYLOAD_FIELD, ""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> {
                    assertThat(error.message()).isEqualTo(REQUEST_VALIDATION_FAILED);
                    assertThat(error.details()).containsKeys("name", PAYLOAD_FIELD);
                    assertThat(error.details()).containsEntry("name", "must not be blank");
                    assertThat(error.details()).containsEntry(PAYLOAD_FIELD, "must not be blank");
                });
    }

    @Test
    void returnsBadRequestForBlankUpdateTags() {
        SecretResponse created = create(ALPHA);

        webTestClient.put()
                .uri(SECRET_BY_ID_URI, created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("tags", new String[]{"prod", "  "}))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> {
                    assertThat(error.message()).isEqualTo(REQUEST_VALIDATION_FAILED);
                    assertThat(error.details()).containsKey("tags[]");
                });
    }

    @Test
    void returnsAllValidationMessagesForSameField() {
        SecretResponse created = create(ALPHA);

        webTestClient.put()
                .uri(SECRET_BY_ID_URI, created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("tags", new String[]{" ".repeat(65)}))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.details())
                        .containsEntry("tags[]", "must not be blank; size must be between 0 and 64"));
    }

    @Test
    void returnsConflictForDuplicateName() {
        SecretResponse first = create(ALPHA);
        assertThat(first).isNotNull();

        webTestClient.post()
                .uri(SECRETS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "ALPHA", PAYLOAD_FIELD, "second"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).contains("ALPHA"));
    }

    @Test
    void returnsBadRequestForMalformedJsonAndInvalidUuid() {
        webTestClient.post()
                .uri(SECRETS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).isEqualTo("Malformed request"));

        webTestClient.get()
                .uri("/api/v1/secrets/not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rotatesDefaultEncryptionKeyAndReEncryptsStoredSecrets() {
        create(ALPHA);
        create("beta");

        webTestClient.post()
                .uri("/api/v1/secrets/encryption-key/rotate")
                .exchange()
                .expectStatus().isOk()
                .expectBody(KeyRotationResponse.class)
                .value(response -> {
                    assertThat(response.keyId()).isEqualTo("default-secret-key");
                    assertThat(response.previousKeyVersion()).isZero();
                    assertThat(response.newKeyVersion()).isEqualTo(1);
                    assertThat(response.reEncryptedSecrets()).isEqualTo(2);
                });
    }

    @Test
    void grantsRevokesAndChecksAcl() {
        SecretResponse created = create(ALPHA);
        String account = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";
        String mixedCaseAccount = "0x" + account.substring(2).toUpperCase();

        when(blockchainAclClient.grantAccess(created.id(), account, true, false)).thenReturn("0xgrant");
        when(blockchainAclClient.revokeAccess(created.id(), account)).thenReturn("0xrevoke");
        when(blockchainAclClient.auditEvent(eq(created.id()), eq(account), eq(AccessAuditAction.GRANT), anyString()))
                .thenReturn("0xauditgrant");
        when(blockchainAclClient.auditEvent(eq(created.id()), eq(account), eq(AccessAuditAction.REVOKE), anyString()))
                .thenReturn("0xauditrevoke");
        when(blockchainAclClient.canRead(created.id(), account)).thenReturn(true);
        when(blockchainAclClient.canWrite(created.id(), account)).thenReturn(false);

        webTestClient.put()
                .uri(SECRET_ACL_URI, created.id(), mixedCaseAccount)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("canRead", true, "canWrite", false))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(AclTransactionResponse.class)
                .value(response -> {
                    assertThat(response.account()).isEqualTo(account);
                    assertThat(response.transactionHash()).isEqualTo("0xgrant");
                });

        webTestClient.get()
                .uri(SECRET_ACL_URI, created.id(), account)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AccessGrantResponse.class)
                .value(response -> {
                    assertThat(response.canRead()).isTrue();
                    assertThat(response.canWrite()).isFalse();
                    assertThat(response.account()).isEqualTo(account);
                });

        webTestClient.delete()
                .uri(SECRET_ACL_URI, created.id(), mixedCaseAccount)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(AclTransactionResponse.class)
                .value(response -> {
                    assertThat(response.account()).isEqualTo(account);
                    assertThat(response.transactionHash()).isEqualTo("0xrevoke");
                });

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/secrets/{id}/audit")
                        .queryParam("action", "GRANT")
                        .queryParam("account", "abcdef")
                        .build(created.id()))
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(AuditEventResponse.class)
                .value(events -> {
                    assertThat(events).hasSize(1);
                    assertThat(events.getFirst().account()).isEqualTo(account);
                    assertThat(events.getFirst().action()).isEqualTo(AccessAuditAction.GRANT);
                    assertThat(events.getFirst().transactionHash()).isEqualTo("0xauditgrant");
                });
    }

    @Test
    void returnsBadRequestForInvalidAclAddressAndRequestBody() {
        SecretResponse created = create(ALPHA);

        webTestClient.get()
                .uri("/api/v1/secrets/{id}/acl/not-an-address", created.id())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).contains("Invalid blockchain account"));

        webTestClient.put()
                .uri(SECRET_ACL_URI, created.id(), "0x1111111111111111111111111111111111111111")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("canRead", true))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.details()).containsKey("canWrite"));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/secrets/{id}/audit")
                        .queryParam("action", "bad-action")
                        .build(created.id()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).contains("Invalid audit action"));
    }

    private SecretResponse create(String name) {
        return webTestClient.post()
                .uri(SECRETS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", name, PAYLOAD_FIELD, "secret-value"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(SecretResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private String login() {
        AuthResponse response = webTestClient.mutate()
                .defaultHeaders(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
                .build()
                .post()
                .uri(LOGIN_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin", "password", TEST_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(response).isNotNull();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isPositive();
        return response.accessToken();
    }
}
