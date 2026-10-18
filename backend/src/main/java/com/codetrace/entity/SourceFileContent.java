package com.codetrace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Optional copy of an uploaded file's text, keyed by the {@link SourceFile} id.
 * Only written when {@code codetrace.retention.store-source=true}.
 */
@Entity
@Table(name = "source_file_content")
public class SourceFileContent implements Persistable<UUID> {

    @Id
    @Column(name = "source_file_id")
    private UUID sourceFileId;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Transient
    private boolean isNew = true;

    protected SourceFileContent() {
    }

    public SourceFileContent(UUID sourceFileId, String content) {
        this.sourceFileId = sourceFileId;
        this.content = content;
    }

    @Override
    public UUID getId() {
        return sourceFileId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public String getContent() {
        return content;
    }
}
