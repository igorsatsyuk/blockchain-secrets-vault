package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AuditWriter {

    static final int DEFAULT_HISTORY_LIMIT = 1_000;

    private final BlockchainAclClient blockchainAclClient;
    private final AuditEventHasher auditEventHasher;
    private final Clock clock;
    private final Map<UUID, ArrayDeque<AuditEventRecord>> eventsBySecret = new HashMap<>();
    private final int historyLimit;

    public AuditWriter(BlockchainAclClient blockchainAclClient, AuditEventHasher auditEventHasher, Clock clock) {
        this(blockchainAclClient, auditEventHasher, clock, DEFAULT_HISTORY_LIMIT);
    }

    AuditWriter(
            BlockchainAclClient blockchainAclClient,
            AuditEventHasher auditEventHasher,
            Clock clock,
            int historyLimit
    ) {
        this.blockchainAclClient = blockchainAclClient;
        this.auditEventHasher = auditEventHasher;
        this.clock = clock;
        this.historyLimit = Math.max(1, historyLimit);
    }

    public void publish(UUID secretId, String account, AccessAuditAction action, String details) {
        Instant occurredAt = Instant.now(clock);
        String detailsHash = auditEventHasher.hash(secretId, account, action, occurredAt, details);
        String transactionHash = blockchainAclClient.auditEvent(secretId, account, action, detailsHash);
        synchronized (eventsBySecret) {
            ArrayDeque<AuditEventRecord> events = eventsBySecret.computeIfAbsent(secretId, _ -> new ArrayDeque<>());
            events.addLast(new AuditEventRecord(secretId, account, action, occurredAt, detailsHash, transactionHash));
            while (events.size() > historyLimit) {
                events.removeFirst();
            }
        }
    }

    public List<AuditEventRecord> history(UUID secretId, Optional<AccessAuditAction> action, Optional<String> account) {
        String accountFilter = account.map(value -> value.toLowerCase(Locale.ROOT)).orElse("");
        synchronized (eventsBySecret) {
            return eventsBySecret.getOrDefault(secretId, new ArrayDeque<>()).stream()
                    .filter(event -> action.isEmpty() || event.action() == action.get())
                    .filter(event -> accountFilter.isBlank() || event.account().toLowerCase(Locale.ROOT).contains(accountFilter))
                    .toList();
        }
    }
}
