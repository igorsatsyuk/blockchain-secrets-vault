package lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain;

import java.util.UUID;

public interface BlockchainAclClient {

    String grantAccess(UUID secretId, String account, boolean canRead, boolean canWrite);

    String revokeAccess(UUID secretId, String account);

    String auditEvent(UUID secretId, String account, AccessAuditAction action, String detailsHash);

    boolean canRead(UUID secretId, String account);

    boolean canWrite(UUID secretId, String account);
}
