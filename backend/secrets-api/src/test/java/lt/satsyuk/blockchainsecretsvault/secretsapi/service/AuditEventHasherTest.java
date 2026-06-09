package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventHasherTest {

    private static final UUID SECRET_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private static final String ACCOUNT = "0x1111111111111111111111111111111111111111";
    private static final Instant OCCURRED_AT = Instant.parse("2026-06-01T12:00:00Z");
    private final AuditEventHasher hasher = new AuditEventHasher();

    @Test
    void createsStableBytes32HashFromCanonicalAuditEvent() {
        String hash = hasher.hash(SECRET_ID, ACCOUNT, AccessAuditAction.READ, OCCURRED_AT, " ok ");

        assertThat(hash)
                .isEqualTo(hasher.hash(SECRET_ID, ACCOUNT, AccessAuditAction.READ, OCCURRED_AT, "ok"))
                .startsWith("0x")
                .hasSize(66);
    }

    @Test
    void normalizesAccountBeforeHashingCanonicalAuditEvent() {
        String mixedCaseAccount = "  0x1111111111111111111111111111111111111111  ".toUpperCase();

        assertThat(hasher.hash(SECRET_ID, mixedCaseAccount, AccessAuditAction.READ, OCCURRED_AT, "ok"))
                .isEqualTo(hasher.hash(SECRET_ID, ACCOUNT, AccessAuditAction.READ, OCCURRED_AT, "ok"));
    }

    @Test
    void hashChangesWhenImportantEventFieldsChange() {
        String readHash = hasher.hash(SECRET_ID, ACCOUNT, AccessAuditAction.READ, OCCURRED_AT, null);
        String writeHash = hasher.hash(SECRET_ID, ACCOUNT, AccessAuditAction.WRITE, OCCURRED_AT, null);

        assertThat(readHash).isNotEqualTo(writeHash);
    }
}
