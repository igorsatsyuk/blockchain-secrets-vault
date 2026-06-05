package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditWriterTest {

    @Test
    void hashesAuditEventAndPublishesHashOnChain() {
        UUID secretId = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        String account = "0x1111111111111111111111111111111111111111";
        Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
        BlockchainAclClient blockchainAclClient = mock(BlockchainAclClient.class);
        AuditEventHasher hasher = new AuditEventHasher();
        String expectedHash = hasher.hash(secretId, account, AccessAuditAction.WRITE, clock.instant(), "updated");

        when(blockchainAclClient.auditEvent(secretId, account, AccessAuditAction.WRITE, expectedHash))
                .thenReturn("0xtransaction");

        new AuditWriter(blockchainAclClient, hasher, clock)
                .publish(secretId, account, AccessAuditAction.WRITE, "updated");

        verify(blockchainAclClient).auditEvent(secretId, account, AccessAuditAction.WRITE, expectedHash);
    }
}
