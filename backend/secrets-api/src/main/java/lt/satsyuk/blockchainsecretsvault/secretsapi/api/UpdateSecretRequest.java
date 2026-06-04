package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import lt.satsyuk.blockchainsecretsvault.secretsapi.api.validation.NullOrNotBlank;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateSecretRequest(
        @Size(max = 128, message = "size must be between 0 and 128")
        @NullOrNotBlank(message = "must not be blank")
        String name,
        @Size(max = 512, message = "size must be between 0 and 512")
        String description,
        @Size(max = 8192, message = "size must be between 0 and 8192")
        @NullOrNotBlank(message = "must not be blank")
        String payload,
        Set<
                @NotBlank(message = "must not be blank")
                @Size(max = 64, message = "size must be between 0 and 64")
                String> tags
) {
    public boolean isEmpty() {
        return name == null && description == null && payload == null && tags == null;
    }
}

