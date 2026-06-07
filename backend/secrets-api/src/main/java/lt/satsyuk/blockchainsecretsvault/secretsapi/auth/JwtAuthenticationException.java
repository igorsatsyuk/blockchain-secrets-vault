package lt.satsyuk.blockchainsecretsvault.secretsapi.auth;

public class JwtAuthenticationException extends RuntimeException {

    public JwtAuthenticationException(String message) {
        super(message);
    }
}
