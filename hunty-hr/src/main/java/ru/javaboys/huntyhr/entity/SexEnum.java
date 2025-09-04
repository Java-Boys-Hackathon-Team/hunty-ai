package ru.javaboys.huntyhr.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum SexEnum implements EnumClass<String> {

    MALE("MALE"),
    FEMALE("FEMALE");

    private final String id;

    SexEnum(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static SexEnum fromId(String id) {
        for (SexEnum at : SexEnum.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}