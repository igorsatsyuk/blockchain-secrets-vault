package lt.satsyuk.blockchainsecretsvault.kms.service;

public class EncryptionFailedException extends RuntimeException {
    public EncryptionFailedException(String message) {
        super(message);
    }

    public EncryptionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
