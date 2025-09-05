package ru.javaboys.huntyhr.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum VacancyStatusEnum implements EnumClass<String> {

    DRAFT("DRAFT"),
    OPEN("OPEN"),
    ON_HOLD("ON_HOLD"),
    CLOSED("CLOSED");

    private final String id;

    VacancyStatusEnum(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static VacancyStatusEnum fromId(String id) {
        for (VacancyStatusEnum at : VacancyStatusEnum.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}