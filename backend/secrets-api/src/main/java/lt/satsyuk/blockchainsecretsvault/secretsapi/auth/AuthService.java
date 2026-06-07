package lt.satsyuk.blockchainsecretsvault.secretsapi.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = String.valueOf(actual).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
