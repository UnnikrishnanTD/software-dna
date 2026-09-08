package com.softwaredna.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.softwaredna.common.domain.ArchitectureEdgeKind;
import com.softwaredna.common.domain.ArchitectureLayer;
import com.softwaredna.common.domain.ArchitectureNodeKind;
import com.softwaredna.common.domain.ComplexityBand;
import com.softwaredna.common.domain.DependencyEcosystem;
import com.softwaredna.common.domain.DependencyStatus;
import com.softwaredna.common.domain.DnaDimension;
import com.softwaredna.common.domain.Effort;
import com.softwaredna.common.domain.HealthVerdict;
import com.softwaredna.common.domain.InsightKind;
import com.softwaredna.common.domain.MilestoneKind;
import com.softwaredna.common.domain.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * The wire contract.
 *
 * <p>These records mirror the TypeScript interfaces the Angular application
 * already renders, field for field and name for name. The frontend is the
 * source of truth for this product's experience, so the API is shaped to it
 * rather than the other way round — which is why, for instance, dimension keys
 * are lower-case strings and complexity bands are hyphenated.
 *
 * <p>Nullable fields are nullable because the corresponding measurement may
 * genuinely be unavailable. {@code coverage} is the clearest case: static
 * analysis cannot produce it, so it is null unless the repository committed a
 * report. Nothing here ever defaults a missing measurement to zero.
 */
