package ru.javaboys.huntyhr.service.impl;

import io.jmix.core.DataManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.javaboys.huntyhr.entity.ApplicationEntity;
import ru.javaboys.huntyhr.entity.InterviewSessionEntity;
import ru.javaboys.huntyhr.entity.InterviewStateEnum;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewSessionService {

    private final DataManager dm;

    @Transactional
    public InterviewSessionEntity createOrUpdateSession(UUID applicationId,
                                                        LocalDateTime scheduledStartAt) {
        ApplicationEntity app = dm.load(ApplicationEntity.class)
                .id(applicationId)
                .one();

        // один application ⇢ одна сессия (обновляем если есть)
        InterviewSessionEntity session = dm.load(InterviewSessionEntity.class)
                .query("select s from InterviewSessionEntity s where s.application = :app")
                .parameter("app", app)
                .optional()
                .orElseGet(() -> {
                    InterviewSessionEntity s = dm.create(InterviewSessionEntity.class);
                    s.setApplication(app);
                    // подтянем сценарий из вакансии, если задан
                    if (app.getVacancy() != null) {
                        s.setScenario(app.getVacancy().getScenario());
                    }
                    return s;
                });

        session.setScheduledStartAt(scheduledStartAt);
        session.setState(InterviewStateEnum.PENDING);

        String token = UUID.randomUUID().toString();
        String link = "https://your-host/interview/" + (session.getId() != null ? session.getId() : "new")
                + "?token=" + token;
        session.setInterviewLink(link);

        return dm.save(session);
    }
}