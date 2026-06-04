package lt.satsyuk.blockchainsecretsvault.secretsapi.config;

import java.math.BigInteger;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("blockchain.acl")
public record BlockchainAclProperties(
        String rpcUrl,
        String contractAddress,
        String privateKey,
        BigInteger gasPrice,
        BigInteger gasLimit,
        Long chainId
) {

    private static final String DEFAULT_RPC_URL = "http://localhost:8545";
    private static final BigInteger DEFAULT_GAS_PRICE = BigInteger.valueOf(20_000_000_000L);
    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(300_000L);
    private static final long DEFAULT_CHAIN_ID = 31_337L;

    public BlockchainAclProperties {
        rpcUrl = normalizeText(rpcUrl, DEFAULT_RPC_URL);
        contractAddress = normalizeText(contractAddress, null);
        privateKey = normalizeText(privateKey, null);
        gasPrice = gasPrice == null ? DEFAULT_GAS_PRICE : gasPrice;
        gasLimit = gasLimit == null ? DEFAULT_GAS_LIMIT : gasLimit;
        chainId = chainId == null ? DEFAULT_CHAIN_ID : chainId;
    }

    public boolean enabled() {
        return hasText(contractAddress) && hasText(privateKey);
    }

    private static String normalizeText(String value, String fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
