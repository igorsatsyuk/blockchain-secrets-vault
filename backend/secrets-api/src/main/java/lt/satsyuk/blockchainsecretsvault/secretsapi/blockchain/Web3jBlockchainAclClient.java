package lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;

public class Web3jBlockchainAclClient implements BlockchainAclClient {

    private static final BigInteger ZERO = BigInteger.ZERO;

    private final Web3j web3j;
    private final Credentials credentials;
    private final RawTransactionManager transactionManager;
    private final String contractAddress;
    private final BigInteger gasPrice;
    private final BigInteger gasLimit;

    public Web3jBlockchainAclClient(
            Web3j web3j,
            Credentials credentials,
            String contractAddress,
            BigInteger gasPrice,
            BigInteger gasLimit
    ) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.transactionManager = new RawTransactionManager(web3j, credentials);
        this.contractAddress = contractAddress;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
    }

    @Override
    public String grantAccess(UUID secretId, String account, boolean canRead, boolean canWrite) {
        return send(function(
                "grantAccess",
                List.of(secretId(secretId), new Address(account), new Bool(canRead), new Bool(canWrite)),
                List.of()
        ));
    }

    @Override
    public String revokeAccess(UUID secretId, String account) {
        return send(function(
                "revokeAccess",
                List.of(secretId(secretId), new Address(account)),
                List.of()
        ));
    }

    @Override
    public boolean canRead(UUID secretId, String account) {
        return callBoolean(function(
                "canRead",
                List.of(secretId(secretId), new Address(account)),
                List.of(TypeReference.create(Bool.class))
        ));
    }

    @Override
    public boolean canWrite(UUID secretId, String account) {
        return callBoolean(function(
                "canWrite",
                List.of(secretId(secretId), new Address(account)),
                List.of(TypeReference.create(Bool.class))
        ));
    }

    private String send(Function function) {
        try {
            EthSendTransaction transaction = transactionManager.sendTransaction(
                    gasPrice,
                    gasLimit,
                    contractAddress,
                    FunctionEncoder.encode(function),
                    ZERO
            );
            if (transaction.hasError()) {
                throw new BlockchainAclException(transaction.getError().getMessage());
            }
            return transaction.getTransactionHash();
        } catch (IOException exception) {
            throw new BlockchainAclException("Failed to submit ACL transaction", exception);
        }
    }

    private boolean callBoolean(Function function) {
        try {
            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(
                            credentials.getAddress(),
                            contractAddress,
                            FunctionEncoder.encode(function)
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();
            if (response.hasError()) {
                throw new BlockchainAclException(response.getError().getMessage());
            }
            List<Type> values = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            return (Boolean) values.getFirst().getValue();
        } catch (IOException exception) {
            throw new BlockchainAclException("Failed to call ACL contract", exception);
        }
    }

    private static Function function(String name, List<Type> inputs, List<TypeReference<?>> outputs) {
        return new Function(name, inputs, outputs);
    }

    private static Bytes32 secretId(UUID secretId) {
        return new Bytes32(SecretIdCodec.toBytes32(secretId));
    }
}
