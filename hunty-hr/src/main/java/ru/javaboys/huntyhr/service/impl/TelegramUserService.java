package ru.javaboys.huntyhr.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Update;

import io.jmix.core.DataManager;
import io.jmix.core.querycondition.PropertyCondition;
import lombok.RequiredArgsConstructor;
import ru.javaboys.huntyhr.entity.CandidateEntity;

@Component
@RequiredArgsConstructor
public class TelegramUserService {

    private final DataManager dataManager;

    @Transactional
    public void setTelegramUserId(Update update) {

        String tgUserName = update.getMessage().getFrom().getUserName();

        CandidateEntity candidateEntity = dataManager.load(CandidateEntity.class)
                .condition(PropertyCondition.equal("telegramUserName", tgUserName))
                .one();

        candidateEntity.setTelegramUserName(update.getMessage().getFrom().getUserName());
        candidateEntity.setTelegramChatId(update.getMessage().getChatId());

        dataManager.save(candidateEntity);
    }
}
