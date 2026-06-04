package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import java.util.UUID;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessGrant;

public record AccessGrantResponse(
        UUID secretId,
        String account,
        boolean canRead,
        boolean canWrite
) {

    public static AccessGrantResponse from(UUID secretId, AccessGrant grant) {
        return new AccessGrantResponse(secretId, grant.account(), grant.canRead(), grant.canWrite());
    }
}
