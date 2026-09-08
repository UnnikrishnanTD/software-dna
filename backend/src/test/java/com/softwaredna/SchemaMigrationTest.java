package com.softwaredna;

import com.softwaredna.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs every Flyway migration against a real PostgreSQL and checks that the
 * schema the rest of the system assumes actually exists.
 */
class SchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appliesEveryMigration() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(applied).isEqualTo(6);
    }

    @Test
    void createsEveryTableTheDomainNeeds() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains(
                "repository", "analysis", "analysis_stage",
                "dimension_score", "metric",
                "code_file", "architecture_node", "architecture_edge", "architecture_cycle",
                "hotspot", "dependency", "technology",
                "contributor", "evolution_point", "evolution_milestone",
                "issue", "recommendation");
    }

    @Test
    void indexesTheColumnsTheScreensActuallySortOn() {
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);

        assertThat(indexes).contains(
                "idx_analysis_completed",   // analyses list
                "idx_file_path",            // codebase explorer tree walk
                "idx_file_health",          // lowest-health ranking
                "idx_hotspot_analysis",     // hotspot ranking
                "idx_edge_source",          // graph traversal
                "idx_edge_target");
    }

    @Test
    void keepsNullableTheMeasurementsThatMayBeUnavailable() {
        // Coverage cannot be measured by static analysis. The column must
        // stay nullable so a missing value is never stored as zero.
        assertThat(isNullable("analysis", "test_coverage")).isTrue();
        assertThat(isNullable("code_file", "coverage")).isTrue();
        assertThat(isNullable("dependency", "advisory_count")).isTrue();
        assertThat(isNullable("dependency", "latest_version")).isTrue();
        assertThat(isNullable("analysis", "percentile")).isTrue();
        assertThat(isNullable("dimension_score", "score")).isTrue();
    }

    @Test
    void cascadesChildRowsWhenAnAnalysisIsDeleted() {
        List<String> rules = jdbc.queryForList("""
                SELECT rc.delete_rule
                FROM information_schema.referential_constraints rc
                JOIN information_schema.table_constraints tc
                  ON tc.constraint_name = rc.constraint_name
                WHERE tc.table_name = 'code_file'
                """, String.class);
        assertThat(rules).contains("CASCADE");
    }

    private boolean isNullable(String table, String column) {
        String nullable = jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, String.class, table, column);
        return "YES".equals(nullable);
    }
}
