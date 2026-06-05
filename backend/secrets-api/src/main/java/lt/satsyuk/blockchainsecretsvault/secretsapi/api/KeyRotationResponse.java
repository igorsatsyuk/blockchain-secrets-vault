package lt.satsyuk.blockchainsecretsvault.secretsapi.api;

import lt.satsyuk.blockchainsecretsvault.secretsapi.service.KeyRotationResult;

public record KeyRotationResponse(
        String keyId,
        int previousKeyVersion,
        int newKeyVersion,
        int reEncryptedSecrets
) {
    public static KeyRotationResponse from(KeyRotationResult result) {
        return new KeyRotationResponse(
                result.keyId(),
                result.previousKeyVersion(),
                result.newKeyVersion(),
                result.reEncryptedSecrets()
        );
    }
}
