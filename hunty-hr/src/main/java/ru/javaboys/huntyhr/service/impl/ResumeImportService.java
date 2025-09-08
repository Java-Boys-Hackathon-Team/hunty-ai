package ru.javaboys.huntyhr.service.impl;

import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.javaboys.huntyhr.ai.OpenAiService;
import ru.javaboys.huntyhr.dto.ResumeStructuredDto;
import ru.javaboys.huntyhr.entity.ApplicationEntity;
import ru.javaboys.huntyhr.entity.ApplicationStatusEnum;
import ru.javaboys.huntyhr.entity.CandidateEntity;
import ru.javaboys.huntyhr.entity.CompanyEntity;
import ru.javaboys.huntyhr.entity.EducationEntity;
import ru.javaboys.huntyhr.entity.EducationLevelEnum;
import ru.javaboys.huntyhr.entity.ResumeExperienceEntity;
import ru.javaboys.huntyhr.entity.ResumeSkillEntity;
import ru.javaboys.huntyhr.entity.ResumeSourceType;
import ru.javaboys.huntyhr.entity.ResumeVersionEntity;
import ru.javaboys.huntyhr.entity.SexEnum;
import ru.javaboys.huntyhr.entity.StorageObjectEntity;
import ru.javaboys.huntyhr.entity.VacancyEntity;
import ru.javaboys.huntyhr.service.DocParseService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeImportService {

    private final DataManager dm;
    private final DocParseService docParseService;
    private final OpenAiService openAiService;

    @Value
    public static class Result {
        UUID applicationId;
        String resumeText;
    }

    @Transactional
    public Result importFromFile(UUID vacancyId, FileRef fileRef) {
        // 1) текст резюме
        String rawText = docParseService.parseToText(fileRef);
        String safe = safeTrim(rawText, 18000);

        // 2) LLM → DTO
        String conversationId = "resume-import-" + UUID.randomUUID();

        SystemMessage system = new SystemMessage("""
                Ты извлекаешь данные кандидата из РЕЗЮМЕ (RU).
                Верни РОВНО ОДИН JSON (без лишнего текста) по схеме:
                {
                  "name": "string", "surname": "string",
                  "birthDate": "yyyy-MM-dd" | "",
                  "sex": "M" | "F" | "",
                  "email": "string", "phone": "string",
                  "telegram": "string", "linkedin": "string",
                  "skills": ["string", ...],
                  "education": [
                    {"level":"string","place":"string","startDate":"yyyy-MM|yyyy|","endDate":"yyyy-MM|yyyy|"}
                  ],
                  "experience": [
                    {"company":"string","title":"string","startDate":"yyyy-MM|yyyy|","endDate":"yyyy-MM|yyyy|настоящее время|","summary":"string"}
                  ]
                }
                
                Правила:
                - education.level = SECONDARY, VOCATIONAL_SECONDARY, INCOMPLETE_HIGHER,BACHELOR,SPECIALIST,MASTER,CANDIDATE_OF_SCIENCES,DOCTOR_OF_SCIENCES;
                - Если поле отсутствует — верни "" (или [] для списков).
                - Email: [\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}
                - Telegram: '@username' или 't.me/username' → верни username без '@'.
                - Телефон оставляй как в тексте.
                - Даты старайся нормализовать к "yyyy-MM"; если есть только год — "yyyy".
                - Для "настоящее время" в experience.endDate верни строку "настоящее время".
                - skills разделяй по запятым/переносам строк/2+ пробелам.
                """);

        UserMessage user = new UserMessage("""
                Файл: %s
                Текст резюме:
                ----------------
                %s
                """.formatted(fileRef.getFileName(), safe));

        ResumeStructuredDto dto = openAiService.structuredTalkToChatGPT(
                conversationId, system, user, ResumeStructuredDto.class);

        if (dto == null) dto = new ResumeStructuredDto(); // всё опционально

        // 3) upsert кандидата (все контакты и пол — опциональны)
        CandidateEntity candidate = upsertCandidate(dto);

        // 4) файл
        StorageObjectEntity fileObj = dm.create(StorageObjectEntity.class);
        fileObj.setCreatedAt(LocalDateTime.now());
        fileObj.setRef(fileRef); // предполагаем, что поле типизировано FileRef
        fileObj = dm.save(fileObj);

        // 5) версия резюме
        ResumeVersionEntity version = dm.create(ResumeVersionEntity.class);
        version.setCandidate(candidate);
        version.setCreatedAt(LocalDateTime.now());
        version.setSourceType(ResumeSourceType.MANUAL);
        version.setFile(fileObj);
        version = dm.save(version);

        // 6) навыки (опционально)
        if (dto.getSkills() != null) {
            for (String s : dto.getSkills()) {
                if (s == null) continue;
                String name = s.trim();
                if (name.isEmpty()) continue;
                ResumeSkillEntity skill = dm.create(ResumeSkillEntity.class);
                skill.setName(name);
                skill.setResumeVersionEntity(version);
                dm.save(skill);
            }
        }

        // ----- EDUCATION -----
        if (dto.getEducation() != null) {
            for (var ed : dto.getEducation()) {
                if (ed == null) continue;
                String place = nz(ed.getPlace());
                String level = nz(ed.getLevel());
                if (place == null && level == null) continue;

                EducationEntity e = dm.create(EducationEntity.class);
                e.setResumeVersionEntity(version);
                e.setPlace(place);  // строки могут быть длинные — у тебя @Lob ок
                e.setLevel(EducationLevelEnum.fromId(level));

                // необязательные даты — парсим мягко
                LocalDate start = parseYearOrYearMonth(ed.getStartDate());
                LocalDate end   = parseYearOrYearMonth(ed.getEndDate());
                // если в твоей модели у EducationEntity нет полей дат — пропусти эти строки.
                // Если планируешь добавить START_AT/END_DATE — распакуй как ниже:
                // e.setStartAt(start);
                // e.setEndDate(end);

                dm.save(e);
            }
        }

// ----- EXPERIENCE -----
        if (dto.getExperience() != null) {
            for (var ex : dto.getExperience()) {
                if (ex == null) continue;
                String companyName = nz(ex.getCompany());
                if (companyName == null) continue;

                // upsert по компании (по имени, без чувствительности к регистру)
                CompanyEntity company = dm.load(CompanyEntity.class)
                        .query("select c from CompanyEntity c where lower(c.name)=:n")
                        .parameter("n", companyName.toLowerCase(Locale.ROOT))
                        .optional()
                        .orElseGet(() -> {
                            CompanyEntity c = dm.create(CompanyEntity.class);
                            c.setName(companyName);
                            return dm.save(c);
                        });

                ResumeExperienceEntity re = dm.create(ResumeExperienceEntity.class);
                re.setResumeVersionEntity(version);
                re.setCompany(company);

                // даты
                LocalDate start = parseYearOrYearMonth(ex.getStartDate());
                LocalDate end   = parseExperienceEnd(ex.getEndDate());
                re.setStartAt(start);
                re.setEndDate(end);

                // если планируешь расширение модели, можно хранить title/summary
                // Добавь поля и здесь:
                // re.setTitle(nz(ex.getTitle()));
                // re.setSummary(nz(ex.getSummary()));

                dm.save(re);
            }
        }

        // 7) заявка
        if (vacancyId == null) {
            throw new IllegalArgumentException("vacancyId must not be null");
        }

        VacancyEntity vacancy = dm.load(VacancyEntity.class)
                .id(vacancyId)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vacancy not found by id=" + vacancyId
                ));

        // не создаём дубликат, если уже есть заявка на эту вакансию
        var existingAppOpt = dm.load(ApplicationEntity.class)
                .query("select a from ApplicationEntity a where a.vacancy = :v and a.candidate = :c")
                .parameter("v", vacancy)
                .parameter("c", candidate)
                .optional();

        ApplicationEntity app = existingAppOpt.orElseGet(() -> {
            ApplicationEntity a = dm.create(ApplicationEntity.class);
            a.setCandidate(candidate);
            a.setVacancy(vacancy);
            a.setStatus(ApplicationStatusEnum.NEW);
            a.setScreeningMatchPercent(0L);
            a.setTechScore(0L);
            a.setCommScore(0L);
            a.setCasesScore(0L);
            a.setTotalScore(0L);
            return dm.save(a);
        });

        // если нужно вернуть id существующей/новой заявки:
        return new Result(app.getId(), rawText);
    }

    private CandidateEntity upsertCandidate(ResumeStructuredDto dto) {
        // Нормализация (всё опционально!)
        String name = nz(dto.getName());
        String surname = nz(dto.getSurname());
        LocalDate bdate = parseDate(dto.getBirthDate());
        SexEnum sex = parseSex(dto.getSex());
        String emailNorm = normalizeEmail(dto.getEmail());
        String phoneNorm = normalizePhone(dto.getPhone()); // только цифры или null
        String tgNorm = normalizeTelegram(dto.getTelegram()); // без @, lower

        CandidateEntity found = null;

        if (emailNorm != null) {
            found = dm.load(CandidateEntity.class)
                    .query("select c from CandidateEntity c where lower(c.email) = :e")
                    .parameter("e", emailNorm)
                    .optional().orElse(null);
        }
        if (found == null && phoneNorm != null) {
            found = dm.load(CandidateEntity.class)
                    .query("select c from CandidateEntity c where c.phone = :p")
                    .parameter("p", phoneNorm)
                    .optional().orElse(null);
        }
        if (found == null && tgNorm != null) {
            found = dm.load(CandidateEntity.class)
                    .query("select c from CandidateEntity c where lower(c.telegramUserName) = :tg")
                    .parameter("tg", tgNorm)
                    .optional().orElse(null);
        }

        if (found != null) {
            if (isBlank(found.getName()) && name != null) found.setName(name);
            if (isBlank(found.getSurname()) && surname != null) found.setSurname(surname);
            if (found.getBirthDate() == null && bdate != null) found.setBirthDate(bdate);
            if (found.getSex() == null && sex != null) found.setSex(sex);
            if (isBlank(found.getEmail()) && emailNorm != null) found.setEmail(emailNorm);
            if (isBlank(found.getPhone()) && phoneNorm != null) found.setPhone(phoneNorm);
            if (isBlank(found.getTelegramUserName()) && tgNorm != null) found.setTelegramUserName(tgNorm);
            if (isBlank(found.getLinkedin()) && nz(dto.getLinkedin()) != null) found.setLinkedin(nz(dto.getLinkedin()));
            return dm.save(found);
        }

        // создаём нового (всё поле — опционально)
        CandidateEntity c = dm.create(CandidateEntity.class);
        c.setName(name);
        c.setSurname(surname);
        c.setBirthDate(bdate);
        c.setSex(sex);                 // может быть null
        c.setEmail(emailNorm);         // может быть null
        c.setPhone(phoneNorm);         // может быть null
        c.setTelegramUserName(tgNorm);         // может быть null
        c.setLinkedin(nz(dto.getLinkedin()));
        return dm.save(c);
    }

    // ---------- helpers ----------

    private static SexEnum parseSex(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase(Locale.ROOT);
        if (t.isEmpty()) return null;
        // Поддержим русские варианты, чтобы не падать:
        if (t.equals("М") || t.equals("MALE") || t.equals("МУЖ") || t.equals("МУЖЧИНА")) return SexEnum.MALE;
        if (t.equals("Ж") || t.equals("FEMALE") || t.equals("ЖЕН") || t.equals("ЖЕНЩИНА")) return SexEnum.FEMALE;
        // если модель вернула "M"/"F" — полагаемся на enum с такими значениями
        try {
            return SexEnum.valueOf(t);
        } catch (Exception ignore) {
            return null; // НЕ кидаем, поле остаётся пустым
        }
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeEmail(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t.toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String p) {
        if (p == null) return null;
        String digits = p.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private static String normalizeTelegram(String t) {
        if (t == null) return null;
        String v = t.trim();
        if (v.isEmpty()) return null;
        v = v.replace("@", "");
        // поддержим ссылки вида t.me/user
        int idx = v.toLowerCase(Locale.ROOT).indexOf("t.me/");
        if (idx >= 0) v = v.substring(idx + "t.me/".length());
        v = v.trim();
        return v.isEmpty() ? null : v.toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nz(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String safeTrim(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max));
    }

    private static LocalDate parseYearOrYearMonth(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        try {
            if (t.matches("\\d{4}-\\d{2}")) {          // yyyy-MM
                return LocalDate.parse(t + "-01");     // приводим к yyyy-MM-01
            } else if (t.matches("\\d{4}")) {          // yyyy
                return LocalDate.parse(t + "-01-01");  // год -> 1 января
            } else {
                // попытка распознать русские месяцы типа "Октябрь 2024"
                LocalDate m = parseRuMonthYear(t);
                if (m != null) return m;
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static LocalDate parseExperienceEnd(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.contains("настояще")) return null; // open-ended
        return parseYearOrYearMonth(s);
    }

    private static LocalDate parseRuMonthYear(String s) {
        // very light-weight parser "Месяц yyyy" -> yyyy-MM-01
        String t = s.trim().toLowerCase(Locale.ROOT);
        String[] months = {
                "январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр"
        };
        int month = -1;
        for (int i = 0; i < months.length; i++) {
            if (t.contains(months[i])) { month = i + 1; break; }
        }
        String year = null;
        var m = java.util.regex.Pattern.compile("(20\\d{2}|19\\d{2})").matcher(t);
        if (m.find()) year = m.group(1);
        if (month > 0 && year != null) {
            String mm = (month < 10 ? "0" : "") + month;
            return LocalDate.parse(year + "-" + mm + "-01");
        }
        return null;
    }
}