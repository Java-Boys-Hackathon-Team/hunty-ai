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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@JmixEntity
@Table(name = "RESUME_EXPERIENCE_ENTITY", indexes = {
        @Index(name = "IDX_RESUME_EXPERIENCE_ENTITY_RESUME_VERSION_ENTITY", columnList = "RESUME_VERSION_ENTITY_ID"),
        @Index(name = "IDX_RESUME_EXPERIENCE_ENTITY_COMPANY", columnList = "COMPANY_ID")
})
@Entity
public class ResumeExperienceEntity {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @JoinColumn(name = "RESUME_VERSION_ENTITY_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ResumeVersionEntity resumeVersionEntity;

    @JoinColumn(name = "COMPANY_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private CompanyEntity company;

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public ResumeVersionEntity getResumeVersionEntity() {
        return resumeVersionEntity;
    }

    public void setResumeVersionEntity(ResumeVersionEntity resumeVersionEntity) {
        this.resumeVersionEntity = resumeVersionEntity;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}