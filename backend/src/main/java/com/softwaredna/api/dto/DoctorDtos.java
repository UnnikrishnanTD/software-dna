package com.softwaredna.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The assistant's request and response shapes.
 *
 * <p>An answer is a list of typed blocks rather than a paragraph of text. That
 * is what lets the frontend render a metric as a real meter and a plan as a
 * ranked list, and it is what separates a diagnostic tool from a chat window
 * that happens to quote numbers.
 */
public final class DoctorDtos {

    private DoctorDtos() {
    }

    @Schema(description = "A question about one analysis")
    public record QuestionRequest(
            @NotBlank(message = "an analysis id is required") String analysisId,
            @NotBlank(message = "a question is required")
            @Size(max = 1000, message = "that question is too long")
            String question
    ) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include =
            JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TextBlock.class, name = "text"),
            @JsonSubTypes.Type(value = MetricBlock.class, name = "metric"),
            @JsonSubTypes.Type(value = ListBlock.class, name = "list"),
            @JsonSubTypes.Type(value = NodesBlock.class, name = "nodes"),
            @JsonSubTypes.Type(value = PlanBlock.class, name = "plan")
    })
    public sealed interface DoctorBlock
            permits TextBlock, MetricBlock, ListBlock, NodesBlock, PlanBlock {
        String type();
    }

    public record TextBlock(String type, String text) implements DoctorBlock {
        public TextBlock(String text) {
            this("text", text);
        }
    }

    public record MetricBlock(String type, String label, double value, double verdictScore)
            implements DoctorBlock {
        public MetricBlock(String label, double value, double verdictScore) {
            this("metric", label, value, verdictScore);
        }
    }

    public record ListBlock(String type, boolean ordered, List<String> items)
            implements DoctorBlock {
        public ListBlock(boolean ordered, List<String> items) {
            this("list", ordered, items);
        }
    }

    public record NodesBlock(String type, String caption, List<String> nodeIds)
            implements DoctorBlock {
        public NodesBlock(String caption, List<String> nodeIds) {
            this("nodes", caption, nodeIds);
        }
    }

    public record PlanBlock(String type, List<AnalysisDtos.RemediationStepDto> steps)
            implements DoctorBlock {
        public PlanBlock(List<AnalysisDtos.RemediationStepDto> steps) {
            this("plan", steps);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DoctorMessage(
            String id,
            @Schema(allowableValues = {"user", "doctor"}) String author,
            String text,
            List<DoctorBlock> blocks,
            long timestamp,
            @Schema(description = "Which provider answered, so a mock is never mistaken for a model")
            String provider
    ) {
    }
}
