package ru.javaboys.huntyhr.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDelete;
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

import java.util.UUID;

@JmixEntity
@Table(name = "APPLICATION_ENTITY", indexes = {
        @Index(name = "IDX_APPLICATION_ENTITY_CANDIDATE", columnList = "CANDIDATE_ID"),
        @Index(name = "IDX_APPLICATION_ENTITY_VACANCY", columnList = "VACANCY_ID"),
        @Index(name = "IDX_APPLICATION_ENTITY_STAGE", columnList = "STAGE_ID")
})
@Entity
public class ApplicationEntity {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "SCREENING_SUMMARY_HTML")
    @Lob
    private String screeningSummaryHtml;

    @JoinColumn(name = "CANDIDATE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private CandidateEntity candidate;

    @JoinColumn(name = "VACANCY_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(DeletePolicy.CASCADE)
    private VacancyEntity vacancy;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "SCREENING_SUMMARY")
    @Lob
    private String screeningSummary;

    @Column(name = "SCREENING_MATCH_PERCENT")
    private Long screeningMatchPercent;

    @Column(name = "TECH_SCORE")
    private Long techScore;

    @Column(name = "COMM_SCORE")
    private Long commScore;

    @Column(name = "CASES_SCORE")
    private Long casesScore;

    @Column(name = "TOTAL_SCORE")
    private Long totalScore;

    @JoinColumn(name = "STAGE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private VacancyPipelineStageEntity stage;

    public String getScreeningSummaryHtml() {
        return screeningSummaryHtml;
    }

    public void setScreeningSummaryHtml(String screeningSummaryHtml) {
        this.screeningSummaryHtml = screeningSummaryHtml;
    }

    public VacancyPipelineStageEntity getStage() {
        return stage;
    }

    public void setStage(VacancyPipelineStageEntity stage) {
        this.stage = stage;
    }

    public Long getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Long totalScore) {
        this.totalScore = totalScore;
    }

    public Long getCasesScore() {
        return casesScore;
    }

    public void setCasesScore(Long casesScore) {
        this.casesScore = casesScore;
    }

    public Long getCommScore() {
        return commScore;
    }

    public void setCommScore(Long commScore) {
        this.commScore = commScore;
    }

    public Long getTechScore() {
        return techScore;
    }

    public void setTechScore(Long techScore) {
        this.techScore = techScore;
    }

    public Long getScreeningMatchPercent() {
        return screeningMatchPercent;
    }

    public void setScreeningMatchPercent(Long screeningMatchPercent) {
        this.screeningMatchPercent = screeningMatchPercent;
    }

    public String getScreeningSummary() {
        return screeningSummary;
    }

    public void setScreeningSummary(String screeningSummary) {
        this.screeningSummary = screeningSummary;
    }

    public ApplicationStatusEnum getStatus() {
        return status == null ? null : ApplicationStatusEnum.fromId(status);
    }

    public void setStatus(ApplicationStatusEnum status) {
        this.status = status == null ? null : status.getId();
    }

    public VacancyEntity getVacancy() {
        return vacancy;
    }

    public void setVacancy(VacancyEntity vacancy) {
        this.vacancy = vacancy;
    }

    public CandidateEntity getCandidate() {
        return candidate;
    }

    public void setCandidate(CandidateEntity candidate) {
        this.candidate = candidate;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}