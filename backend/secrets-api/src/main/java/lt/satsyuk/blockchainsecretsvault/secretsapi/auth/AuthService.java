package lt.satsyuk.blockchainsecretsvault.secretsapi.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lt.satsyuk.blockchainsecretsvault.secretsapi.config.AuthProperties;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthProperties properties;
    private final JwtService jwtService;

    public AuthService(AuthProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    public String login(String username, String password) {
        if (!constantTimeEquals(properties.username(), username) || !constantTimeEquals(properties.password(), password)) {
            throw new InvalidCredentialsException();
        }
        return jwtService.issueToken(username);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(sha256(expected), sha256(String.valueOf(actual)));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }
}
