package com.codetrace.repository;

import com.codetrace.entity.SourceFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SourceFileRepository extends JpaRepository<SourceFile, UUID> {

    List<SourceFile> findByAnalysis_IdOrderByOrdinalAsc(UUID analysisId);

    long countByAnalysis_Id(UUID analysisId);
}
