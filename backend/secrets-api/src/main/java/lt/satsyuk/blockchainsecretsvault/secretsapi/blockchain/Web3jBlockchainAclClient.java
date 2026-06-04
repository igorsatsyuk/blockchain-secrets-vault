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
    private final TransactionSender transactionSender;
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
        RawTransactionManager transactionManager = new RawTransactionManager(web3j, credentials);
        this.transactionSender = transactionManager::sendTransaction;
        this.contractAddress = contractAddress;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
    }

    Web3jBlockchainAclClient(
            Web3j web3j,
            Credentials credentials,
            TransactionSender transactionSender,
            String contractAddress,
            BigInteger gasPrice,
            BigInteger gasLimit
    ) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.transactionSender = transactionSender;
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
            EthSendTransaction transaction = transactionSender.sendTransaction(
                    gasPrice,
                    gasLimit,
                    contractAddress,
                    FunctionEncoder.encode(function),
                    ZERO
            );
            if (transaction.hasError()) {
                throw new BlockchainAclException(transaction.getError().getMessage());
            }
            String transactionHash = transaction.getTransactionHash();
            if (!hasText(transactionHash)) {
                throw new BlockchainAclException("ACL transaction response did not include a transaction hash");
            }
            return transactionHash;
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
            String responseValue = response.getValue();
            if (!hasText(responseValue)) {
                throw new BlockchainAclException("ACL contract returned no boolean value");
            }
            List<?> values = FunctionReturnDecoder.decode(
                    responseValue,
                    function.getOutputParameters()
            );
            if (values.isEmpty()) {
                throw new BlockchainAclException("ACL contract returned no boolean value");
            }
            Type<?> value = (Type<?>) values.getFirst();
            return (Boolean) value.getValue();
        } catch (IOException exception) {
            throw new BlockchainAclException("Failed to call ACL contract", exception);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked", "java:S3740"})
    private static Function function(
            String name,
            List<? extends Type<?>> inputs,
            List<? extends TypeReference<?>> outputs
    ) {
        return new Function(name, inputs.stream().map(Type.class::cast).toList(), (List) outputs);
    }

    private static Bytes32 secretId(UUID secretId) {
        return new Bytes32(SecretIdCodec.toBytes32(secretId));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    interface TransactionSender {

        EthSendTransaction sendTransaction(
                BigInteger gasPrice,
                BigInteger gasLimit,
                String to,
                String data,
                BigInteger value
        ) throws IOException;
    }
}
