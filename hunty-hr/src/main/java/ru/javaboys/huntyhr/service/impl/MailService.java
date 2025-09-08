package ru.javaboys.huntyhr.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.jmix.core.DataManager;
import io.jmix.email.EmailInfo;
import io.jmix.email.EmailInfoBuilder;
import io.jmix.email.Emailer;
import lombok.RequiredArgsConstructor;
import ru.javaboys.huntyhr.entity.CandidateEntity;
import ru.javaboys.huntyhr.entity.InterviewSessionEntity;
import ru.javaboys.huntyhr.entity.VacancyEntity;

@Service
@RequiredArgsConstructor
public class MailService {
    private final Emailer emailer;
    private final DataManager dataManager;

    public void sendInterviewScheduled(InterviewSessionEntity interviewSession) {
        VacancyEntity vacancy = interviewSession.getApplication().getVacancy();
        CandidateEntity candidate = interviewSession.getApplication().getCandidate();
        LocalDateTime scheduledStartAt = interviewSession.getScheduledStartAt();

        String body = MailTemplates.getSchedule(
                StringUtils.joinWith(" ", candidate.getSurname(), candidate.getName()).trim(),
                scheduledStartAt,
                vacancy.getTitle(),
                ""// todo build link
        );
        sendMessageToCandidate(candidate.getEmail(), "Приглашение на интервью", body);
    }

    public void sendInterviewNotification(InterviewSessionEntity interviewSession) {
        CandidateEntity candidate = interviewSession.getApplication().getCandidate();
        long minutes = Duration.between(LocalDateTime.now(), interviewSession.getScheduledStartAt()).toMinutes();
        if (minutes < 2) {
            return;
        }

        String body = MailTemplates.getInterviewNotification(
                StringUtils.joinWith(" ", candidate.getSurname(), candidate.getName()).trim(),
                minutes,
                ""// todo build link
        );
        sendMessageToCandidate(candidate.getEmail(), "Напоминание о встрече", body);
    }

    private void sendMessageToCandidate(String to, String title, String body) {
        EmailInfo emailInfo = EmailInfoBuilder.create()
                .setAddresses(to)
                .setSubject(title)
                .setFrom(null)
                .setBody(body)
                .setImportant(true)
                .setBodyContentType("text/html; charset=UTF-8")
                .build();
        emailer.sendEmailAsync(emailInfo);
    }

}
