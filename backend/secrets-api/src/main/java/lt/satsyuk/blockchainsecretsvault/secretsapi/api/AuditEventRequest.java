package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.AccessAuditAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuditEventRequest(
        @NotNull AccessAuditAction action,
        @Size(max = 1024) String details
) {
}
