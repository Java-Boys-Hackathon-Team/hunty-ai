package ru.javaboys.huntyhr.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum SeniorityLevelEnum implements EnumClass<String> {

    INTERN("INTERN"),
    JUNIOR("JUNIOR"),
    MIDDLE("MIDDLE"),
    SENIOR("SENIOR"),
    LEAD("LEAD"),
    PRINCIPAL("PRINCIPAL");

    private final String id;

    SeniorityLevelEnum(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static SeniorityLevelEnum fromId(String id) {
        for (SeniorityLevelEnum at : SeniorityLevelEnum.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}