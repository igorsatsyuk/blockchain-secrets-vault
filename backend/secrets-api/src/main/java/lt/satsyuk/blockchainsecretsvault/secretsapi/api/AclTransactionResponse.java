package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import java.util.UUID;

public record AclTransactionResponse(
        UUID secretId,
        String account,
        String transactionHash
) {
}
