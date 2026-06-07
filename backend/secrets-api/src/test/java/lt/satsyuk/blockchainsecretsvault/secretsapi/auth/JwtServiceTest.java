package lt.satsyuk.blockchainsecretsvault.secretsapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import lt.satsyuk.blockchainsecretsvault.secretsapi.config.AuthProperties;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String JWT_SECRET = "test-jwt-secret-with-enough-entropy";

    private final AuthProperties properties = new AuthProperties(
            "admin",
            "change-me",
            JWT_SECRET,
            "vault-tests",
            Duration.ofMinutes(15)
    );
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(properties, clock);

    @Test
    void issuesAndValidatesSignedTokens() {
        String token = jwtService.issueToken("admin");

        assertThat(token).contains(".");
        assertThat(jwtService.validate(token)).isEqualTo("admin");
    }

    @Test
    void rejectsMalformedTamperedAndExpiredTokens() {
        assertThatThrownBy(() -> jwtService.validate("not-a-token"))
                .isInstanceOf(JwtAuthenticationException.class);

        String token = jwtService.issueToken("admin");
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "bad-signature";
        assertThatThrownBy(() -> jwtService.validate(tampered))
                .isInstanceOf(JwtAuthenticationException.class);

        JwtService expiredService = new JwtService(
                new AuthProperties("admin", "change-me", properties.jwtSecret(), properties.issuer(), Duration.ofSeconds(1)),
                clock
        );
        String expiredToken = expiredService.issueToken("admin");
        JwtService validatingLater = new JwtService(
                properties,
                Clock.fixed(Instant.parse("2026-06-01T12:00:02Z"), ZoneOffset.UTC)
        );
        assertThatThrownBy(() -> validatingLater.validate(expiredToken))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void appliesSafeDefaultsAndRequiresSecrets() {
        AuthProperties defaults = new AuthProperties(
                " ",
                "configured-password",
                " configured-jwt-secret-with-32-bytes ",
                " ",
                null
        );

        assertThat(defaults.username()).isEqualTo("admin");
        assertThat(defaults.password()).isEqualTo("configured-password");
        assertThat(defaults.jwtSecret()).isEqualTo("configured-jwt-secret-with-32-bytes");
        assertThat(defaults.issuer()).isEqualTo("blockchain-secrets-vault");
        assertThat(defaults.tokenTtl()).isEqualTo(Duration.ofHours(1));
        assertThatThrownBy(this::authPropertiesWithoutPassword)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("secrets.auth.password must be configured");
        assertThatThrownBy(this::authPropertiesWithoutJwtSecret)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("secrets.auth.jwt-secret must be configured");
        assertThatThrownBy(this::authPropertiesWithShortJwtSecret)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("secrets.auth.jwt-secret must be at least 32 bytes");
    }

    private AuthProperties authPropertiesWithoutPassword() {
        return new AuthProperties("admin", " ", JWT_SECRET, "issuer", Duration.ofMinutes(5));
    }

    private AuthProperties authPropertiesWithoutJwtSecret() {
        return new AuthProperties("admin", "password", " ", "issuer", Duration.ofMinutes(5));
    }

    private AuthProperties authPropertiesWithShortJwtSecret() {
        return new AuthProperties("admin", "password", "short-secret", "issuer", Duration.ofMinutes(5));
    }
}
