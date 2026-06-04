package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

public class InvalidBlockchainAccountException extends RuntimeException {

    public InvalidBlockchainAccountException(String account) {
        super("Invalid blockchain account address: " + account);
    }
}
