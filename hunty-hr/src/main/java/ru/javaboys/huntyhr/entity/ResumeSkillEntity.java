package ru.javaboys.huntyhr.entity;

import java.util.UUID;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@JmixEntity
@Table(name = "RESUME_SKILL_ENTITY", indexes = {
        @Index(name = "IDX_RESUME_SKILL_ENTITY_RESUME_VERSION_ENTITY", columnList = "RESUME_VERSION_ENTITY_ID")
})
@Entity
public class ResumeSkillEntity {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @InstanceName
    @Column(name = "NAME")
    private String name;

    @JoinColumn(name = "RESUME_VERSION_ENTITY_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ResumeVersionEntity resumeVersionEntity;

    public ResumeVersionEntity getResumeVersionEntity() {
        return resumeVersionEntity;
    }

    public void setResumeVersionEntity(ResumeVersionEntity resumeVersionEntity) {
        this.resumeVersionEntity = resumeVersionEntity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}