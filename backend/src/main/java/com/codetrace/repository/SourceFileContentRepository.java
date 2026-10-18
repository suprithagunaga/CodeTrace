package com.codetrace.repository;

import com.codetrace.entity.SourceFileContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SourceFileContentRepository extends JpaRepository<SourceFileContent, UUID> {
}
