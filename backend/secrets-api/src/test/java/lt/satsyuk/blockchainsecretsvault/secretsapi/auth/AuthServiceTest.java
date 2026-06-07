package lt.satsyuk.blockchainsecretsvault.secretsapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import lt.satsyuk.blockchainsecretsvault.secretsapi.config.AuthProperties;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private static final AuthProperties PROPERTIES = new AuthProperties(
            "admin",
            "change-me",
            "auth-service-test-jwt-secret-32-bytes",
            "vault-tests",
            Duration.ofMinutes(15)
    );

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(PROPERTIES, clock);
    private final AuthService authService = new AuthService(PROPERTIES, jwtService);

    @Test
    void issuesTokenForValidCredentials() {
        String token = authService.login("admin", "change-me");

        assertThat(jwtService.validate(token)).isEqualTo("admin");
    }

    @Test
    void rejectsInvalidCredentials() {
        assertThatThrownBy(() -> authService.login("admin", "wrong-length-password"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> authService.login("administrator", "change-me"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
