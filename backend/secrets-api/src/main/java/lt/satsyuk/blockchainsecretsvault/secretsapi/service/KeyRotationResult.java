package lt.satsyuk.blockchainsecretsvault.secretsapi.service;

public record KeyRotationResult(
        String keyId,
        int previousKeyVersion,
        int newKeyVersion,
        int reEncryptedSecrets
) {
}
