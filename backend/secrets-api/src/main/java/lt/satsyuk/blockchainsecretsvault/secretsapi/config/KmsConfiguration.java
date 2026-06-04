package lt.satsyuk.blockchainsecretsvault.secretsapi.config;

import lt.satsyuk.blockchainsecretsvault.kms.service.AesGcmKmsService;
import lt.satsyuk.blockchainsecretsvault.kms.service.KmsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KmsConfiguration {
    
    @Bean
    public KmsService kmsService() {
        return new AesGcmKmsService();
    }
}
