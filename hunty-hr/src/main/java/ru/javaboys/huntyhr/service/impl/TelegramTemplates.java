package ru.javaboys.huntyhr.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TelegramTemplates {
    private static final String SCHEDULE = """
            <b>📌 Приглашение на интервью</b>
            <u>%s</u>, рады подтвердить вашу встречу!
            
            <b>📅 Дата:</b> %s
            <b>⏰ Время:</b> %s
            <b>💼 Роль:</b> %s
            <b>⏳ Ориентировочная длительность:</b> 60 минут
            
            <b>🔗 Ссылка на встречу:</b> <a href="%s">Встреча</a>
            
            <b>📝 План встречи:</b>
            • Короткое знакомство и вопросы о вашем опыте \s
            • Технический блок / кейс (при необходимости) \s
            
            <b>✅ Что подготовить:</b>
            • Доступ к камере и микрофону \s
            • Тихое место и стабильный интернет \s
            """;

    public static String getSchedule(String candidateName, LocalDateTime scheduledAt, String position, String meetingLink) {
        String date = scheduledAt.toLocalDate().format(DateTimeFormatter.ofPattern("d MMMM uuuu", new Locale("ru")));
        String time = scheduledAt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm", new Locale("ru")));

        return SCHEDULE.formatted(candidateName, date, time, position, meetingLink);
    }

    private static final String INTERVIEW_NOTIFICATION = """
            <b>⏰ Напоминание: интервью через %s минут</b>
            <u>%s</u>, встреча скоро начнётся!
            
            <b>🔗 Присоединиться:</b> <a href="%s">Встреча</a>
            """;
    public static String getInterviewNotification(String candidateName, long interviewAfterMin, String meetingLink) {
        return INTERVIEW_NOTIFICATION.formatted(interviewAfterMin, candidateName, meetingLink);
    }

}
