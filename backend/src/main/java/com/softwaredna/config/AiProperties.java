
package com.softwaredna.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Selects the AI provider. Defaults to {@code deterministic}, which answers
 * from the analysis data itself and needs no credentials.
 */
@ConfigurationProperties(prefix = "softwaredna.ai")
public record AiProperties(String provider, String apiKey, String model) {

    public AiProperties {
        if (provider == null || provider.isBlank()) provider = "deterministic";
        if (apiKey == null) apiKey = "";
        if (model == null) model = "";
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "AiProperties[provider=%s, apiKey=%s, model=%s]"
                .formatted(provider, hasApiKey() ? "<set>" : "<absent>", model);
    }
}
