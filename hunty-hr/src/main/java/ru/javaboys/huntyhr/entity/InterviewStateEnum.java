package ru.javaboys.huntyhr.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum InterviewStateEnum implements EnumClass<String> {

    PENDING("PENDING"),
    LIVE("LIVE"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
    CANCELED("CANCELED");

    private final String id;

    InterviewStateEnum(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static InterviewStateEnum fromId(String id) {
        for (InterviewStateEnum at : InterviewStateEnum.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}