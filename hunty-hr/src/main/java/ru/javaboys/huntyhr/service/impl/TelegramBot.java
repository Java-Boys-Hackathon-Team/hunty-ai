package ru.javaboys.huntyhr.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import io.jmix.core.NoResultException;
import io.jmix.core.security.SystemAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class TelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    @Value("${telegram.bot.token}")
    private String token;

    private final TelegramClient telegramClient;
    private final SystemAuthenticator systemAuthenticator;
    private final TelegramUserService telegramUserService;

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @SneakyThrows
    @Override
    @Transactional
    public void consume(Update update) {

        systemAuthenticator.begin("admin");

        try {
            String input = update.getMessage().getText();
            if (input.startsWith("/start")) {
                processSetup(update);
            }

        } finally {
            systemAuthenticator.end();
        }
    }

    private void processSetup(Update update) throws TelegramApiException {

        try {
            telegramUserService.setTelegramUserId(update);

            sendHtmlMessage(update, "<b>Telegram успешно привязан.</b>\nТеперь вы будете получать все важные уведомления на ваш telegram аккаунт");
        } catch (NoResultException e) {
            log.error("No telegram user found for update {}", update, e);
            sendHtmlMessage(update, "<b>Вас еще нет в нашей системе.</b>");
        }
    }

    private void sendHtmlMessage(Update update, String text) throws TelegramApiException {
        long chatId = update.getMessage().getChatId();
        SendMessage message = SendMessage
                .builder()
                .chatId(chatId)
                .parseMode("HTML")
                .text(text)
                .build();
        telegramClient.execute(message);
    }

    @AfterBotRegistration
    public void afterRegistration(BotSession botSession) {
        log.info("Registered Bot running state is: {}", botSession.isRunning());
    }
}