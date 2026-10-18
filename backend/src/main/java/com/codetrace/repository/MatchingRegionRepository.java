package com.codetrace.repository;

import com.codetrace.entity.MatchingRegion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchingRegionRepository extends JpaRepository<MatchingRegion, UUID> {

    List<MatchingRegion> findByComparison_IdOrderByFileAStartOffsetAsc(UUID comparisonId);

    List<MatchingRegion> findByComparison_Analysis_Id(UUID analysisId);

    long countByComparison_Analysis_Id(UUID analysisId);
}
