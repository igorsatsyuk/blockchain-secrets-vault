package lt.satsyuk.blockchainsecretsvault.secretsapi.config;

import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.DisabledBlockchainAclClient;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.Web3jBlockchainAclClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
@EnableConfigurationProperties(BlockchainAclProperties.class)
public class BlockchainAclConfiguration {

    @Bean(destroyMethod = "shutdown")
    @Conditional(BlockchainAclEnabledCondition.class)
    Web3j web3j(BlockchainAclProperties properties) {
        return Web3j.build(new HttpService(properties.rpcUrl()));
    }

    @Bean
    BlockchainAclClient blockchainAclClient(BlockchainAclProperties properties, ObjectProvider<Web3j> web3j) {
        if (!properties.enabled()) {
            return new DisabledBlockchainAclClient();
        }

        Credentials credentials = Credentials.create(properties.privateKey());
        return new Web3jBlockchainAclClient(
                web3j.getObject(),
                credentials,
                properties.contractAddress(),
                properties.gasPrice(),
                properties.gasLimit()
        );
    }

    static final class BlockchainAclEnabledCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return Binder.get(context.getEnvironment())
                    .bind("blockchain.acl", BlockchainAclProperties.class)
                    .map(BlockchainAclProperties::enabled)
                    .orElse(false);
        }
    }
}
