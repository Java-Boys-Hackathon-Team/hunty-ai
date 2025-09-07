package ru.javaboys.huntyhr.ai;

import org.springframework.stereotype.Service;
import ru.javaboys.huntyhr.ai.dto.MatchScore;
import ru.javaboys.huntyhr.ai.dto.ResumeInfo;
import ru.javaboys.huntyhr.ai.dto.VacancyInfo;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class RankingService {

    public MatchScore scoreResumeAgainstVacancy(ResumeInfo resume, VacancyInfo vacancy) {
        if (resume == null || vacancy == null) {
            return new MatchScore(0, "Нет данных для сравнения");
        }
        double score = 0;
        StringBuilder expl = new StringBuilder();

        // 1) Skills overlap 0..50
        Set<String> req = normalizeSet(vacancy.getRequiredSkills());
        Set<String> got = normalizeSet(resume.getSkills());
        int overlap = 0;
        for (String s : req) if (got.contains(s)) overlap++;
        double skillsPart = req.isEmpty() ? 0 : (50.0 * overlap / req.size());
        score += skillsPart;
        expl.append(String.format(Locale.US, "Навыки: совпало %d из %d (%.1f). ", overlap, req.size(), skillsPart));

        // 2) Years experience 0..20
        Integer needY = vacancy.getMinYearsExperience();
        Integer hasY = resume.getTotalYearsExperience();
        double yearsPart = 0;
        if (needY != null && needY > 0 && hasY != null) {
            if (hasY >= needY) yearsPart = 20; else yearsPart = 20.0 * hasY / needY;
        }
        score += yearsPart;
        expl.append(String.format(Locale.US, "Опыт: %.1f. ", yearsPart));

        // 3) Languages 0..10
        Set<String> reqLang = normalizeSet(vacancy.getLanguages());
        Set<String> gotLang = normalizeSet(resume.getLanguages());
        int langOverlap = 0;
        for (String s : reqLang) if (gotLang.contains(s)) langOverlap++;
        double langPart = reqLang.isEmpty() ? 0 : (10.0 * langOverlap / reqLang.size());
        score += langPart;
        expl.append(String.format(Locale.US, "Языки: %.1f. ", langPart));

        // 4) Education match 0..10
        double eduPart = 0;
        if (vacancy.getRequiredEducationLevel() != null && resume.getEducationLevel() != null) {
            if (normalize(vacancy.getRequiredEducationLevel()).equals(normalize(resume.getEducationLevel()))) {
                eduPart = 10;
            } else if ("master".equals(normalize(resume.getEducationLevel())) && "bachelor".equals(normalize(vacancy.getRequiredEducationLevel()))) {
                eduPart = 10; // higher degree ok
            }
        }
        score += eduPart;
        expl.append(String.format(Locale.US, "Образование: %.1f. ", eduPart));

        // 5) Seniority 0..10
        double senPart = 0;
        if (vacancy.getSeniority() != null && resume.getSeniority() != null) {
            String v = normalize(vacancy.getSeniority());
            String r = normalize(resume.getSeniority());
            if (v.equals(r)) senPart = 10;
            else if ("senior".equals(r) && ("middle".equals(v) || "junior".equals(v))) senPart = 8;
            else if ("middle".equals(r) && "junior".equals(v)) senPart = 6;
            else if ("junior".equals(r) && ("middle".equals(v) || "senior".equals(v))) senPart = 2;
        }
        score += senPart;
        expl.append(String.format(Locale.US, "Уровень: %.1f. ", senPart));

        if (score > 100) score = 100;
        return new MatchScore(Math.round(score * 10.0) / 10.0, expl.toString().trim());
    }

    private static Set<String> normalizeSet(java.util.List<String> list) {
        Set<String> set = new HashSet<>();
        if (list != null) {
            for (String s : list) if (s != null) set.add(normalize(s));
        }
        return set;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).trim();
    }
}
