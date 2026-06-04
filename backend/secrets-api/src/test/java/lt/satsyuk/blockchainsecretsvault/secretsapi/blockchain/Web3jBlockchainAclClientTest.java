package lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Bool;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;

class Web3jBlockchainAclClientTest {

    private static final UUID SECRET_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private static final String ACCOUNT = "0x1111111111111111111111111111111111111111";
    private static final String CONTRACT = "0x2222222222222222222222222222222222222222";
    private static final BigInteger GAS_PRICE = BigInteger.valueOf(10);
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(20);
    private static final long CHAIN_ID = 31_337L;
    private static final Credentials CREDENTIALS = Credentials.create(
            "0000000000000000000000000000000000000000000000000000000000000001"
    );

    @Test
    void createsChainIdAwareTransactionManager() {
        Web3j web3j = mock(Web3j.class);
        AtomicReference<List<?>> constructorArguments = new AtomicReference<>();

        try (var _ = mockConstruction(
                RawTransactionManager.class,
                (_, context) -> constructorArguments.set(context.arguments())
        )) {
            new Web3jBlockchainAclClient(web3j, CREDENTIALS, CONTRACT, GAS_PRICE, GAS_LIMIT, CHAIN_ID);

            assertThat(constructorArguments.get()).hasSize(3);
            assertThat(constructorArguments.get().get(0)).isSameAs(web3j);
            assertThat(constructorArguments.get().get(1)).isSameAs(CREDENTIALS);
            assertThat(constructorArguments.get().get(2)).isEqualTo(CHAIN_ID);
        }
    }

    @Test
    void submitsGrantAndRevokeTransactions() {
        Web3jBlockchainAclClient client = clientWithTransactionSender(successfulTransactionSender());

        assertThat(client.grantAccess(SECRET_ID, ACCOUNT, true, false)).isEqualTo("0xtransaction");
        assertThat(client.revokeAccess(SECRET_ID, ACCOUNT)).isEqualTo("0xtransaction");
    }

    @Test
    void mapsTransactionErrorsAndIoFailuresToAclException() {
        Web3jBlockchainAclClient errorClient = clientWithTransactionSender((_, _, _, _, _) -> {
            EthSendTransaction response = new EthSendTransaction();
            response.setError(new Response.Error(3, "contract reverted"));
            return response;
        });

        assertThatThrownBy(() -> errorClient.grantAccess(SECRET_ID, ACCOUNT, true, true))
                .isInstanceOf(BlockchainAclException.class)
                .hasMessage("contract reverted");

        Web3jBlockchainAclClient ioClient = clientWithTransactionSender((_, _, _, _, _) -> {
            throw new IOException("node unavailable");
        });

        assertThatThrownBy(() -> ioClient.revokeAccess(SECRET_ID, ACCOUNT))
                .isInstanceOf(BlockchainAclException.class)
                .hasMessage("Failed to submit ACL transaction")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void rejectsTransactionResponsesWithoutHash() {
        Web3jBlockchainAclClient client = clientWithTransactionSender((_, _, _, _, _) -> new EthSendTransaction());

        assertThatThrownBy(() -> client.grantAccess(SECRET_ID, ACCOUNT, true, true))
                .isInstanceOf(BlockchainAclException.class)
                .hasMessage("ACL transaction response did not include a transaction hash");
    }

    @Test
    void decodesReadAndWriteAccessChecks() throws IOException {
        Web3j web3j = web3jReturning("0x" + FunctionEncoder.encodeConstructor(List.of(new Bool(true))));
        Web3jBlockchainAclClient client = clientWithWeb3j(web3j);

        assertThat(client.canRead(SECRET_ID, ACCOUNT)).isTrue();
        assertThat(client.canWrite(SECRET_ID, ACCOUNT)).isTrue();
    }

    @Test
    void mapsCallErrorsEmptyResponsesAndIoFailuresToAclException() throws IOException {
        Web3j errorWeb3j = web3jReturningError("call reverted");
        Web3jBlockchainAclClient errorClient = clientWithWeb3j(errorWeb3j);

        assertThatThrownBy(() -> errorClient.canRead(SECRET_ID, ACCOUNT))
                .isInstanceOf(BlockchainAclException.class)
                .hasMessage("call reverted");

        Web3j emptyWeb3j = web3jReturning("0x");
        Web3jBlockchainAclClient emptyClient = clientWithWeb3j(emptyWeb3j);

        assertThatThrownBy(() -> emptyClient.canWrite(SECRET_ID, ACCOUNT))
                .isInstanceOf(BlockchainAclException.class)
                .hasMessage("ACL contract returned no boolean value");

        Web3j blankWeb3j = web3jReturning(" ");
        Web3jBlockchainAclClient blankClient = clientWithWeb3j(blankWeb3j);

        assertThatThrownBy(() -> blankClient.canWrite(SECRET_ID, ACCOUNT))
                .isInstanceOf(BlockchainAclException.class)
                .hasMessage("ACL contract returned no boolean value");

        Web3j ioWeb3j = web3jThrowing(new IOException("node unavailable"));
        Web3jBlockchainAclClient ioClient = clientWithWeb3j(ioWeb3j);

        assertThatThrownBy(() -> ioClient.canRead(SECRET_ID, ACCOUNT))
                .isInstanceOf(BlockchainAclException.class)
                .hasMessage("Failed to call ACL contract")
                .hasCauseInstanceOf(IOException.class);
    }

    private static Web3jBlockchainAclClient clientWithTransactionSender(
            Web3jBlockchainAclClient.TransactionSender transactionSender
    ) {
        return new Web3jBlockchainAclClient(
                mock(Web3j.class),
                CREDENTIALS,
                transactionSender,
                CONTRACT,
                GAS_PRICE,
                GAS_LIMIT
        );
    }

    private static Web3jBlockchainAclClient clientWithWeb3j(Web3j web3j) {
        return new Web3jBlockchainAclClient(
                web3j,
                CREDENTIALS,
                successfulTransactionSender(),
                CONTRACT,
                GAS_PRICE,
                GAS_LIMIT
        );
    }

    private static Web3jBlockchainAclClient.TransactionSender successfulTransactionSender() {
        return (_, _, _, _, _) -> {
            EthSendTransaction response = new EthSendTransaction();
            response.setResult("0xtransaction");
            return response;
        };
    }

    @SuppressWarnings("unchecked")
    private static Web3j web3jReturning(String value) throws IOException {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> request = mock(Request.class);
        EthCall response = new EthCall();
        response.setResult(value);
        when(request.send()).thenReturn(response);
        doReturn(request).when(web3j).ethCall(any(), eq(org.web3j.protocol.core.DefaultBlockParameterName.LATEST));
        return web3j;
    }

    @SuppressWarnings("unchecked")
    private static Web3j web3jReturningError(String message) throws IOException {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> request = mock(Request.class);
        EthCall response = new EthCall();
        response.setError(new Response.Error(3, message));
        when(request.send()).thenReturn(response);
        doReturn(request).when(web3j).ethCall(any(), eq(org.web3j.protocol.core.DefaultBlockParameterName.LATEST));
        return web3j;
    }

    @SuppressWarnings("unchecked")
    private static Web3j web3jThrowing(IOException exception) throws IOException {
        Web3j web3j = mock(Web3j.class);
        Request<?, EthCall> request = mock(Request.class);
        when(request.send()).thenThrow(exception);
        doReturn(request).when(web3j).ethCall(any(), eq(org.web3j.protocol.core.DefaultBlockParameterName.LATEST));
        return web3j;
    }
}
