package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import lt.satsyuk.blockchainsecretsvault.secretsapi.service.AuditEventRecord;
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

    static AuditEventResponse from(AuditEventRecord event) {
        return new AuditEventResponse(
                event.secretId(),
                event.account(),
                event.action(),
                event.occurredAt(),
                event.detailsHash(),
                event.transactionHash()
        );
    }
}
