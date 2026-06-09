package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(properties = {
        "secrets.auth.password=change-me",
        "secrets.auth.jwt-secret=test-jwt-secret-with-enough-entropy"
})
@AutoConfigureWebTestClient
@SuppressWarnings("java:S2068")
class AuthControllerTest {
    private static final String LOGIN_URI = "/api/v1/auth/login";
    private static final String SECRETS_URI = "/api/v1/secrets";
    private static final String USERNAME_FIELD = "username";
    private static final String PASSWORD_FIELD = "password";
    private static final String TEST_PASSWORD = "change-me";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private BlockchainAclClient blockchainAclClient;

    @Test
    void loginIssuesBearerTokenThatUnlocksApiRequests() {
        AuthResponse response = webTestClient.post()
                .uri(LOGIN_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(USERNAME_FIELD, " admin ", PASSWORD_FIELD, TEST_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).contains(".");
        assertThat(response.expiresIn()).isEqualTo(3600);

        webTestClient.get()
                .uri(SECRETS_URI)
                .header(HttpHeaders.AUTHORIZATION, "bearer " + response.accessToken())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void rejectsMissingInvalidAndExpiredCredentials() {
        webTestClient.get()
                .uri(SECRETS_URI)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).isEqualTo("Authentication is required"));

        webTestClient.get()
                .uri(SECRETS_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.post()
                .uri(LOGIN_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(USERNAME_FIELD, "admin", PASSWORD_FIELD, TEST_PASSWORD))
                .exchange()
                .expectStatus().isOk();

        webTestClient.post()
                .uri(LOGIN_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(USERNAME_FIELD, "admin", PASSWORD_FIELD, "wrong"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).isEqualTo("Invalid username or password"));
    }

    @Test
    void validatesLoginRequestBody() {
        webTestClient.post()
                .uri(LOGIN_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(USERNAME_FIELD, "", PASSWORD_FIELD, ""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.details()).containsKeys(USERNAME_FIELD, PASSWORD_FIELD));

        webTestClient.post()
                .uri(LOGIN_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(USERNAME_FIELD, "a".repeat(129), PASSWORD_FIELD, "p".repeat(513)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.details()).containsKeys(USERNAME_FIELD, PASSWORD_FIELD));
    }
}
