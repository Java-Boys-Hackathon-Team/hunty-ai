package ru.javaboys.huntyhr.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class VacancyInfo {
    private String position; // e.g., Java Developer
    private List<String> requiredSkills;
    private Integer minYearsExperience; // minimum preferred
    private List<String> languages; // human languages
    private String requiredEducationLevel; // e.g., BACHELOR
    private String seniority; // desired level
}
