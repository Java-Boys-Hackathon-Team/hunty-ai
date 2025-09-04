package ru.javaboys.huntyhr.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum ResumeSourceType implements EnumClass<String> {

    MANUAL("MANUAL"),
    HEAD_HUNTER("HEAD_HUNTER");

    private final String id;

    ResumeSourceType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static ResumeSourceType fromId(String id) {
        for (ResumeSourceType at : ResumeSourceType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}