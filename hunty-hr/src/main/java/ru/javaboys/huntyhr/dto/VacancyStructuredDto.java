package ru.javaboys.huntyhr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VacancyStructuredDto {
    private String title;

    private String seniority;

    private String status;

    private String description;
    private String responsibilities;
    private String requirements;
    private String niceToHave;
    private String conditions;

    @JsonProperty("weightTech")
    private Integer weightTech;
    @JsonProperty("weightComm")
    private Integer weightComm;
    @JsonProperty("weightCases")
    private Integer weightCases;
}
