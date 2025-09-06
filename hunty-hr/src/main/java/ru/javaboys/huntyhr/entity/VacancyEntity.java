package ru.javaboys.huntyhr.entity;

import java.util.List;
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
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@JmixEntity
@Table(name = "VACANCY_ENTITY", indexes = {
        @Index(name = "IDX_VACANCY_ENTITY_SCENARIO", columnList = "SCENARIO_ID")
})
@Entity
public class VacancyEntity {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @InstanceName
    @Column(name = "TITLE")
    private String title;

    @Column(name = "SENIORITY")
    private String seniority;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "DESCRIPTION")
    @Lob
    private String description;

    @Column(name = "RESPONSIBILITIES")
    @Lob
    private String responsibilities;

    @Column(name = "REQUIREMENTS")
    @Lob
    private String requirements;

    @Column(name = "NICE_TO_HAVE")
    @Lob
    private String niceToHave;

    @Column(name = "CONDITIONS")
    @Lob
    private String conditions;

    @Column(name = "WEIGHT_TECH")
    private Long weightTech;

    @Column(name = "WEIGHT_COMM")
    private Long weightComm;

    @Column(name = "WEIGHT_CASES")
    private Long weightCases;

    @OneToMany(mappedBy = "vacancyEntity")
    private List<VacancyPipelineStageEntity> stages;

    @JoinColumn(name = "SCENARIO_ID")
    @OneToOne(fetch = FetchType.LAZY)
    private InterviewScenarioEntity scenario;

    public InterviewScenarioEntity getScenario() {
        return scenario;
    }

    public void setScenario(InterviewScenarioEntity scenario) {
        this.scenario = scenario;
    }

    public List<VacancyPipelineStageEntity> getStages() {
        return stages;
    }

    public void setStages(List<VacancyPipelineStageEntity> stages) {
        this.stages = stages;
    }

    public Long getWeightCases() {
        return weightCases;
    }

    public void setWeightCases(Long weightCases) {
        this.weightCases = weightCases;
    }

    public Long getWeightComm() {
        return weightComm;
    }

    public void setWeightComm(Long weightComm) {
        this.weightComm = weightComm;
    }

    public Long getWeightTech() {
        return weightTech;
    }

    public void setWeightTech(Long weightTech) {
        this.weightTech = weightTech;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    public String getNiceToHave() {
        return niceToHave;
    }

    public void setNiceToHave(String niceToHave) {
        this.niceToHave = niceToHave;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }

    public String getResponsibilities() {
        return responsibilities;
    }

    public void setResponsibilities(String responsibilities) {
        this.responsibilities = responsibilities;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public VacancyStatusEnum getStatus() {
        return status == null ? null : VacancyStatusEnum.fromId(status);
    }

    public void setStatus(VacancyStatusEnum status) {
        this.status = status == null ? null : status.getId();
    }

    public SeniorityLevelEnum getSeniority() {
        return seniority == null ? null : SeniorityLevelEnum.fromId(seniority);
    }

    public void setSeniority(SeniorityLevelEnum seniority) {
        this.seniority = seniority == null ? null : seniority.getId();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}