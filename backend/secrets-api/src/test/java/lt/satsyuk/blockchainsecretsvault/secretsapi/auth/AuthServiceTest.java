package lt.satsyuk.blockchainsecretsvault.secretsapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import lt.satsyuk.blockchainsecretsvault.secretsapi.config.AuthProperties;
import org.junit.jupiter.api.Test;

@SuppressWarnings("java:S2068")
class AuthServiceTest {
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "change-me";

    private static final AuthProperties PROPERTIES = new AuthProperties(
            USERNAME,
            PASSWORD,
            "auth-service-test-jwt-secret-32-bytes",
            "vault-tests",
            Duration.ofMinutes(15)
    );

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(PROPERTIES, clock);
    private final AuthService authService = new AuthService(PROPERTIES, jwtService);

    @Test
    void issuesTokenForValidCredentials() {
        String token = authService.login(USERNAME, PASSWORD);

        assertThat(jwtService.validate(token)).isEqualTo(USERNAME);
    }

    @Test
    void rejectsInvalidCredentials() {
        assertThatThrownBy(() -> authService.login(USERNAME, "wrong-length-password"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> authService.login("administrator", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
