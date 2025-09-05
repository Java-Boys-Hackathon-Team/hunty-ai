package ru.javaboys.huntyhr.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum LanguageEnum implements EnumClass<String> {

    RU("RU"),
    EN("EN"),
    DE("DE"),
    GR("GR");

    private final String id;

    LanguageEnum(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static LanguageEnum fromId(String id) {
        for (LanguageEnum at : LanguageEnum.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}