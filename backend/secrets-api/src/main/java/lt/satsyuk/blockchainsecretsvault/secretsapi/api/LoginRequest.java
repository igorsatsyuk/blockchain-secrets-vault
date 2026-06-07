package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 128) String username,
        @NotBlank @Size(max = 512) String password
) {
}