public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    // ---- Repository and summary -------------------------------------------

    @Schema(description = "Identifying details of the analysed repository")
    public record RepositoryRef(
            String owner,
            String name,
            String displayName,
            String url,
            String defaultBranch,
            String description,
            int stars,
            int forks,
            String primaryLanguage,
            String createdAt,
            String lastCommitAt
    ) {
    }

    @Schema(description = "Lightweight descriptor used in lists and the comparison picker")
    public record AnalysisSummary(
            String id,
            String displayName,
            String owner,
            String name,
            String url,
            double overall,
            String primaryLanguage,
            String generatedAt
    ) {
    }

    @Schema(description = "Headline counts for the analysed repository")
    public record AnalysisStats(
            int files,
            int linesOfCode,
            int modules,
            int services,
            int components,
            int commits,
            int contributors,
            @Schema(description = "Null when no coverage report was committed")
            Double testCoverage,
            long analysisDurationMs
    ) {
    }

    // ---- DNA ---------------------------------------------------------------

    @Schema(description = "One measured dimension of the profile")
    public record DnaDimensionDto(
            DnaDimension key,
            String label,
            @Schema(description = "Null when the dimension could not be assessed")
            Double score,
            HealthVerdict verdict,
            String headline,
            String summary,
            List<String> strengths,
            List<String> watchItems,
            double delta,
            double weight,
            @Schema(description = "0-100. Falls when inputs were missing.")
            double confidence,
            @Schema(description = "Metric key to the value that justified the finding")
            Map<String, String> evidence
    ) {
    }

    @Schema(description = "The complete Software DNA profile")
    public record DnaScore(
            double overall,
            HealthVerdict verdict,
            List<DnaDimensionDto> dimensions,
            @Schema(description = "Null until enough analyses exist to place this one")
            Double percentile,
            double confidence
    ) {
    }

    // ---- Architecture ------------------------------------------------------

    public record ArchitectureNodeDto(
            String id,
            String name,
            ArchitectureNodeKind kind,
            ArchitectureLayer layer,
            String path,
            String language,
            ComplexityBand complexity,
            int complexityScore,
            int linesOfCode,
            @Schema(description = "Null unless a coverage report was committed")
            Double coverage,
            RiskLevel risk,
            int fanOut,
            int fanIn,
            String description
    ) {
    }

    public record ArchitectureEdgeDto(
            String id,
            String source,
            String target,
            ArchitectureEdgeKind kind,
            int weight
    ) {
    }

    public record ArchitectureGraphDto(
            List<ArchitectureNodeDto> nodes,
            List<ArchitectureEdgeDto> edges,
            List<List<String>> circularDependencies
    ) {
    }

    // ---- Codebase ----------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FileMetricsDto(
            int linesOfCode,
            ComplexityBand complexity,
            int complexityScore,
            int dependencies,
            int dependents,
            int changes,
            Double coverage,
            RiskLevel risk,
            String lastChanged,
            String primaryAuthor
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FileNodeDto(
            String id,
            String name,
            String path,
            @Schema(allowableValues = {"directory", "file"}) String type,
            String language,
            List<FileNodeDto> children,
            FileMetricsDto metrics,
            Double health,
            String architectureNodeId
    ) {
    }

    // ---- Hotspots ----------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HotspotDto(
            String id,
            String name,
            String path,
            int changes,
            int complexityScore,
            ComplexityBand complexity,
            int dependencies,
            int bugFixes,
            int linesOfCode,
            Double coverage,
            int contributors,
            double riskScore,
            RiskLevel severity,
            String rationale,
            String recommendation,
            String architectureNodeId
    ) {
    }

    // ---- Dependencies ------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DependencyDto(
            String id,
            String name,
            DependencyEcosystem ecosystem,
            String version,
            @Schema(description = "Null when no registry lookup was performed")
            String latestVersion,
            DependencyStatus status,
            String license,
            boolean direct,
            int usedBy,
            Integer sizeKb,
            RiskLevel risk,
            @Schema(description = "Null means no advisory source was consulted, not zero findings")
            Integer advisories,
            String note
    ) {
    }

    public record TechnologyDto(
            String id,
            String name,
            String category,
            double share,
            int linesOfCode,
            String version,
            int dependencyCount
    ) {
    }

    public record DependencyProfileDto(
            int total,
            int direct,
            @Schema(description = "Null: transitive resolution requires a registry lookup")
            Integer transitive,
            Integer outdated,
            int deprecated,
            Integer advisories,
            List<DependencyDto> dependencies,
            List<TechnologyDto> technologies
    ) {
    }

    // ---- Evolution ---------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EvolutionPointDto(
            int year,
            @Schema(description = "Dimension scores; empty for years the analysis did not re-run")
            Map<String, Double> scores,
            Double overall,
            int contributors,
            int commits,
            int linesOfCode,
            int modules,
            int hotspots,
            Double testCoverage
    ) {
    }

    public record EvolutionMilestoneDto(
            String id,
            int year,
            double offset,
            MilestoneKind kind,
            String title,
            String description,
            double impact
    ) {
    }

    public record EvolutionHistoryDto(
            List<EvolutionPointDto> points,
            List<EvolutionMilestoneDto> milestones
    ) {
    }

    // ---- Insights ----------------------------------------------------------

    public record AiInsightDto(
            String id,
            InsightKind kind,
            RiskLevel severity,
            String title,
            String body,
            DnaDimension dimension,
            List<String> relatedNodeIds,
            double confidence
    ) {
    }

    public record RemediationStepDto(
            int rank,
            String title,
            String detail,
            Effort effort,
            double expectedGain,
            DnaDimension dimension
    ) {
    }

    public record DoctorPromptDto(
            String id,
            String question,
            String category
    ) {
    }

    // ---- The aggregate -----------------------------------------------------

    @Schema(description = "Everything one completed analysis produced")
    public record RepositoryAnalysisDto(
            String id,
            RepositoryRef repository,
            String generatedAt,
            AnalysisStats stats,
            DnaScore dna,
            ArchitectureGraphDto architecture,
            FileNodeDto codebase,
            List<HotspotDto> hotspots,
            DependencyProfileDto dependencies,
            EvolutionHistoryDto evolution,
            List<AiInsightDto> insights,
            List<RemediationStepDto> remediation,
            List<DoctorPromptDto> doctorPrompts,
            @Schema(description = "Dimensions that could not be assessed")
            List<String> incompleteDimensions
    ) {
    }
}
