package lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain;

import java.util.UUID;

public class DisabledBlockchainAclClient implements BlockchainAclClient {

    private static final String MESSAGE = "Blockchain ACL adapter is not configured";

    @Override
    public String grantAccess(UUID secretId, String account, boolean canRead, boolean canWrite) {
        throw new BlockchainAclException(MESSAGE);
    }

    @Override
    public String revokeAccess(UUID secretId, String account) {
        throw new BlockchainAclException(MESSAGE);
    }

    @Override
    public boolean canRead(UUID secretId, String account) {
        throw new BlockchainAclException(MESSAGE);
    }

    @Override
    public boolean canWrite(UUID secretId, String account) {
        throw new BlockchainAclException(MESSAGE);
    }
}
