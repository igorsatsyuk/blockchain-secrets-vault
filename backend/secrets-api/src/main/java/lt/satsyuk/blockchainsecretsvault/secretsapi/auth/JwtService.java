package lt.satsyuk.blockchainsecretsvault.secretsapi.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lt.satsyuk.blockchainsecretsvault.secretsapi.config.AuthProperties;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {};
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final AuthProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JwtService(AuthProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String issueToken(String subject) {
        Instant now = Instant.now(clock);
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> claims = Map.of(
                "iss", properties.issuer(),
                "sub", subject,
                "iat", now.getEpochSecond(),
                "exp", now.plus(properties.tokenTtl()).getEpochSecond()
        );
        String unsignedToken = "%s.%s".formatted(encodeJson(header), encodeJson(claims));
        return "%s.%s".formatted(unsignedToken, sign(unsignedToken));
    }

    public String validate(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new JwtAuthenticationException("Malformed bearer token");
        }

        String unsignedToken = "%s.%s".formatted(parts[0], parts[1]);
        if (!MessageDigest.isEqual(sign(unsignedToken).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new JwtAuthenticationException("Invalid bearer token signature");
        }

        Map<String, Object> claims = decodeJson(parts[1]);
        String issuer = asString(claims.get("iss"));
        String subject = asString(claims.get("sub"));
        long expiresAt = asLong(claims.get("exp"));
        if (!properties.issuer().equals(issuer) || subject.isBlank()) {
            throw new JwtAuthenticationException("Invalid bearer token claims");
        }
        if (Instant.now(clock).getEpochSecond() >= expiresAt) {
            throw new JwtAuthenticationException("Bearer token has expired");
        }
        return subject;
    }

    private String encodeJson(Map<String, Object> payload) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize JWT payload", exception);
        }
    }

    private Map<String, Object> decodeJson(String value) {
        try {
            return objectMapper.readValue(BASE64_URL_DECODER.decode(value), CLAIMS_TYPE);
        } catch (IllegalArgumentException | IOException exception) {
            throw new JwtAuthenticationException("Malformed bearer token payload");
        }
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to sign JWT", exception);
        }
    }

    private static String asString(Object value) {
        return value instanceof String string ? string : "";
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new JwtAuthenticationException("Invalid bearer token claims");
    }
}
