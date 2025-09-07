package ru.javaboys.huntyhr.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class ResumeInfo {
    private String fullName;
    private String title; // e.g., Java Developer
    private List<String> skills;
    private Integer totalYearsExperience; // sum or best estimate
    private List<String> languages; // human languages
    private String educationLevel; // e.g., BACHELOR, MASTER
    private String seniority; // e.g., JUNIOR, MIDDLE, SENIOR
    private List<String> lastCompanies; // optional
}
