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

@SpringBootTest
@AutoConfigureWebTestClient
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private BlockchainAclClient blockchainAclClient;

    @Test
    void loginIssuesBearerTokenThatUnlocksApiRequests() {
        AuthResponse response = webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin", "password", "change-me"))
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
                .uri("/api/v1/secrets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + response.accessToken())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void rejectsMissingInvalidAndExpiredCredentials() {
        webTestClient.get()
                .uri("/api/v1/secrets")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get()
                .uri("/api/v1/secrets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin", "password", "wrong"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.message()).isEqualTo("Invalid username or password"));
    }

    @Test
    void validatesLoginRequestBody() {
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "", "password", ""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(error -> assertThat(error.details()).containsKeys("username", "password"));
    }
}
