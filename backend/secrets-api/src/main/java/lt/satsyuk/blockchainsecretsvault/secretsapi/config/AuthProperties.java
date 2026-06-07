package lt.satsyuk.blockchainsecretsvault.secretsapi.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("secrets.auth")
public record AuthProperties(
        String username,
        String password,
        String jwtSecret,
        String issuer,
        Duration tokenTtl
) {
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_ISSUER = "blockchain-secrets-vault";
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(1);
    private static final int MINIMUM_JWT_SECRET_BYTES = 32;

    public AuthProperties {
        username = hasText(username) ? username.trim() : DEFAULT_USERNAME;
        if (!hasText(password)) {
            throw new IllegalArgumentException("secrets.auth.password must be configured");
        }
        if (!hasText(jwtSecret)) {
            throw new IllegalArgumentException("secrets.auth.jwt-secret must be configured");
        }
        jwtSecret = jwtSecret.trim();
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_JWT_SECRET_BYTES) {
            throw new IllegalArgumentException("secrets.auth.jwt-secret must be at least 32 bytes");
        }
        issuer = hasText(issuer) ? issuer.trim() : DEFAULT_ISSUER;
        tokenTtl = tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()
                ? DEFAULT_TOKEN_TTL
                : tokenTtl;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
