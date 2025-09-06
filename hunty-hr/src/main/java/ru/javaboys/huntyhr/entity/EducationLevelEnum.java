package ru.javaboys.huntyhr.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum EducationLevelEnum implements EnumClass<String> {

    SECONDARY("SECONDARY"),
    VOCATIONAL_SECONDARY("VOCATIONAL_SECONDARY"),
    INCOMPLETE_HIGHER("INCOMPLETE_HIGHER"),
    BACHELOR("BACHELOR"),
    SPECIALIST("SPECIALIST"),
    MASTER("MASTER"),
    CANDIDATE_OF_SCIENCES("CANDIDATE_OF_SCIENCES"),
    DOCTOR_OF_SCIENCES("DOCTOR_OF_SCIENCES");

    private final String id;

    EducationLevelEnum(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static EducationLevelEnum fromId(String id) {
        for (EducationLevelEnum at : EducationLevelEnum.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}