package lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain;

public record AccessGrant(String account, boolean canRead, boolean canWrite) {
}
