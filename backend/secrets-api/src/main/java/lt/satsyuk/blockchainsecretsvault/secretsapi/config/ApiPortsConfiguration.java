package lt.satsyuk.blockchainsecretsvault.secretsapi.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiPortsConfiguration {
    /*
     * Issue #7 owns the CRUD API contract. KMS encryption and blockchain ACL
     * adapters are introduced by follow-up issues #8 and #9.
     */

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

