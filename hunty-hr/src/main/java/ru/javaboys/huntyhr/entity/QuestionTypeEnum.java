package ru.javaboys.huntyhr.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum QuestionTypeEnum implements EnumClass<String> {

    INTRO("INTRO"),
    TECH("TECH"),
    COMM("COMM"),
    CASE("CASE"),
    BEHAVIORAL("BEHAVIORAL"),
    WRAPUP("WRAPUP");

    private final String id;

    QuestionTypeEnum(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static QuestionTypeEnum fromId(String id) {
        for (QuestionTypeEnum at : QuestionTypeEnum.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}