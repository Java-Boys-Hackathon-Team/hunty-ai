package ru.javaboys.huntyhr.job;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import lombok.RequiredArgsConstructor;
import ru.javaboys.huntyhr.entity.InterviewSessionEntity;
import ru.javaboys.huntyhr.service.impl.MailService;
import ru.javaboys.huntyhr.service.impl.TelegramBotService;

@Service
@RequiredArgsConstructor
public class NotificationJob {
    private final TelegramBotService telegramBotService;
    private final SystemAuthenticator authenticator;
    private final DataManager dataManager;
    private final MailService mailService;

    @Scheduled(fixedRate = 60 * 1000)
    public void telegram() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.plusMinutes(10);
        LocalDateTime to = now.plusMinutes(20);

        authenticator.runWithSystem(() -> {

            List<InterviewSessionEntity> entities = dataManager.load(InterviewSessionEntity.class)
                    .query("select s from InterviewSessionEntity s where s.scheduledStartAt between :from and :to and s.notificationSent <> true")
                    .parameter("from", from)
                    .parameter("to", to)
                    .list();

            for (InterviewSessionEntity entity : entities) {
                telegramBotService.sendInterviewNotification(entity);
                mailService.sendInterviewNotification(entity);

                entity.setNotificationSent(true);
                dataManager.save(entity);
            }

        });
    }

}