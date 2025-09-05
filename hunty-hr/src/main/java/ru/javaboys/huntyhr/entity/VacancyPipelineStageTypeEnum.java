package ru.javaboys.huntyhr.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum VacancyPipelineStageTypeEnum implements EnumClass<String> {

    SOURCING("SOURCING"),
    CV_REVIEW("CV_REVIEW"),
    SENT_TO_MANAGER("SENT_TO_MANAGER"),
    INTERVIEW_SCHEDULED("INTERVIEW_SCHEDULED"),
    TECH_INTERVIEW("TECH_INTERVIEW"),
    MANAGER_INTERVIEW("MANAGER_INTERVIEW"),
    FINAL_INTERVIEW("FINAL_INTERVIEW"),
    OFFER("OFFER"),
    HIRED("HIRED"),
    REJECTED("REJECTED");

    private final String id;

    VacancyPipelineStageTypeEnum(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static VacancyPipelineStageTypeEnum fromId(String id) {
        for (VacancyPipelineStageTypeEnum at : VacancyPipelineStageTypeEnum.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}