package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventHasherTest {

    private static final UUID SECRET_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private static final String ACCOUNT = "0x1111111111111111111111111111111111111111";
    private final AuditEventHasher hasher = new AuditEventHasher();

    @Test
    void createsStableBytes32HashFromCanonicalAuditEvent() {
        Instant occurredAt = Instant.parse("2026-06-01T12:00:00Z");

        String hash = hasher.hash(SECRET_ID, ACCOUNT, AccessAuditAction.READ, occurredAt, " ok ");

        assertThat(hash)
                .isEqualTo(hasher.hash(SECRET_ID, ACCOUNT, AccessAuditAction.READ, occurredAt, "ok"))
                .startsWith("0x")
                .hasSize(66);
    }

    @Test
    void normalizesAccountBeforeHashingCanonicalAuditEvent() {
        Instant occurredAt = Instant.parse("2026-06-01T12:00:00Z");
        String mixedCaseAccount = "  0x1111111111111111111111111111111111111111  ".toUpperCase();

        assertThat(hasher.hash(SECRET_ID, mixedCaseAccount, AccessAuditAction.READ, occurredAt, "ok"))
                .isEqualTo(hasher.hash(SECRET_ID, ACCOUNT, AccessAuditAction.READ, occurredAt, "ok"));
    }

    @Test
    void hashChangesWhenImportantEventFieldsChange() {
        Instant occurredAt = Instant.parse("2026-06-01T12:00:00Z");
        String readHash = hasher.hash(SECRET_ID, ACCOUNT, AccessAuditAction.READ, occurredAt, null);
        String writeHash = hasher.hash(SECRET_ID, ACCOUNT, AccessAuditAction.WRITE, occurredAt, null);

        assertThat(readHash).isNotEqualTo(writeHash);
    }
}
