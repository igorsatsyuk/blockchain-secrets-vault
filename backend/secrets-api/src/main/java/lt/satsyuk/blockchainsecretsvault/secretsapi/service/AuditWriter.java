package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;

public class AuditWriter {

    private final BlockchainAclClient blockchainAclClient;
    private final AuditEventHasher auditEventHasher;
    private final Clock clock;
    private final CopyOnWriteArrayList<AuditEventRecord> events = new CopyOnWriteArrayList<>();

    public AuditWriter(BlockchainAclClient blockchainAclClient, AuditEventHasher auditEventHasher, Clock clock) {
        this.blockchainAclClient = blockchainAclClient;
        this.auditEventHasher = auditEventHasher;
        this.clock = clock;
    }

    public void publish(UUID secretId, String account, AccessAuditAction action, String details) {
        Instant occurredAt = Instant.now(clock);
        String detailsHash = auditEventHasher.hash(secretId, account, action, occurredAt, details);
        String transactionHash = blockchainAclClient.auditEvent(secretId, account, action, detailsHash);
        events.add(new AuditEventRecord(secretId, account, action, occurredAt, detailsHash, transactionHash));
    }

    public List<AuditEventRecord> history(UUID secretId, Optional<AccessAuditAction> action, Optional<String> account) {
        String accountFilter = account.map(value -> value.toLowerCase(Locale.ROOT)).orElse("");
        return events.stream()
                .filter(event -> event.secretId().equals(secretId))
                .filter(event -> action.isEmpty() || event.action() == action.get())
                .filter(event -> accountFilter.isBlank() || event.account().toLowerCase(Locale.ROOT).contains(accountFilter))
                .toList();
    }
}
