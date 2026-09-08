package com.softwaredna.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI softwareDnaApi() {
        return new OpenAPI().info(new Info()
                .title("Software DNA API")
                .version("0.1.0")
                .description("""
                        Analyses a Git repository and produces a Software DNA profile.

                        Two conventions run through this API and are worth knowing before
                        reading any response:

                        1. A null measurement means *not measured*, never zero. Test
                           coverage is null unless the repository committed a coverage
                           report, because measuring it would require executing repository
                           code. Dependency advisory counts are null unless a vulnerability
                           database was consulted.

                        2. Every dimension score carries a confidence and an evidence map.
                           The evidence names the metrics that produced the score, so any
                           number in a profile can be traced back to what was measured.
                        """)
                .license(new License().name("Proprietary")));
    }
}
