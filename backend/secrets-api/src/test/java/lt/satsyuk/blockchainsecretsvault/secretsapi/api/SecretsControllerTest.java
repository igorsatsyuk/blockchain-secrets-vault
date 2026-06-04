package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import lt.satsyuk.blockchainsecretsvault.secretsapi.repository.SecretRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class SecretsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private SecretRepository secretRepository;

    @MockitoBean
    private BlockchainAclClient blockchainAclClient;

    @BeforeEach
    void clearRepository() {
        secretRepository.deleteAll();
    }

    @Test
    void createsListsGetsUpdatesAndDeletesSecret() {
        SecretResponse created = webTestClient.post()
                .uri("/api/v1/secrets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "payment-api",
                        "description", "API token",
                        "payload", "secret-value",
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
        assertThat(created.name()).isEqualTo("payment-api");
        assertThat(created.tags()).containsExactlyInAnyOrder("prod", "payments");

        webTestClient.get()
                .uri("/api/v1/secrets")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(SecretResponse.class)
                .hasSize(1);

        webTestClient.get()
                .uri("/api/v1/secrets/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(SecretResponse.class)
                .value(secret -> assertThat(secret.name()).isEqualTo("payment-api"));

        webTestClient.put()
                .uri("/api/v1/secrets/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "payment-api-renamed", "payload", "rotated-value"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SecretResponse.class)
                .value(secret -> assertThat(secret.name()).isEqualTo("payment-api-renamed"));

        webTestClient.delete()
                .uri("/api/v1/secrets/{id}", created.id())
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        webTestClient.get()
                .uri("/api/v1/secrets/{id}", created.id())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).contains(created.id().toString()));
    }

    @Test
    void returnsBadRequestForInvalidCreateAndEmptyUpdate() {
        webTestClient.post()
                .uri("/api/v1/secrets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "", "payload", ""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> {
                    assertThat(error.message()).isEqualTo("Request validation failed");
                    assertThat(error.details()).containsKeys("name", "payload");
                });

        SecretResponse created = create("alpha");

        webTestClient.put()
                .uri("/api/v1/secrets/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).contains("At least one"));
    }

    @Test
    void returnsBadRequestForBlankUpdateNameAndPayload() {
        SecretResponse created = create("alpha");

        webTestClient.put()
                .uri("/api/v1/secrets/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "  ", "payload", ""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> {
                    assertThat(error.message()).isEqualTo("Request validation failed");
                    assertThat(error.details()).containsKeys("name", "payload");
                    assertThat(error.details()).containsEntry("name", "must not be blank");
                    assertThat(error.details()).containsEntry("payload", "must not be blank");
                });
    }

    @Test
    void returnsBadRequestForBlankUpdateTags() {
        SecretResponse created = create("alpha");

        webTestClient.put()
                .uri("/api/v1/secrets/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("tags", new String[]{"prod", "  "}))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> {
                    assertThat(error.message()).isEqualTo("Request validation failed");
                    assertThat(error.details()).containsKey("tags[]");
                });
    }

    @Test
    void returnsAllValidationMessagesForSameField() {
        SecretResponse created = create("alpha");

        webTestClient.put()
                .uri("/api/v1/secrets/{id}", created.id())
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
        SecretResponse first = create("alpha");
        assertThat(first).isNotNull();

        webTestClient.post()
                .uri("/api/v1/secrets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "ALPHA", "payload", "second"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).contains("ALPHA"));
    }

    @Test
    void returnsBadRequestForMalformedJsonAndInvalidUuid() {
        webTestClient.post()
                .uri("/api/v1/secrets")
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
    void grantsRevokesAndChecksAcl() {
        SecretResponse created = create("alpha");
        String account = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";
        String mixedCaseAccount = "0x" + account.substring(2).toUpperCase();

        when(blockchainAclClient.grantAccess(created.id(), account, true, false)).thenReturn("0xgrant");
        when(blockchainAclClient.revokeAccess(created.id(), account)).thenReturn("0xrevoke");
        when(blockchainAclClient.canRead(created.id(), account)).thenReturn(true);
        when(blockchainAclClient.canWrite(created.id(), account)).thenReturn(false);

        webTestClient.put()
                .uri("/api/v1/secrets/{id}/acl/{account}", created.id(), mixedCaseAccount)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("canRead", true, "canWrite", false))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AclTransactionResponse.class)
                .value(response -> {
                    assertThat(response.account()).isEqualTo(account);
                    assertThat(response.transactionHash()).isEqualTo("0xgrant");
                });

        webTestClient.get()
                .uri("/api/v1/secrets/{id}/acl/{account}", created.id(), account)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AccessGrantResponse.class)
                .value(response -> {
                    assertThat(response.canRead()).isTrue();
                    assertThat(response.canWrite()).isFalse();
                    assertThat(response.account()).isEqualTo(account);
                });

        webTestClient.delete()
                .uri("/api/v1/secrets/{id}/acl/{account}", created.id(), mixedCaseAccount)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(AclTransactionResponse.class)
                .value(response -> {
                    assertThat(response.account()).isEqualTo(account);
                    assertThat(response.transactionHash()).isEqualTo("0xrevoke");
                });
    }

    @Test
    void returnsBadRequestForInvalidAclAddressAndRequestBody() {
        SecretResponse created = create("alpha");

        webTestClient.get()
                .uri("/api/v1/secrets/{id}/acl/not-an-address", created.id())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).contains("Invalid blockchain account"));

        webTestClient.put()
                .uri("/api/v1/secrets/{id}/acl/{account}", created.id(), "0x1111111111111111111111111111111111111111")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("canRead", true))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.details()).containsKey("canWrite"));
    }

    private SecretResponse create(String name) {
        return webTestClient.post()
                .uri("/api/v1/secrets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", name, "payload", "secret-value"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(SecretResponse.class)
                .returnResult()
                .getResponseBody();
    }
}

