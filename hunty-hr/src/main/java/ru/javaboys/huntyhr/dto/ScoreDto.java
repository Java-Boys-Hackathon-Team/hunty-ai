package ru.javaboys.huntyhr.dto;

import lombok.Data;
import java.util.List;

@Data
public class ScoreDto {
    private Integer tech;   // 0..100
    private Integer comm;   // 0..100
    private Integer cases;  // 0..100
    private Integer total;  // игнорируем с точки зрения истины, считаем на бэке

    private String summary;          // краткий отчёт
    private List<String> matches;    // что хорошо совпало
    private List<String> gaps;       // чего не хватает
    private List<String> redFlags;   // риски (опц.)
}