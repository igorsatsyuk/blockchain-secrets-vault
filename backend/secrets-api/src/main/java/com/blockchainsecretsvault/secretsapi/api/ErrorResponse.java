package com.blockchainsecretsvault.secretsapi.api;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> details
) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(Instant.now(), status, error, message, Map.of());
    }

    public static ErrorResponse withDetails(
            int status,
            String error,
            String message,
            Map<String, String> details
    ) {
        return new ErrorResponse(Instant.now(), status, error, message, details);
    }
}
