package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
