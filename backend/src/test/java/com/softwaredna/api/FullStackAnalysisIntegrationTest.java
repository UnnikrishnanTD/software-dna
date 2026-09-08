package com.softwaredna.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.softwaredna.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The acceptance test for the whole system.
 *
 * <p>Boots the application against a real PostgreSQL, submits a real public
 * GitHub repository over HTTP, follows the analysis to completion by polling
 * the same endpoint the frontend polls, and then reads the profile back and
 * checks it against what that repository actually contains.
 *
 * <p>Tagged {@code live} because it clones from GitHub. Run with
 * {@code ./mvnw test -DexcludedGroups=}.
 */
@Tag("live")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FullStackAnalysisIntegrationTest extends PostgresIntegrationTest {

    /** Small, stable, and genuinely a repository: ideal for an acceptance run. */
    private static final String REPOSITORY = "github.com/octocat/Hello-World";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void analysesARealRepositoryFromSubmissionToProfile() {
        // ---- 1. Submit --------------------------------------------------
        ResponseEntity<JsonNode> submission = rest.postForEntity(
                url("/api/analyses"),
                Map.of("repositoryUrl", REPOSITORY),
                JsonNode.class);

        assertThat(submission.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(submission.getHeaders().getLocation()).isNotNull();

        String analysisId = submission.getBody().get("analysisId").asText();
        assertThat(submission.getBody().get("status").asText()).isEqualTo("QUEUED");
        assertThat(analysisId).isNotBlank();

        // ---- 2. Follow progress -----------------------------------------
        JsonNode finalStatus = pollUntilTerminal(analysisId, Duration.ofMinutes(4));

        assertThat(finalStatus.get("status").asText())
                .as("analysis failed: %s", finalStatus.path("error").asText(""))
                .isEqualTo("COMPLETED");
        assertThat(finalStatus.get("progress").asDouble()).isEqualTo(1.0);

        JsonNode stages = finalStatus.get("stages");
        assertThat(stages).hasSize(8);
        stages.forEach(stage -> {
            assertThat(stage.get("status").asText()).isEqualTo("complete");
            assertThat(stage.get("result").asText()).isNotBlank();
        });

        // ---- 3. Read the profile ----------------------------------------
        ResponseEntity<JsonNode> profileResponse = rest.getForEntity(
                url("/api/analyses/" + analysisId), JsonNode.class);
        assertThat(profileResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode profile = profileResponse.getBody();

        assertThat(profile.get("repository").get("owner").asText()).isEqualTo("octocat");
        assertThat(profile.get("repository").get("name").asText()).isEqualTo("Hello-World");

        JsonNode dna = profile.get("dna");
        assertThat(dna.get("dimensions")).hasSize(8);
        assertThat(dna.get("overall").asDouble()).isBetween(0.0, 100.0);
        assertThat(dna.get("verdict").asText()).isNotBlank();

        JsonNode stats = profile.get("stats");
        assertThat(stats.get("files").asInt()).isPositive();
        assertThat(stats.get("commits").asInt()).isPositive();

        // ---- 4. Honesty about what could not be measured -----------------
        assertThat(stats.get("testCoverage").isNull())
                .as("this repository commits no coverage report, so coverage must be null")
                .isTrue();
        assertThat(profile.get("dependencies").get("transitive").isNull())
                .as("transitive dependencies were never resolved, so the count must be null")
                .isTrue();
        assertThat(profile.get("dependencies").get("advisories").isNull())
                .as("no vulnerability database was consulted, so advisories must be null")
                .isTrue();
        assertThat(dna.get("percentile").isNull())
                .as("a percentile needs a corpus; with one analysis there is none")
                .isTrue();

        // ---- 5. Every score is traceable ---------------------------------
        dna.get("dimensions").forEach(dimension -> {
            assertThat(dimension.get("headline").asText()).isNotBlank();
            assertThat(dimension.get("summary").asText()).isNotBlank();
            assertThat(dimension.get("confidence").asDouble()).isBetween(0.0, 100.0);
            if (!dimension.get("score").isNull()) {
                assertThat(dimension.get("evidence").size())
                        .as("%s scored without recorded evidence",
                                dimension.get("key").asText())
                        .isPositive();
            }
        });

        // ---- 6. The sections the frontend renders ------------------------
        assertThat(profile.get("codebase").get("type").asText()).isEqualTo("directory");
        assertThat(profile.get("codebase").get("children").size()).isPositive();
        assertThat(profile.get("architecture").get("nodes")).isNotNull();
        assertThat(profile.get("evolution").get("points").size()).isPositive();
        assertThat(profile.get("doctorPrompts").size()).isEqualTo(7);

        // ---- 7. It appears in the list -----------------------------------
        ResponseEntity<JsonNode> list = rest.getForEntity(url("/api/analyses"), JsonNode.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().size()).isPositive();
        assertThat(list.getBody().get(0).get("id").asText()).isEqualTo(analysisId);

        // ---- 8. The Doctor answers from this analysis --------------------
        ResponseEntity<JsonNode> answer = rest.postForEntity(
                url("/api/ai/questions"),
                Map.of("analysisId", analysisId, "question", "What should I fix first?"),
                JsonNode.class);

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(answer.getBody().get("author").asText()).isEqualTo("doctor");
        assertThat(answer.getBody().get("provider").asText())
                .as("the provider must be named, so a mock is never taken for a model")
                .isEqualTo("deterministic");
        assertThat(answer.getBody().get("text").asText()).isNotBlank();
    }

    @Test
    void rejectsAReferenceThatIsNotAGitHubRepository() {
        ResponseEntity<JsonNode> response = rest.postForEntity(
                url("/api/analyses"),
                Map.of("repositoryUrl", "http://169.254.169.254/latest/meta-data"),
                JsonNode.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody().get("code").asText())
                .isIn("UNSUPPORTED_HOST", "INVALID_REPOSITORY_URL");
        assertThat(response.getBody().has("timestamp")).isTrue();
        // No stack trace, no exception class, nothing internal.
        assertThat(response.getBody().toString()).doesNotContain("Exception");
    }

    @Test
    void reportsAMissingRepositoryAsNotFound() {
        ResponseEntity<JsonNode> response = rest.postForEntity(
                url("/api/analyses"),
                Map.of("repositoryUrl", "octocat/this-repository-does-not-exist-xyz"),
                JsonNode.class);

        assertThat(response.getStatusCode()).isIn(
                HttpStatus.NOT_FOUND, HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void reportsAnUnknownAnalysisAsNotFound() {
        ResponseEntity<JsonNode> response = rest.getForEntity(
                url("/api/analyses/" + java.util.UUID.randomUUID()), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ANALYSIS_NOT_FOUND");
    }

    @Test
    void rejectsAnEmptyRepositoryReferenceWithFieldLevelDetail() {
        ResponseEntity<JsonNode> response = rest.postForEntity(
                url("/api/analyses"), Map.of("repositoryUrl", ""), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().get("errors").size()).isPositive();
    }

    /** Polls the status endpoint exactly as the frontend does. */
    private JsonNode pollUntilTerminal(String analysisId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        JsonNode last = null;

        while (Instant.now().isBefore(deadline)) {
            last = rest.getForObject(
                    url("/api/analyses/" + analysisId + "/status"), JsonNode.class);
            String status = last.get("status").asText();
            if (status.equals("COMPLETED") || status.equals("FAILED")
                    || status.equals("CANCELLED")) {
                return last;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Analysis did not finish within " + timeout
                + "; last status: " + last);
    }
}
