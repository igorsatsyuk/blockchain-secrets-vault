package lt.satsyuk.blockchainsecretsvault.secretsapi.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecretIdCodecTest {

    @Test
    void convertsUuidToBytes32WithUuidBytesFirstAndZeroPadding() {
        UUID secretId = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

        byte[] encoded = SecretIdCodec.toBytes32(secretId);

        assertThat(encoded)
                .hasSize(32)
                .startsWith(
                (byte) 0x00,
                (byte) 0x11,
                (byte) 0x22,
                (byte) 0x33,
                (byte) 0x44,
                (byte) 0x55,
                (byte) 0x66,
                (byte) 0x77,
                (byte) 0x88,
                (byte) 0x99,
                (byte) 0xaa,
                (byte) 0xbb,
                (byte) 0xcc,
                (byte) 0xdd,
                (byte) 0xee,
                (byte) 0xff
        ).endsWith(
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                (byte) 0
        );
    }
}
