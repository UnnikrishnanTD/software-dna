package com.softwaredna.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a real PostgreSQL server can be started without Docker.
 *
 * The integration test strategy depends on this: Testcontainers is the usual
 * choice but needs a Docker daemon, which is not available on this machine.
 */
class EmbeddedPostgresSmokeTest {

    @Test
    void startsARealPostgresServer() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start();
             Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement()) {

            try (ResultSet versionResult = statement.executeQuery("SELECT version()")) {
                assertThat(versionResult.next()).isTrue();
                assertThat(versionResult.getString(1)).contains("PostgreSQL");
            }

            // Confirm the features the schema will rely on are available.
            statement.execute("CREATE TABLE probe (id UUID PRIMARY KEY, payload JSONB NOT NULL)");
            statement.execute(
                    "INSERT INTO probe VALUES (gen_random_uuid(), '{\"ok\":true}'::jsonb)");
            try (ResultSet countResult = statement.executeQuery(
                    "SELECT count(*) FROM probe WHERE payload->>'ok' = 'true'")) {
                assertThat(countResult.next()).isTrue();
                assertThat(countResult.getInt(1)).isEqualTo(1);
            }
        }
    }
}
