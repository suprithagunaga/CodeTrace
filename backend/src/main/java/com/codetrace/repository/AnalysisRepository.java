package com.codetrace.repository;

import com.codetrace.entity.Analysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    /**
     * Case-insensitive search over the analysis title and the names of its files.
     * {@code pattern} must already be lower-cased, LIKE-escaped with '!' and wrapped in '%'.
     */
    @Query(value = """
            select a from Analysis a
            where lower(a.title) like :pattern escape '!'
               or exists (select f.id from SourceFile f
                          where f.analysis = a and lower(f.displayName) like :pattern escape '!')
            """,
            countQuery = """
            select count(a) from Analysis a
            where lower(a.title) like :pattern escape '!'
               or exists (select f.id from SourceFile f
                          where f.analysis = a and lower(f.displayName) like :pattern escape '!')
            """)
    Page<Analysis> search(@Param("pattern") String pattern, Pageable pageable);

    /** Deletes the analysis row; files, contents, comparisons and regions are removed by ON DELETE CASCADE. */
    @Modifying
    @Query(value = "delete from analysis where id = :id", nativeQuery = true)
    int deleteAnalysis(@Param("id") UUID id);

    @Modifying
    @Query(value = "delete from analysis where created_at < :cutoff", nativeQuery = true)
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);

    /** One row: [analysis count, total files, total comparisons]. */
    @Query(value = """
            select count(*), coalesce(sum(file_count), 0), coalesce(sum(comparison_count), 0)
            from analysis
            """, nativeQuery = true)
    List<Object[]> totals();
}
