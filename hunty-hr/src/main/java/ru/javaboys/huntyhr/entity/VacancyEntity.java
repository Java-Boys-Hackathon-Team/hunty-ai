package ru.javaboys.huntyhr.entity;

import io.jmix.core.FileRef;
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

import java.util.List;
import java.util.UUID;

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

    @Column(name = "FILE_REF")
    @Lob
    private FileRef fileRef;

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
    private Integer weightTech;

    @Column(name = "WEIGHT_COMM")
    private Integer weightComm;

    @Column(name = "WEIGHT_CASES")
    private Integer weightCases;

    @OneToMany(mappedBy = "vacancyEntity")
    private List<VacancyPipelineStageEntity> stages;

    @JoinColumn(name = "SCENARIO_ID")
    @OneToOne(fetch = FetchType.LAZY)
    private InterviewScenarioEntity scenario;

    @Column(name = "FILE_NAME")
    @Lob
    private String fileName;

    public void setWeightTech(Integer weightTech) {
        this.weightTech = weightTech;
    }

    public Integer getWeightTech() {
        return weightTech;
    }

    public void setWeightComm(Integer weightComm) {
        this.weightComm = weightComm;
    }

    public Integer getWeightComm() {
        return weightComm;
    }

    public void setWeightCases(Integer weightCases) {
        this.weightCases = weightCases;
    }

    public Integer getWeightCases() {
        return weightCases;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public FileRef getFileRef() {
        return fileRef;
    }

    public void setFileRef(FileRef fileRef) {
        this.fileRef = fileRef;
    }

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