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
import ru.javaboys.huntyhr.dto.VacancyStructuredDto;
import ru.javaboys.huntyhr.entity.SeniorityLevelEnum;
import ru.javaboys.huntyhr.entity.VacancyEntity;
import ru.javaboys.huntyhr.entity.VacancyStatusEnum;
import ru.javaboys.huntyhr.service.DocParseService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VacancyImportService {

    private final DocParseService docParseService; // твой сервис c Tika
    private final OpenAiService openAiService;     // твоя обёртка над Spring AI
    private final DataManager dataManager;

    @Transactional
    public VacancyEntity importFromFile(FileRef fileRef) {
        // 1) вытащить текст JD
        String rawText = docParseService.parseToText(fileRef);

        // 2) собрать промпт
        String conversationId = "vacancy-import-" + UUID.randomUUID();

        SystemMessage system = new SystemMessage(
                """
                Ты — помощник HR. Тебе дают текст Job Description (JD).
                Верни СТРОГО одну JSON-структуру под схему:
                {
                  "title": string,
                  "seniority": "INTERN|JUNIOR|MIDDLE|SENIOR|LEAD|PRINCIPAL",
                  "status": "DRAFT|OPEN|CLOSED|ON_HOLD",
                  "description": string,
                  "responsibilities": string,
                  "requirements": string,
                  "niceToHave": string,
                  "conditions": string,
                  "weightTech": number (0..100),
                  "weightComm": number (0..100),
                  "weightCases": number (0..100)
                }
                Требования:
                - нужно сгененрировать краткое описание вакансии
                - weightTech + weightComm + weightCases = 100
                - Язык результата: русский (кроме кодовых значений seniority/status).
                - Если информации нет, оставляй поле пустым и распределяй веса по умолчанию: 60 / 25 / 15.
                """
        );

        UserMessage user = new UserMessage(
                """
                Исходный текст JD:
                -------------------
                %s
                -------------------
                Подготовь итоговую структуру.
                """.formatted(safeTrim(rawText, 18000)) // на всякий ограничим размер
        );

        // 3) вызвать LLM со структурой
        VacancyStructuredDto dto = openAiService.structuredTalkToChatGPT(
                conversationId, system, user, VacancyStructuredDto.class);

        // 4) fallback на случай null
        if (dto == null) {
            dto = new VacancyStructuredDto();
            dto.setTitle(fileRef.getFileName());
            dto.setSeniority("MIDDLE");
            dto.setStatus("DRAFT");
            dto.setWeightTech(60);
            dto.setWeightComm(25);
            dto.setWeightCases(15);
        }

        // 5) маппинг в сущность
        VacancyEntity e = dataManager.create(VacancyEntity.class);
        e.setFileRef(fileRef);
        e.setFileName(fileRef.getFileName());

        e.setTitle(nullIfBlank(dto.getTitle(), fileRef.getFileName()));
        e.setDescription(nullIfBlank(dto.getDescription(), null));
        e.setResponsibilities(nullIfBlank(dto.getResponsibilities(), null));
        e.setRequirements(nullIfBlank(dto.getRequirements(), null));
        e.setNiceToHave(nullIfBlank(dto.getNiceToHave(), null));
        e.setConditions(nullIfBlank(dto.getConditions(), null));

        // seniority → enum
        e.setSeniority(parseSeniority(dto.getSeniority()));

        // status пока строкой (или Enum позже)
        e.setStatus(VacancyStatusEnum.OPEN);

        // веса (с фикс-апом)
        int wt = nz(dto.getWeightTech(), 60);
        int wc = nz(dto.getWeightComm(), 25);
        int wk = nz(dto.getWeightCases(), 15);
        int sum = wt + wc + wk;
        if (sum != 100) {
            // нормализуем пропорционально, чтобы всё равно сохранить 100
            double k = sum == 0 ? 1.0 : 100.0 / sum;
            wt = (int)Math.round(wt * k);
            wc = (int)Math.round(wc * k);
            wk = 100 - wt - wc; // добиваем до 100
        }
        e.setWeightTech(wt);
        e.setWeightComm(wc);
        e.setWeightCases(wk);

        // 6) сохранить
        return dataManager.save(e);
    }

    private SeniorityLevelEnum parseSeniority(String s) {
        if (s == null) return SeniorityLevelEnum.MIDDLE;
        try {
            return SeniorityLevelEnum.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            // допускаем русские слова → маппинг
            String t = s.trim().toLowerCase();
            if (t.contains("стаж")) return SeniorityLevelEnum.INTERN;
            if (t.contains("джун")) return SeniorityLevelEnum.JUNIOR;
            if (t.contains("мид")) return SeniorityLevelEnum.MIDDLE;
            if (t.contains("сень") || t.contains("синь")) return SeniorityLevelEnum.SENIOR;
            if (t.contains("лид")) return SeniorityLevelEnum.LEAD;
            if (t.contains("принцип")) return SeniorityLevelEnum.PRINCIPAL;
            return SeniorityLevelEnum.MIDDLE;
        }
    }

    private static String safeTrim(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars);
    }

    private static int nz(Integer i, int def) {
        return i == null ? def : i;
    }

    private static String nullIfBlank(String s, String def) {
        if (s == null) return def;
        String t = s.trim();
        return t.isEmpty() ? def : t;
    }
}
