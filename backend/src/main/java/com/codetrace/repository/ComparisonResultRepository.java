package com.codetrace.repository;

import com.codetrace.entity.ComparisonResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComparisonResultRepository extends JpaRepository<ComparisonResult, UUID> {

    @Query("""
            select c from ComparisonResult c
            join fetch c.fileA fa
            join fetch c.fileB fb
            where c.analysis.id = :analysisId
            order by c.overallScore desc, fa.ordinal asc, fb.ordinal asc
            """)
    List<ComparisonResult> findForAnalysis(@Param("analysisId") UUID analysisId);

    @Query("""
            select c from ComparisonResult c
            join fetch c.fileA
            join fetch c.fileB
            join fetch c.analysis
            where c.id = :id and c.analysis.id = :analysisId
            """)
    Optional<ComparisonResult> findInAnalysis(@Param("analysisId") UUID analysisId, @Param("id") UUID id);

    @Query("""
            select c from ComparisonResult c
            join fetch c.fileA
            join fetch c.fileB
            join fetch c.analysis an
            order by c.overallScore desc, an.createdAt desc
            """)
    List<ComparisonResult> findTopScoring(Pageable pageable);

    long countByAnalysis_Id(UUID analysisId);

    /** Histogram of overall scores in ten 10%-wide buckets: rows of [bucket index 0..9, count]. */
    @Query(value = """
            select least(floor(overall_score * 10), 9) as bucket, count(*) as total
            from comparison_result
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> scoreHistogram();
}
