package ru.javaboys.huntyhr.entity;

import java.time.LocalDateTime;
import java.util.List;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@JmixEntity
@Table(name = "RESUME_VERSION_ENTITY", indexes = {
        @Index(name = "IDX_RESUME_VERSION_ENTITY_RESUME", columnList = "CANDIDATE_ID"),
        @Index(name = "IDX_RESUME_VERSION_ENTITY_FILE", columnList = "FILE_ID")
})
@Entity
public class ResumeVersionEntity {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @OneToMany(mappedBy = "resumeVersionEntity")
    private List<EducationEntity> education;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @JoinColumn(name = "CANDIDATE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private CandidateEntity candidate;

    @Column(name = "SOURCE_TYPE")
    private String sourceType;

    @Column(name = "SOURCE_URL")
    @Lob
    private String sourceUrl;

    @JoinColumn(name = "FILE_ID")
    @OneToOne(fetch = FetchType.LAZY)
    private StorageObjectEntity file;

    @OneToMany(mappedBy = "resumeVersionEntity")
    private List<ResumeExperienceEntity> experience;

    @OneToMany(mappedBy = "resumeVersionEntity")
    private List<ResumeSkillEntity> skills;

    public List<ResumeSkillEntity> getSkills() {
        return skills;
    }

    public void setSkills(List<ResumeSkillEntity> skills) {
        this.skills = skills;
    }

    public StorageObjectEntity getFile() {
        return file;
    }

    public void setFile(StorageObjectEntity file) {
        this.file = file;
    }

    public List<EducationEntity> getEducation() {
        return education;
    }

    public void setEducation(List<EducationEntity> education) {
        this.education = education;
    }

    public List<ResumeExperienceEntity> getExperience() {
        return experience;
    }

    public void setExperience(List<ResumeExperienceEntity> experience) {
        this.experience = experience;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setCandidate(CandidateEntity resume) {
        this.candidate = resume;
    }

    public CandidateEntity getCandidate() {
        return candidate;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String source) {
        this.sourceUrl = source;
    }

    public ResumeSourceType getSourceType() {
        return sourceType == null ? null : ResumeSourceType.fromId(sourceType);
    }

    public void setSourceType(ResumeSourceType sourceType) {
        this.sourceType = sourceType == null ? null : sourceType.getId();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}