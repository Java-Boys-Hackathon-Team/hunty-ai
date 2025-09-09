package ru.javaboys.huntyhr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VacancyStructuredDto {
    private String title;

    private String seniority;

    private String status;

    @NotNull
    private String description;
    @NotNull
    private String responsibilities;
    @NotNull
    private String requirements;
    @NotNull
    private String niceToHave;
    @NotNull
    private String conditions;

    @JsonProperty("weightTech")
    private Integer weightTech;
    @JsonProperty("weightComm")
    private Integer weightComm;
    @JsonProperty("weightCases")
    private Integer weightCases;
}
