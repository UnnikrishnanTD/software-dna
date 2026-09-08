
package com.softwaredna.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableCaching
@EnableConfigurationProperties({
        GitHubProperties.class,
        WorkspaceProperties.class,
        AnalysisLimits.class,
        AnalysisExecutionProperties.class,
        AiProperties.class
})
public class ApplicationConfig {

    /**
     * Shared builder for outbound HTTP. Providers add their own base URL and
     * authentication; nothing here carries credentials.
     */
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
