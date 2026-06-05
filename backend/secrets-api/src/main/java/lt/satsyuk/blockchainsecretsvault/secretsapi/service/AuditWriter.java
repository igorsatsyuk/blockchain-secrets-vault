package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class AuditWriter {

    private final BlockchainAclClient blockchainAclClient;
    private final AuditEventHasher auditEventHasher;
    private final Clock clock;

    public AuditWriter(BlockchainAclClient blockchainAclClient, AuditEventHasher auditEventHasher, Clock clock) {
        this.blockchainAclClient = blockchainAclClient;
        this.auditEventHasher = auditEventHasher;
        this.clock = clock;
    }

    public AuditEventTransaction publish(UUID secretId, String account, AccessAuditAction action, String details) {
        Instant occurredAt = Instant.now(clock);
        String detailsHash = auditEventHasher.hash(secretId, account, action, occurredAt, details);
        String transactionHash = blockchainAclClient.auditEvent(secretId, account, action, detailsHash);
        return new AuditEventTransaction(account, action, occurredAt, detailsHash, transactionHash);
    }
}
