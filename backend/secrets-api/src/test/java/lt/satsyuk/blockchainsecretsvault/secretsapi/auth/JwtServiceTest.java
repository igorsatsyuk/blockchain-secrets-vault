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
class JwtServiceTest {
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "change-me";
    private static final String JWT_SECRET = "test-jwt-secret-with-enough-entropy";
    private static final String ISSUER = "issuer";

    private final AuthProperties properties = new AuthProperties(
            USERNAME,
            PASSWORD,
            JWT_SECRET,
            "vault-tests",
            Duration.ofMinutes(15)
    );
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(properties, clock);

    @Test
    void issuesAndValidatesSignedTokens() {
        String token = jwtService.issueToken(USERNAME);

        assertThat(token).contains(".");
        assertThat(jwtService.validate(token)).isEqualTo(USERNAME);
    }

    @Test
    void rejectsMalformedTamperedAndExpiredTokens() {
        assertThatThrownBy(() -> jwtService.validate("not-a-token"))
                .isInstanceOf(JwtAuthenticationException.class);

        String token = jwtService.issueToken(USERNAME);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "bad-signature";
        assertThatThrownBy(() -> jwtService.validate(tampered))
                .isInstanceOf(JwtAuthenticationException.class);

        JwtService expiredService = new JwtService(
                new AuthProperties(USERNAME, PASSWORD, properties.jwtSecret(), properties.issuer(), Duration.ofSeconds(1)),
                clock
        );
        String expiredToken = expiredService.issueToken(USERNAME);
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

        assertThat(defaults.username()).isEqualTo(USERNAME);
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
        return new AuthProperties(USERNAME, " ", JWT_SECRET, ISSUER, Duration.ofMinutes(5));
    }

    private AuthProperties authPropertiesWithoutJwtSecret() {
        return new AuthProperties(USERNAME, "password", " ", ISSUER, Duration.ofMinutes(5));
    }

    private AuthProperties authPropertiesWithShortJwtSecret() {
        return new AuthProperties(USERNAME, "password", "short-secret", ISSUER, Duration.ofMinutes(5));
    }
}
