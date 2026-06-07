package lt.satsyuk.blockchainsecretsvault.secretsapi.auth;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
