package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import lt.satsyuk.blockchainsecretsvault.secretsapi.service.AuditEventTransaction;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID secretId,
        String account,
        AccessAuditAction action,
        Instant occurredAt,
        String detailsHash,
        String transactionHash
) {

    public static AuditEventResponse from(UUID secretId, AuditEventTransaction transaction) {
        return new AuditEventResponse(
                secretId,
                transaction.account(),
                transaction.action(),
                transaction.occurredAt(),
                transaction.detailsHash(),
                transaction.transactionHash()
        );
    }
}
