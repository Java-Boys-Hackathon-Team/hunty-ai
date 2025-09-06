package ru.javaboys.huntyhr.entity;

import java.util.UUID;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@JmixEntity
@Table(name = "EDUCATION_ENTITY", indexes = {
        @Index(name = "IDX_EDUCATION_ENTITY_RESUME_VERSION_ENTITY", columnList = "RESUME_VERSION_ENTITY_ID")
})
@Entity
public class EducationEntity {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "LEVEL_")
    private String level;

    @Column(name = "PLACE")
    @Lob
    private String place;

    @JoinColumn(name = "RESUME_VERSION_ENTITY_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ResumeVersionEntity resumeVersionEntity;

    public ResumeVersionEntity getResumeVersionEntity() {
        return resumeVersionEntity;
    }

    public void setResumeVersionEntity(ResumeVersionEntity resumeVersionEntity) {
        this.resumeVersionEntity = resumeVersionEntity;
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public EducationLevelEnum getLevel() {
        return level == null ? null : EducationLevelEnum.fromId(level);
    }

    public void setLevel(EducationLevelEnum level) {
        this.level = level == null ? null : level.getId();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}