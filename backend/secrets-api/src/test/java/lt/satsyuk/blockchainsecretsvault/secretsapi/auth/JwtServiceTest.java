package lt.satsyuk.blockchainsecretsvault.secretsapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import lt.satsyuk.blockchainsecretsvault.secretsapi.config.AuthProperties;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final AuthProperties properties = new AuthProperties(
            "admin",
            "change-me",
            "test-jwt-secret-with-enough-entropy",
            "vault-tests",
            Duration.ofMinutes(15)
    );
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(properties, new ObjectMapper(), clock);

    @Test
    void issuesAndValidatesSignedTokens() {
        String token = jwtService.issueToken("admin");

        assertThat(token).contains(".");
        assertThat(jwtService.validate(token)).isEqualTo("admin");
    }

    @Test
    void rejectsMalformedTamperedAndExpiredTokens() {
        assertThatThrownBy(() -> jwtService.validate("not-a-token"))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageContaining("Malformed");

        String token = jwtService.issueToken("admin");
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "bad-signature";
        assertThatThrownBy(() -> jwtService.validate(tampered))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageContaining("signature");

        JwtService expiredService = new JwtService(
                new AuthProperties("admin", "change-me", properties.jwtSecret(), properties.issuer(), Duration.ofSeconds(1)),
                new ObjectMapper(),
                clock
        );
        String expiredToken = expiredService.issueToken("admin");
        JwtService validatingLater = new JwtService(
                properties,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-06-01T12:00:02Z"), ZoneOffset.UTC)
        );
        assertThatThrownBy(() -> validatingLater.validate(expiredToken))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void appliesSafeDefaultsForBlankProperties() {
        AuthProperties defaults = new AuthProperties(" ", "", null, " ", null);

        assertThat(defaults.username()).isEqualTo("admin");
        assertThat(defaults.password()).isEqualTo("change-me");
        assertThat(defaults.jwtSecret()).isNotBlank();
        assertThat(defaults.issuer()).isEqualTo("blockchain-secrets-vault");
        assertThat(defaults.tokenTtl()).isEqualTo(Duration.ofHours(1));
    }
}
