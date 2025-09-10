package ru.javaboys.huntyhr.service.impl;

import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.javaboys.huntyhr.ai.OpenAiService;
import ru.javaboys.huntyhr.dto.ScoreDto;
import ru.javaboys.huntyhr.dto.ScreeningReportDto;
import ru.javaboys.huntyhr.entity.ApplicationEntity;
import ru.javaboys.huntyhr.entity.CandidateEntity;
import ru.javaboys.huntyhr.entity.ResumeEducationEntity;
import ru.javaboys.huntyhr.entity.ResumeExperienceEntity;
import ru.javaboys.huntyhr.entity.ResumeVersionEntity;
import ru.javaboys.huntyhr.entity.VacancyEntity;
import ru.javaboys.huntyhr.service.DocParseService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScoringService {

    private final DataManager dm;
    private final OpenAiService openAiService;
    private final DocParseService docParseService; // уже есть у тебя

    /**
     * LLM-скоринг: JD + Резюме → структурные баллы + отчёт.
     * Сохраняет в ApplicationEntity: tech/comm/cases, total (по весам вакансии), screeningSummary, screeningMatchPercent.
     */
    @Transactional
    public ScoreDto scoreWithLlm(UUID applicationId) {
        ApplicationEntity app = dm.load(ApplicationEntity.class)
                .id(applicationId).optional()
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        VacancyEntity vac = app.getVacancy();
        CandidateEntity cand = app.getCandidate();

        // Берем последнюю версию резюме
        ResumeVersionEntity resume = dm.load(ResumeVersionEntity.class)
                .query("select r from ResumeVersionEntity r where r.candidate = :c order by r.createdAt desc")
                .parameter("c", cand)
                .maxResults(1)
                .optional().orElse(null);

        if (resume == null || resume.getFile() == null || resume.getFile().getRef() == null) {
            // Нет резюме — нули и короткое пояснение
            app.setTechScore(0L);
            app.setCommScore(0L);
            app.setCasesScore(0L);
            app.setTotalScore(0L);
            app.setScreeningMatchPercent(0L);
            app.setScreeningSummary("Скоринг не выполнен: отсутствует файл резюме.");
            dm.save(app);
            return null;
        }

        // JD текст
        String jd = buildJdText(vac);
        // Резюме-raw (из файла)
        FileRef ref = resume.getFile().getRef();
        String resumeRaw = safeTrim(docParseService.parseToText(ref), 18000);

        // Промпт
        String conversationId = "llm-score-" + UUID.randomUUID();
        SystemMessage system = new SystemMessage("""
                Ты — ассистент по найму. Тебе дают текст вакансии (JD) и текст резюме (RU).
                Оцени кандидата по трем осям: tech/comm/cases — каждое целое число 0..100.
                Верни РОВНО ОДИН JSON формата:
                {
                  "tech": 0..100,
                  "comm": 0..100,
                  "cases": 0..100,
                  "total": 0..100,
                  "matches": ["пункт1", "пункт2", ...],
                  "gaps": ["пункт1", ...],
                  "redFlags": ["пункт1", ...]
                }
                Правила:
                - tech: соотнесение навыков/технологий/доменных знаний с JD.
                - comm: коммуникативные и аналитические компетенции (требования, work with stakeholders, UX/CJM, документация и т.п.).
                - cases: опыт (сроки, разнообразие, роль/вклад, заметные результаты).
                - Если информации мало — ставь реалистичные низкие баллы и поясняй в summary.
                - Строго JSON, без комментариев и лишнего текста.
                """);

        UserMessage user = new UserMessage("""
                JD (вакансия):
                ----------------
                %s

                Резюме (raw):
                ----------------
                %s
                """.formatted(safeTrim(jd, 8000), resumeRaw));

        // Вызов LLM со структурным ответом
        ScoreDto dto = null;
        try {
            dto = openAiService.structuredTalkToChatGPT(conversationId, system, user, ScoreDto.class);
        } catch (Exception e) {
            log.warn("LLM scoring failed: {}", e.getMessage());
        }

        if (dto == null) {
            // Не ответила — не падаем, просто отмечаем
            app.setScreeningSummary("LLM-скоринг: не удалось получить ответ от модели.");
            dm.save(app);
            return null;
        }

        // Защита от мусора и нормализация
        int tech  = clamp(nz(dto.getTech(), 0));
        int comm  = clamp(nz(dto.getComm(), 0));
        int cases = clamp(nz(dto.getCases(), 0));

        // Считаем total на бэке по весам вакансии
        int wT = safeInt(vac.getWeightTech(), 60);
        int wC = safeInt(vac.getWeightComm(), 25);
        int wK = safeInt(vac.getWeightCases(), 15);
        int sum = Math.max(1, wT + wC + wK);
        double k = 100.0 / sum;
        double nT = wT * k, nC = wC * k, nK = wK * k;
        int total = (int) Math.round(tech * (nT/100.0) + comm * (nC/100.0) + cases * (nK/100.0));

        // Сохраняем в Application
        app.setTechScore((long) tech);
        app.setCommScore((long) comm);
        app.setCasesScore((long) cases);
        app.setTotalScore((long) total);
        app.setScreeningMatchPercent((long) total);
        dm.save(app);

        // Перезаписываем total в dto «как у нас на бэке»
        dto.setTotal(total);

        // собираем отчет
        try {
            fillLlmReport(app, vac, resume);
        } catch (Exception ex) {
            log.warn("LLM screening failed for app {}: {}", applicationId, ex.getMessage());
            app.setScreeningSummary("Анализ временно недоступен. Попробуйте «Пересчитать анализ».");
            dm.save(app);
        }

        return dto;
    }

    // -------- helpers --------

    private String buildJdText(VacancyEntity v) {
        StringBuilder sb = new StringBuilder();
        if (v.getTitle() != null) sb.append("Название: ").append(v.getTitle()).append('\n');
        if (v.getSeniority() != null) sb.append("Уровень: ").append(v.getSeniority()).append('\n');
        if (v.getDescription() != null) sb.append("\nОписание:\n").append(v.getDescription()).append('\n');
        if (v.getResponsibilities() != null) sb.append("\nОбязанности:\n").append(v.getResponsibilities()).append('\n');
        if (v.getRequirements() != null) sb.append("\nТребования:\n").append(v.getRequirements()).append('\n');
        if (v.getNiceToHave() != null) sb.append("\nЖелательно:\n").append(v.getNiceToHave()).append('\n');
        if (v.getConditions() != null) sb.append("\nУсловия:\n").append(v.getConditions()).append('\n');
        return sb.toString();
    }

    private void fillLlmReport(ApplicationEntity app, VacancyEntity vac, ResumeVersionEntity resume) {
        // грубо соберём тексты
        String jd = joinTexts(vac.getTitle(), vac.getDescription(), vac.getResponsibilities(),
                vac.getRequirements(), vac.getNiceToHave());
        String cv = joinExperienceEducation(resume);

        var system = new SystemMessage("""
Ты — помощник рекрутера. Сопоставь требования вакансии и резюме.
Верни СТРОГО ОДИН JSON по схеме:
{
  "overall": string (2-3 предложения, на русском),
  "strengths": [string],
  "gaps": [string],
  "hardMatches": [string],
  "softMatches": [string],
  "risks": [string],
  "recommendations": [string]
}
""");

        var user = new UserMessage("""
        ВАКАНСИЯ (JD):
        -------------
        %s

        РЕЗЮМЕ:
        -------
        %s
    """.formatted(safe(jd), safe(cv)));

        ScreeningReportDto dto = openAiService.structuredTalkToChatGPT(
                "screening-" + app.getId(), system, user, ScreeningReportDto.class);

        if (dto != null) {
            // сохраним красивую «человеческую» выжимку (для быстрого чтения)
            app.setScreeningSummary(renderSummary(dto));
            app.setScreeningSummaryHtml(renderSummaryHtml(dto));

            // если хочешь хранить исходный JSON:
            // app.setScreeningJson(objectMapper.writeValueAsString(dto));
            dm.save(app);
        }
    }

    private String renderSummary(ScreeningReportDto d) {
        StringBuilder sb = new StringBuilder();
        if (d.getOverall() != null && !d.getOverall().isBlank()) {
            sb.append("Итог: ").append(d.getOverall().trim()).append("\n\n");
        }
        appendBullets(sb, "Сильные стороны", d.getStrengths());
        appendBullets(sb, "Пробелы", d.getGaps());
        appendBullets(sb, "Совпадения (hard)", d.getHardMatches());
        appendBullets(sb, "Совпадения (soft/процессы)", d.getSoftMatches());
        appendBullets(sb, "Риски", d.getRisks());
        appendBullets(sb, "Рекомендации для интервью", d.getRecommendations());
        return sb.toString().trim();
    }

    private void appendBullets(StringBuilder sb, String title, List<String> items) {
        if (items == null || items.isEmpty()) return;
        sb.append(title).append(":\n");
        for (String it : items) {
            if (it != null && !it.isBlank()) {
                sb.append(" • ").append(it.trim()).append("\n");
            }
        }
        sb.append("\n");
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static int nz(Integer v, int def) {
        return v == null ? def : v;
    }

    private static String safeTrim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static int safeInt(Integer v, int def) {
        return v == null ? def : v.intValue();
    }

    private String joinTexts(String... arr) {
        StringBuilder sb = new StringBuilder();
        for (String a : arr) {
            if (a != null && !a.isBlank()) {
                sb.append(' ').append(a.trim());
            }
        }
        return sb.toString().trim();
    }

    // Собираем текстовое представление опыта и образования из сущностей резюме
    private String joinExperienceEducation(ResumeVersionEntity resume) {
        StringBuilder sb = new StringBuilder();

        if (resume.getExperience() != null) {
            for (ResumeExperienceEntity e : resume.getExperience()) {
                if (e.getCompany() != null && e.getCompany().getName() != null) {
                    sb.append(" ").append(e.getCompany().getName());
                }
                if (e.getStartAt() != null) {
                    sb.append(" ").append(e.getStartAt());
                }
                if (e.getEndDate() != null) {
                    sb.append(" ").append(e.getEndDate());
                }
            }
        }

        if (resume.getEducation() != null) {
            for (ResumeEducationEntity ed : resume.getEducation()) {
                if (ed.getPlace() != null) {
                    sb.append(" ").append(ed.getPlace());
                }
                if (ed.getLevel() != null) {
                    sb.append(" ").append(ed.getLevel());
                }
            }
        }

        return sb.toString().trim();
    }

    // Безопасно обрабатываем null
    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String renderSummaryHtml(ScreeningReportDto d) {
        StringBuilder html = new StringBuilder();
        html.append("""
      <section style="font-family:system-ui,Segoe UI,Roboto,Arial,sans-serif;line-height:1.45">
        <div style="display:flex;align-items:center;gap:.6rem;margin-bottom:.6rem">
          <div style="font-weight:600;font-size:1.1rem;">Анализ резюме</div>
          <span style="padding:.15rem .5rem;border-radius:.5rem;font-size:.85rem;">
    """);

        if (notBlank(d.getOverall())) {
            html.append("<p style='margin:.3rem 0 1rem 0;'>")
                    .append(esc(d.getOverall().trim()))
                    .append("</p>");
        }

        html.append(block("Сильные стороны", d.getStrengths()));
        html.append(block("Пробелы", d.getGaps()));
        html.append(block("Совпадения (hard)", d.getHardMatches()));
        html.append(block("Совпадения (soft/процессы)", d.getSoftMatches()));
        html.append(block("Риски", d.getRisks()));
        html.append(block("Рекомендации для интервью", d.getRecommendations()));
        html.append("</section>");
        return html.toString();
    }

    private String block(String title, List<String> items) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        b.append("<div style='margin:.8rem 0;'>")
                .append("<div style='font-weight:600;margin-bottom:.25rem;'>").append(esc(title)).append("</div>")
                .append("<ul style='margin:.2rem 0 .2rem 1.1rem;padding:0;'>");
        for (String it : items) if (notBlank(it)) b.append("<li>").append(esc(it.trim())).append("</li>");
        b.append("</ul></div>");
        return b.toString();
    }
    private static boolean notBlank(String s){ return s!=null && !s.isBlank(); }
    private static String esc(String s){ return s.replace("&","&amp;").replace("<","&lt;")
            .replace(">","&gt;").replace("\"","&quot;")
            .replace("'","&#39;"); }
}