package lt.satsyuk.blockchainsecretsvault.secretsapi.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import lt.satsyuk.blockchainsecretsvault.secretsapi.config.AuthProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String INVALID_TOKEN_ERROR_CODE = "invalid_token";

    private final AuthProperties properties;
    private final Clock clock;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        SecretKeySpec secretKey = new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(jwtValidator());
        this.jwtDecoder = decoder;
    }

    public String issueToken(String subject) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(properties.tokenTtl()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public String validate(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new JwtAuthenticationException("Invalid bearer token claims");
            }
            return subject;
        } catch (BadJwtException exception) {
            throw new JwtAuthenticationException(exception.getMessage());
        } catch (JwtException _) {
            throw new JwtAuthenticationException("Invalid bearer token");
        } catch (IllegalArgumentException _) {
            throw new JwtAuthenticationException("Invalid bearer token claims");
        }
    }

    private OAuth2TokenValidator<Jwt> jwtValidator() {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(properties.issuer()),
                this::validateTimeClaims,
                jwt -> jwt.getSubject() == null || jwt.getSubject().isBlank()
                        ? OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                INVALID_TOKEN_ERROR_CODE,
                                "Bearer token subject is required",
                                null
                        ))
                        : OAuth2TokenValidatorResult.success()
        );
    }

    private OAuth2TokenValidatorResult validateTimeClaims(Jwt jwt) {
        Instant now = Instant.now(clock);
        Instant expiresAt = jwt.getExpiresAt();
        Instant notBefore = jwt.getNotBefore();
        List<OAuth2Error> errors = new ArrayList<>();
        if (expiresAt == null || !now.isBefore(expiresAt)) {
            errors.add(new OAuth2Error(INVALID_TOKEN_ERROR_CODE, "Bearer token has expired", null));
        }
        if (notBefore != null && now.isBefore(notBefore)) {
            errors.add(new OAuth2Error(INVALID_TOKEN_ERROR_CODE, "Bearer token is not active yet", null));
        }
        return errors.isEmpty()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(errors.toArray(OAuth2Error[]::new));
    }
}
