
package com.softwaredna.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where cloned repositories are written.
 *
 * Every clone lives in a directory named after its analysis id beneath
 * {@code root}. Nothing outside that root is ever read or written, which is
 * what makes path traversal in a repository harmless.
 */
@Validated
@ConfigurationProperties(prefix = "softwaredna.workspace")
public record WorkspaceProperties(
        @NotBlank String root,
        boolean keepAfterAnalysis
) {
}
