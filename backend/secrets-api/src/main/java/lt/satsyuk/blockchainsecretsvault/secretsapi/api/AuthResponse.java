package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresIn
) {
}
