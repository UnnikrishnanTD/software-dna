package com.softwaredna.ai;

import com.softwaredna.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects the configured AI provider, falling back safely.
 *
 * <p>If a provider is named but has no credentials, the registry falls back to
 * the deterministic one and says so in the log, rather than failing every
 * question at runtime. Every answer carries the provider that produced it, so
 * a fallback is visible in the response rather than silent.
 */
@Component
public class AiProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRegistry.class);

    private final AiProvider selected;

    public AiProviderRegistry(List<AiProvider> providers, AiProperties properties,
                              DeterministicAiProvider fallback) {
        AiProvider configured = providers.stream()
                .filter(provider -> provider.name().equalsIgnoreCase(properties.provider()))
                .findFirst()
                .orElse(null);

        if (configured == null) {
            log.info("AI provider '{}' is not registered; using the deterministic provider",
                    properties.provider());
            this.selected = fallback;
        } else if (!configured.isAvailable()) {
            log.warn("AI provider '{}' is configured but unavailable (no credentials); "
                    + "using the deterministic provider", configured.name());
            this.selected = fallback;
        } else {
            log.info("AI provider: {}", configured.name());
            this.selected = configured;
        }
    }

    public AiProvider provider() {
        return selected;
    }
}
