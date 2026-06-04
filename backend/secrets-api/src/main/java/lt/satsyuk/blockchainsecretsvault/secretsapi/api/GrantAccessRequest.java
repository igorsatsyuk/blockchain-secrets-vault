package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import jakarta.validation.constraints.NotNull;

public record GrantAccessRequest(
        @NotNull Boolean canRead,
        @NotNull Boolean canWrite
) {
}
