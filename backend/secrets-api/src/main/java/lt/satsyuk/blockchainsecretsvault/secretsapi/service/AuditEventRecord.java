package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import java.time.Instant;
import java.util.UUID;

public record AuditEventRecord(
        UUID secretId,
        String account,
        AccessAuditAction action,
        Instant occurredAt,
        String detailsHash,
        String transactionHash
) {
}
