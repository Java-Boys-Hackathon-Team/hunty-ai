package ru.javaboys.huntyhr.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum ApplicationStatusEnum implements EnumClass<String> {

    NEW("NEW"),
    SCREENING("SCREENING"),
    INVITED("INVITED"),
    INTERVIEWING("INTERVIEWING"),
    SCORED("SCORED"),
    RECOMMENDED("RECOMMENDED"),
    REJECTED("REJECTED");

    private final String id;

    ApplicationStatusEnum(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static ApplicationStatusEnum fromId(String id) {
        for (ApplicationStatusEnum at : ApplicationStatusEnum.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}