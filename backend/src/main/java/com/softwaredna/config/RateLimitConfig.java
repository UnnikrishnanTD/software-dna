package com.softwaredna.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softwaredna.common.ratelimit.RateLimitInterceptor;
import com.softwaredna.common.ratelimit.RateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Wires per-IP rate limiting onto the two endpoints that cost real compute.
 *
 * <p>Everything else — reading a completed analysis, browsing the list — is
 * cheap and unauthenticated by design; only starting an analysis (a clone
 * plus a full static-analysis pass) and asking the Doctor a question are
 * limited.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitProperties properties;
    private final ObjectMapper json;

    public RateLimitConfig(RateLimitProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        RateLimiter analysisLimiter =
                new RateLimiter(properties.analysesPerHour(), Duration.ofHours(1));
        RateLimiter questionLimiter =
                new RateLimiter(properties.questionsPerHour(), Duration.ofHours(1));

        registry.addInterceptor(new RateLimitInterceptor(analysisLimiter, "start analysis", json))
                .addPathPatterns("/api/analyses")
                .order(0);

        registry.addInterceptor(new RateLimitInterceptor(questionLimiter, "ask doctor", json))
                .addPathPatterns("/api/ai/questions")
                .order(0);
    }
}
