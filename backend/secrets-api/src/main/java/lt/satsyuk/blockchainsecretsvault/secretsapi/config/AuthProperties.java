package lt.satsyuk.blockchainsecretsvault.secretsapi.config;

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
    private static final String DEFAULT_PASSWORD = "change-me";
    private static final String DEFAULT_JWT_SECRET = "local-dev-jwt-secret-change-before-production";
    private static final String DEFAULT_ISSUER = "blockchain-secrets-vault";
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(1);

    public AuthProperties {
        username = hasText(username) ? username.trim() : DEFAULT_USERNAME;
        password = hasText(password) ? password : DEFAULT_PASSWORD;
        jwtSecret = hasText(jwtSecret) ? jwtSecret : DEFAULT_JWT_SECRET;
        issuer = hasText(issuer) ? issuer.trim() : DEFAULT_ISSUER;
        tokenTtl = tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()
                ? DEFAULT_TOKEN_TTL
                : tokenTtl;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
