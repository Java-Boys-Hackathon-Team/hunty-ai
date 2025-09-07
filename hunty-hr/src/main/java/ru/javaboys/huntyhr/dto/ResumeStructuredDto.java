package ru.javaboys.huntyhr.dto;

import lombok.Data;

import java.util.List;

@Data
public class ResumeStructuredDto {
    private String name;
    private String surname;
    private String birthDate; // yyyy-MM-dd или ""
    private String sex;       // M|F|""
    private String email;
    private String phone;
    private String telegram;
    private String linkedin;
    private List<String> skills;

    // NEW:
    private List<EducationItem> education;   // список образований
    private List<ExperienceItem> experience; // список мест работы

    @Data
    public static class EducationItem {
        private String level; // "Высшее", "Бакалавр", "Магистр" ... либо ""
        private String place; // "МГУ, Бизнес-информатика" и т.п.
        private String startDate; // "yyyy-MM" | "yyyy" | ""  (LLM вернёт что найдёт)
        private String endDate;   // "yyyy-MM" | "yyyy" | ""
    }

    @Data
    public static class ExperienceItem {
        private String company;   // название компании
        private String startDate; // "yyyy-MM" | "yyyy"
        private String endDate;   // "yyyy-MM" | "yyyy" | "настоящее время" | ""
        private String title;     // опционально: должность
        private String summary;   // краткое описание (несколько строк)
    }
}