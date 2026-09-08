package com.softwaredna.analysis.entity;

import com.softwaredna.common.domain.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AnalysisJpaRepository extends JpaRepository<AnalysisEntity, UUID> {

    /** Completed analyses, newest first; backs the analyses list. */
    List<AnalysisEntity> findByStatusOrderByFinishedAtDesc(AnalysisStatus status);

    List<AnalysisEntity> findByRepositoryIdOrderByQueuedAtDesc(UUID repositoryId);

    /**
     * The previous completed analysis of the same repository, used to compute
     * each dimension's delta. Returns a list so the caller can take the first
     * without a subquery.
     */
    @Query("""
            SELECT a FROM AnalysisEntity a
            WHERE a.repositoryId = :repositoryId
              AND a.status = com.softwaredna.common.domain.AnalysisStatus.COMPLETED
              AND a.id <> :excluding
            ORDER BY a.finishedAt DESC
            """)
    List<AnalysisEntity> findPreviousCompleted(@Param("repositoryId") UUID repositoryId,
                                               @Param("excluding") UUID excluding);

    /** Overall scores of every completed analysis, for percentile placement. */
    @Query("""
            SELECT a.overallScore FROM AnalysisEntity a
            WHERE a.status = com.softwaredna.common.domain.AnalysisStatus.COMPLETED
              AND a.overallScore IS NOT NULL
            """)
    List<java.math.BigDecimal> findAllCompletedScores();

    long countByStatusIn(List<AnalysisStatus> statuses);
}
