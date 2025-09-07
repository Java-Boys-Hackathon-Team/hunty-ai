package ru.javaboys.huntyhr.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import ru.javaboys.huntyhr.ai.dto.ResumeInfo;
import ru.javaboys.huntyhr.ai.dto.VacancyInfo;

@Service
@RequiredArgsConstructor
public class AiExtractionService {

    private final OpenAiService openAiService;

    public ResumeInfo extractResumeInfo(String conversationId, String plainTextResume) {
        SystemMessage system = new SystemMessage("Ты помощник HR. Верни строго JSON для Java класса ResumeInfo. Пустые поля заполняй null. skills и languages — списки строк без дубликатов.");
        UserMessage user = new UserMessage("Резюме (текст):\n" + plainTextResume);
        return openAiService.structuredTalkToChatGPT(conversationId, system, user, ResumeInfo.class);
    }

    public VacancyInfo extractVacancyInfo(String conversationId, String plainTextVacancy) {
        SystemMessage system = new SystemMessage("Ты помощник HR. Верни строго JSON для Java класса VacancyInfo. Поля requiredSkills и languages — списки строк.");
        UserMessage user = new UserMessage("Вакансия (текст):\n" + plainTextVacancy);
        return openAiService.structuredTalkToChatGPT(conversationId, system, user, VacancyInfo.class);
    }
}
