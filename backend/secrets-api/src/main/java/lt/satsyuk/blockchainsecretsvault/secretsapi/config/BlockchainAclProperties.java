package lt.satsyuk.blockchainsecretsvault.secretsapi.config;

import java.math.BigInteger;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("blockchain.acl")
public record BlockchainAclProperties(
        String rpcUrl,
        String contractAddress,
        String privateKey,
        BigInteger gasPrice,
        BigInteger gasLimit
) {

    public BlockchainAclProperties {
        rpcUrl = hasText(rpcUrl) ? rpcUrl : "http://localhost:8545";
        gasPrice = gasPrice == null ? BigInteger.valueOf(20_000_000_000L) : gasPrice;
        gasLimit = gasLimit == null ? BigInteger.valueOf(300_000L) : gasLimit;
    }

    public boolean enabled() {
        return hasText(contractAddress) && hasText(privateKey);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
