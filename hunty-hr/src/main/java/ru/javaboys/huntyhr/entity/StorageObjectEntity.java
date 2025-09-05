package ru.javaboys.huntyhr.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import io.jmix.core.FileRef;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@JmixEntity
@Table(name = "STORAGE_OBJECT_ENTITY")
@Entity
public class StorageObjectEntity {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "REF_")
    @Lob
    private FileRef ref;

    @Column(name = "TYPE_")
    private String type;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FileRef getRef() {
        return ref;
    }

    public void setRef(FileRef ref) {
        this.ref = ref;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}