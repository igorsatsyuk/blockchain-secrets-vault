package lt.satsyuk.blockchainsecretsvault.secretsapi.config;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.DisabledBlockchainAclClient;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.Web3jBlockchainAclClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
@EnableConfigurationProperties(BlockchainAclProperties.class)
public class BlockchainAclConfiguration {

    @Bean
    BlockchainAclClient blockchainAclClient(BlockchainAclProperties properties) {
        if (!properties.enabled()) {
            return new DisabledBlockchainAclClient();
        }

        Web3j web3j = Web3j.build(new HttpService(properties.rpcUrl()));
        Credentials credentials = Credentials.create(properties.privateKey());
        return new Web3jBlockchainAclClient(
                web3j,
                credentials,
                properties.contractAddress(),
                properties.gasPrice(),
                properties.gasLimit()
        );
    }
}
