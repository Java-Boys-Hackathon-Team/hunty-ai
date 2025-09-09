package ru.javaboys.huntyhr.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import groovy.util.logging.Slf4j;
import io.jmix.core.DataManager;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import ru.javaboys.huntyhr.entity.CandidateEntity;
import ru.javaboys.huntyhr.entity.InterviewSessionEntity;
import ru.javaboys.huntyhr.entity.VacancyEntity;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramBotService {

    private final TelegramClient telegramClient;
    private final DataManager dataManager;

    @SneakyThrows
    public String getBotName() {
        return telegramClient.execute(new GetMe()).getUserName();
    }

    public void sendInterviewScheduled(InterviewSessionEntity interviewSession) {
        VacancyEntity vacancy = interviewSession.getApplication().getVacancy();
        CandidateEntity candidate = interviewSession.getApplication().getCandidate();
        LocalDateTime scheduledStartAt = interviewSession.getScheduledStartAt();

        String msg = TelegramTemplates.getSchedule(
                StringUtils.joinWith(" ", candidate.getSurname(), candidate.getName()).trim(),
                scheduledStartAt,
                vacancy.getTitle(),
                interviewSession.getInterviewLink()
        );
        sendMessageToCandidate(msg, candidate.getTelegramChatId());
    }

    public void sendInterviewNotification(InterviewSessionEntity interviewSession) {
        CandidateEntity candidate = interviewSession.getApplication().getCandidate();
        long minutes = Duration.between(LocalDateTime.now(), interviewSession.getScheduledStartAt()).toMinutes();
        if (minutes < 2) {
            return;
        }

        String msg = TelegramTemplates.getInterviewNotification(
                StringUtils.joinWith(" ", candidate.getSurname(), candidate.getName()).trim(),
                minutes,
                ""// todo build link
        );
        sendMessageToCandidate(msg, candidate.getTelegramChatId());
    }

    public void sendInterviewResults(InterviewSessionEntity interviewSession) {
        // todo
    }

    @SneakyThrows
    private void sendMessageToCandidate(String message, Long chatId) {
        SendMessage msg = SendMessage
                .builder()
                .chatId(chatId)
                .text(message)
                .parseMode("HTML")
                .build();
        telegramClient.execute(msg);
    }

}
