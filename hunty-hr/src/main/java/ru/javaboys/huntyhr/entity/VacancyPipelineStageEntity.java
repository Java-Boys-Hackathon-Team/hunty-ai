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
@Table(name = "VACANCY_PIPELINE_STAGE_ENTITY", indexes = {
        @Index(name = "IDX_VACANCY_PIPELINE_STAGE_ENTITY_VACANCY_ENTITY", columnList = "VACANCY_ENTITY_ID")
})
@Entity
public class VacancyPipelineStageEntity {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "TYPE_")
    private String type;

    @InstanceName
    @Column(name = "NAME")
    private String name;

    @Column(name = "IDX")
    private Long idx;

    @Column(name = "ACTIVE")
    private Boolean active;

    @JoinColumn(name = "VACANCY_ENTITY_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private VacancyEntity vacancyEntity;

    public VacancyEntity getVacancyEntity() {
        return vacancyEntity;
    }

    public void setVacancyEntity(VacancyEntity vacancyEntity) {
        this.vacancyEntity = vacancyEntity;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Long getIdx() {
        return idx;
    }

    public void setIdx(Long idx) {
        this.idx = idx;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public VacancyPipelineStageTypeEnum getType() {
        return type == null ? null : VacancyPipelineStageTypeEnum.fromId(type);
    }

    public void setType(VacancyPipelineStageTypeEnum type) {
        this.type = type == null ? null : type.getId();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}