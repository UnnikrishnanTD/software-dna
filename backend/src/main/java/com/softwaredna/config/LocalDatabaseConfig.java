package com.softwaredna.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Starts a PostgreSQL server inside the JVM for local development.
 *
 * <p>Exists because a developer should be able to run this application without
 * first installing Docker or a database. Enabling the {@code localdb} profile
 * downloads a real PostgreSQL binary on first run, starts it, and hands Spring
 * its {@link DataSource}; Flyway then migrates it exactly as it would migrate a
 * managed instance, so what runs locally is the same schema on the same engine.
 *
 * <p><strong>Never enable this profile in a deployed environment.</strong> The
 * data lives in a temporary directory and is discarded when the process exits.
 * Production sets {@code DATABASE_URL} and this class is not activated.
 */
@Configuration
@Profile("localdb")
public class LocalDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(LocalDatabaseConfig.class);

    private EmbeddedPostgres postgres;

    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "localPostgresDataSource")
    DataSource localPostgresDataSource() {
        try {
            log.warn("Starting an embedded PostgreSQL for local development. "
                    + "Data is discarded on shutdown; never use the 'localdb' "
                    + "profile outside development.");
            postgres = EmbeddedPostgres.start();
            log.info("Embedded PostgreSQL listening on port {}", postgres.getPort());
            return postgres.getPostgresDatabase();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not start the embedded PostgreSQL. Set DATABASE_URL to use "
                            + "an external database instead.", e);
        }
    }

    @PreDestroy
    void stop() {
        if (postgres == null) {
            return;
        }
        try {
            postgres.close();
            log.info("Embedded PostgreSQL stopped");
        } catch (IOException e) {
            log.warn("Embedded PostgreSQL did not shut down cleanly: {}", e.getMessage());
        }
    }
}
