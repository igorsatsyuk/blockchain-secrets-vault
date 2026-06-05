package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

public class AuditEventHasher {

    public String hash(UUID secretId, String account, AccessAuditAction action, Instant occurredAt, String details) {
        String payload = String.join("\n",
                "blockchain-secrets-vault.audit.v1",
                "secretId=" + secretId,
                "account=" + normalizeAccount(account),
                "action=" + action.name(),
                "occurredAt=" + occurredAt,
                "details=" + normalizeDetails(details)
        );
        return Numeric.toHexString(Hash.sha3(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static String normalizeDetails(String details) {
        if (details == null) {
            return "";
        }
        return details.trim();
    }

    private static String normalizeAccount(String account) {
        if (account == null) {
            return "";
        }
        return account.trim().toLowerCase(Locale.ROOT);
    }
}
