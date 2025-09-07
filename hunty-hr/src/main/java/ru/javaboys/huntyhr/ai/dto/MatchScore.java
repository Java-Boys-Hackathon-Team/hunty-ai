package ru.javaboys.huntyhr.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MatchScore {
    private double score; // 0..100
    private String explanation;
}
