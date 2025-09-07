package ru.javaboys.huntyhr.service.impl;

import io.jmix.core.DataManager;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.javaboys.huntyhr.entity.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScoringService {

    private final DataManager dm;

    @Value
    public static class ScoreResult {
        int tech;
        int comm;
        int cases;
        int total;
    }

    @Transactional
    public ScoreResult scoreApplication(UUID applicationId) {
        ApplicationEntity app = dm.load(ApplicationEntity.class)
                .id(applicationId)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        VacancyEntity vac = app.getVacancy();
        CandidateEntity cand = app.getCandidate();

        // Находим последнюю версию резюме кандидата (если несколько — берем по createdAt)
        ResumeVersionEntity resume = dm.load(ResumeVersionEntity.class)
                .query("select r from ResumeVersionEntity r where r.candidate = :c order by r.createdAt desc")
                .parameter("c", cand)
                .maxResults(1)
                .optional()
                .orElse(null);

        if (resume == null) {
            // нет резюме — всё 0
            app.setTechScore(0L);
            app.setCommScore(0L);
            app.setCasesScore(0L);
            app.setTotalScore(0L);
            app.setScreeningMatchPercent(0L);
            dm.save(app);
            return new ScoreResult(0,0,0,0);
        }

        int tech  = calcTechScore(vac, resume);
        int comm  = calcCommScore(vac, resume);
        int cases = calcCasesScore(resume);

        int wT = safeInt(vac.getWeightTech(), 60);
        int wC = safeInt(vac.getWeightComm(), 25);
        int wK = safeInt(vac.getWeightCases(), 15);
        int sum = Math.max(1, wT + wC + wK);
        // нормализуем веса
        double k = 100.0 / sum;
        double nT = wT * k, nC = wC * k, nK = wK * k;

        int total = (int)Math.round(tech * (nT/100.0) + comm * (nC/100.0) + cases * (nK/100.0));

        app.setTechScore((long) tech);
        app.setCommScore((long) comm);
        app.setCasesScore((long) cases);
        app.setTotalScore((long) total);
        app.setScreeningMatchPercent((long) total);
        dm.save(app);

        return new ScoreResult(tech, comm, cases, total);
    }

    // ----------------- TECH -----------------

    private int calcTechScore(VacancyEntity vac, ResumeVersionEntity resume) {
        String vacancyText = joinTexts(
                vac.getRequirements(), vac.getNiceToHave(), vac.getDescription(), vac.getTitle()
        );

        Set<String> vacancySkills = extractSkills(vacancyText);
        if (vacancySkills.isEmpty()) return 0;

        // skills из таблицы + грубый парс всего резюме (опц. если в таблице пусто)
        Set<String> resumeSkills = resume.getSkills() != null
                ? resume.getSkills().stream()
                .map(ResumeSkillEntity::getName)
                .filter(Objects::nonNull)
                .map(this::norm)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                : new LinkedHashSet<>();

        // Дополнительно пробуем извлечь скиллы из опыта/образования (названия и summary)
        resumeSkills.addAll(extractSkills(joinExperienceEducation(resume)));

        int inter = (int) resumeSkills.stream().filter(vacancySkills::contains).count();
        double base = (inter * 1.0) / vacancySkills.size(); // 0..1

        // бонус за "сильные" термины
        Set<String> strong = Set.of("kubernetes","kafka","camunda","bpmn","k8s","spring-boot","docker","postgresql","aws","gcp","azure");
        long strongHit = resumeSkills.stream().filter(strong::contains).count();
        double bonus = Math.min(0.15, strongHit * 0.05); // макс +15%

        int score = (int)Math.round(Math.min(1.0, base + bonus) * 100.0);
        return clamp(score);
    }

    // ----------------- COMM -----------------

    private int calcCommScore(VacancyEntity vac, ResumeVersionEntity resume) {
        String text = joinExperienceEducation(resume);
        text += " " + safe(vac.getDescription());

        // ключевые маркеры коммуникаций/аналитики
        String[] markers = {
                "коммуник", "презентац", "демонстрац", "обратн", "stakeholder",
                "заинтерес", "ux", "интервью", "workshop", "custdev", "исследован",
                "аналитик", "требован", "user story", "use case", "cjm", "документац",
                "координац", "взаимодейст", "согласован", "проведени"
        };

        String low = norm(text);
        int hits = 0;
        for (String m : markers) {
            if (low.contains(m)) hits++;
        }
        // нормализация: 0..12 хитов → 0..100
        int score = (int)Math.round(Math.min(1.0, hits / 12.0) * 100.0);
        return clamp(score);
    }

    // ----------------- CASES -----------------

    private int calcCasesScore(ResumeVersionEntity resume) {
        // 1) месяцы опыта
        int months = 0;
        if (resume.getExperience() != null) {
            for (ResumeExperienceEntity e : resume.getExperience()) {
                LocalDate start = e.getStartAt();
                LocalDate end   = e.getEndDate() != null ? e.getEndDate() : LocalDate.now();
                if (start != null && end != null && !end.isBefore(start)) {
                    months += (int) ChronoUnit.MONTHS.between(start.withDayOfMonth(1), end.withDayOfMonth(1)) + 1;
                }
            }
        }
        // 36+ мес = 100, линейная шкала
        double monthsPart = Math.min(1.0, months / 36.0);

        // 2) разнообразие компаний
        int companies = 0;
        if (resume.getExperience() != null) {
            companies = (int) resume.getExperience().stream()
                    .map(ResumeExperienceEntity::getCompany)
                    .filter(Objects::nonNull)
                    .map(CompanyEntity::getName)
                    .filter(Objects::nonNull)
                    .map(this::norm)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .count();
        }
        double companiesBonus = Math.min(0.15, Math.max(0, companies - 1) * 0.05); // 2+ компаний → до +15%

        // 3) наличие “результатов” (метрики в summary — если появятся поля)
        // пока можно грубо не учитывать; оставлена точка расширения

        int score = (int)Math.round(Math.min(1.0, monthsPart + companiesBonus) * 100.0);
        return clamp(score);
    }

    // ----------------- utils -----------------

    private String joinExperienceEducation(ResumeVersionEntity resume) {
        StringBuilder sb = new StringBuilder();
        if (resume.getExperience() != null) {
            for (ResumeExperienceEntity e : resume.getExperience()) {
                if (e.getCompany() != null && e.getCompany().getName() != null) sb.append(" ").append(e.getCompany().getName());
                // если добавите title/summary — склеивайте сюда
            }
        }
        if (resume.getEducation() != null) {
            for (EducationEntity ed : resume.getEducation()) {
                if (ed.getPlace() != null) sb.append(" ").append(ed.getPlace());
                if (ed.getLevel() != null) sb.append(" ").append(ed.getLevel());
            }
        }
        return sb.toString();
    }

    private String joinTexts(String... arr) {
        StringBuilder sb = new StringBuilder();
        for (String a : arr) if (a != null) sb.append(' ').append(a);
        return sb.toString();
    }

    private Set<String> extractSkills(String text) {
        String low = norm(text);
        // токенизация по не-словесным символам
        String[] toks = NON_WORD_SPLIT.split(low);
        // примитивный стемминг/нормализация доменных штук
        return Arrays.stream(toks)
                .map(this::mapAlias)
                .filter(s -> s.length() >= 2)
                .filter(this::looksLikeSkill)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String mapAlias(String s) {
        switch (s) {
            case "js": return "javascript";
            case "ts": return "typescript";
            case "postgre": case "postgres": return "postgresql";
            case "spring": return "spring-boot"; // грубо, но для MVP
            case "k8s": return "kubernetes";
            default: return s;
        }
    }

    private boolean looksLikeSkill(String s) {
        // грубый whitelist (можно расширять)
        String[] whitelist = {
                "java","spring-boot","kotlin","gradle","maven","docker","kubernetes","k8s","helm",
                "postgresql","mysql","oracle","redis","kafka","camunda","bpmn","jira","confluence",
                "miro","figma","ux","ui","rest","graphql","git","python","sql","etl","bi","powerbi"
        };
        // разрешаем любые токены из whitelist + токены с цифрами/плюсами типа c++, .net — можно расширить
        if (Arrays.asList(whitelist).contains(s)) return true;
        if (s.contains("bpmn") || s.contains("camunda")) return true;
        if (s.equals("c++") || s.equals(".net")) return true;
        return false;
    }

    private String norm(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT);
        t = t.replace('ё','е');
        return t;
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private int safeInt(Integer v, int def) {
        return v == null ? def : v.intValue();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private static final Pattern NON_WORD_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\+\\.#]+");
}