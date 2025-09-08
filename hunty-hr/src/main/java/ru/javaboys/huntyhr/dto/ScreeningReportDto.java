package ru.javaboys.huntyhr.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScreeningReportDto {
    private String overall;
    private List<String> strengths;
    private List<String> gaps;
    private List<String> hardMatches;
    private List<String> softMatches;
    private List<String> risks;
    private List<String> recommendations;
}
