package com.blockchainsecretsvault.secretsapi.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.blockchainsecretsvault.secretsapi.repository.SecretRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class SecretsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private SecretRepository secretRepository;

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
