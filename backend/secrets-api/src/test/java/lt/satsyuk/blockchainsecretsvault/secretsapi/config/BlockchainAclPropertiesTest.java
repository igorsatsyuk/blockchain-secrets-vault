package lt.satsyuk.blockchainsecretsvault.secretsapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigInteger;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclClient;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.BlockchainAclException;
import lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain.DisabledBlockchainAclClient;
import org.springframework.beans.factory.ObjectProvider;
import org.web3j.protocol.Web3j;
import org.junit.jupiter.api.Test;

class BlockchainAclPropertiesTest {

    @Test
    void appliesDefaultsAndDisablesClientWhenContractSettingsAreMissing() {
        BlockchainAclProperties properties = new BlockchainAclProperties(null, null, null, null, null, null);

        assertThat(properties.rpcUrl()).isEqualTo("http://localhost:8545");
        assertThat(properties.gasPrice()).isEqualTo(BigInteger.valueOf(20_000_000_000L));
        assertThat(properties.gasLimit()).isEqualTo(BigInteger.valueOf(300_000L));
        assertThat(properties.chainId()).isEqualTo(31_337L);
        assertThat(properties.enabled()).isFalse();
    }

    @Test
    void enablesClientWhenContractAddressAndPrivateKeyArePresent() {
        BlockchainAclProperties properties = new BlockchainAclProperties(
                "  http://node:8545  ",
                "  0x1111111111111111111111111111111111111111  ",
                "  private-key  ",
                BigInteger.ONE,
                BigInteger.TEN,
                1L
        );

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.rpcUrl()).isEqualTo("http://node:8545");
        assertThat(properties.contractAddress()).isEqualTo("0x1111111111111111111111111111111111111111");
        assertThat(properties.privateKey()).isEqualTo("private-key");
        assertThat(properties.gasPrice()).isEqualTo(BigInteger.ONE);
        assertThat(properties.gasLimit()).isEqualTo(BigInteger.TEN);
        assertThat(properties.chainId()).isEqualTo(1L);
    }

    @Test
    void createsDisabledClientWhenContractSettingsAreMissing() {
        BlockchainAclConfiguration configuration = new BlockchainAclConfiguration();
        ObjectProvider<Web3j> web3j = mockWeb3jProvider();

        BlockchainAclClient client = configuration.blockchainAclClient(
                new BlockchainAclProperties(null, null, null, null, null, null),
                web3j
        );

        assertThat(client).isInstanceOf(DisabledBlockchainAclClient.class);
        assertThatThrownBy(() -> client.canRead(null, "0x1111111111111111111111111111111111111111"))
                .isInstanceOf(BlockchainAclException.class)
                .hasMessage("Blockchain ACL adapter is not configured");
        verifyNoInteractions(web3j);
    }

    private static ObjectProvider<Web3j> mockWeb3jProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<Web3j> provider = mock(ObjectProvider.class);
        return provider;
    }
}
