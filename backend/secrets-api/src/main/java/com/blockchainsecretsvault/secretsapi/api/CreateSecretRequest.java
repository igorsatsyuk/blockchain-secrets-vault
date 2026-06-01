package com.blockchainsecretsvault.secretsapi.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateSecretRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @NotBlank @Size(max = 8192) String payload,
        Set<@NotBlank @Size(max = 64) String> tags
) {
}
