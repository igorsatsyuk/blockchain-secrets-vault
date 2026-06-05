package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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

        AuditWriter writer = new AuditWriter(blockchainAclClient, hasher, clock);

        writer.publish(secretId, account, AccessAuditAction.WRITE, "updated");

        verify(blockchainAclClient).auditEvent(secretId, account, AccessAuditAction.WRITE, expectedHash);
        assertThat(writer.history(secretId, Optional.of(AccessAuditAction.WRITE), Optional.of("1111")))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.account()).isEqualTo(account);
                    assertThat(event.occurredAt()).isEqualTo(clock.instant());
                    assertThat(event.detailsHash()).isEqualTo(expectedHash);
                    assertThat(event.transactionHash()).isEqualTo("0xtransaction");
                });
        assertThat(writer.history(secretId, Optional.of(AccessAuditAction.READ), Optional.empty())).isEmpty();
    }
}
