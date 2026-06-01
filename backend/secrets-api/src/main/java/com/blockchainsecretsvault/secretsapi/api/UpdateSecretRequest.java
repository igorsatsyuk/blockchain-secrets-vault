package com.blockchainsecretsvault.secretsapi.api;

import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateSecretRequest(
        @Size(max = 128) String name,
        @Size(max = 512) String description,
        @Size(max = 8192) String payload,
        Set<@Size(max = 64) String> tags
) {
    public boolean isEmpty() {
        return name == null && description == null && payload == null && tags == null;
    }
}
