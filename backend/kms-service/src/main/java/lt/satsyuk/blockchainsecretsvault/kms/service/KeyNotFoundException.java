package lt.satsyuk.blockchainsecretsvault.kms.service;

public class KeyNotFoundException extends RuntimeException {
    public KeyNotFoundException(String message) {
        super(message);
    }
}
