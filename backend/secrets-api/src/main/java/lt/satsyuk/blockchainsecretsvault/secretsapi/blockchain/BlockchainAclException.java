package lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain;

public class BlockchainAclException extends RuntimeException {

    public BlockchainAclException(String message) {
        super(message);
    }

    public BlockchainAclException(String message, Throwable cause) {
        super(message, cause);
    }
}
