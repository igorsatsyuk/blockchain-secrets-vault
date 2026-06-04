package lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class SecretIdCodec {

    private SecretIdCodec() {
    }

    public static byte[] toBytes32(UUID secretId) {
        ByteBuffer uuidBytes = ByteBuffer.allocate(16)
                .putLong(secretId.getMostSignificantBits())
                .putLong(secretId.getLeastSignificantBits());
        byte[] bytes32 = new byte[32];
        System.arraycopy(uuidBytes.array(), 0, bytes32, 0, 16);
        return bytes32;
    }
}
