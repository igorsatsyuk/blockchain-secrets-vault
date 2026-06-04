package lt.satsyuk.blockchainsecretsvault.secretsapi.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiPortsConfiguration {
    /*
     * Issue #7 owns the CRUD API contract. KMS encryption is introduced by
     * issue #8; blockchain ACL adapters are configured by issue #9.
     */

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

