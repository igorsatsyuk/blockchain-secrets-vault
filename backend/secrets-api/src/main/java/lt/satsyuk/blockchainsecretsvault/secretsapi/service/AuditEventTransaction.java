package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import java.time.Instant;

public record AuditEventTransaction(
        String account,
        AccessAuditAction action,
        Instant occurredAt,
        String detailsHash,
        String transactionHash
) {
}
