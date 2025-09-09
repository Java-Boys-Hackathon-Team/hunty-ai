package ru.javaboys.huntyhr.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MailTemplates {

    private static final String SCHEDULE = """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
              <meta charset="UTF-8" />
              <title>Приглашение на интервью</title>
            </head>
            <body style="font-family: Arial, sans-serif; color: #333; line-height: 1.5;">
              <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                <h2 style="color:#2c3e50;">📌 Приглашение на интервью</h2>
                <p><strong>{{NAME}}</strong>, рады подтвердить вашу встречу!</p>
            
                <table style="width:100%%; border-collapse: collapse; margin: 15px 0;">
                  <tr><td><b>📅 Дата:</b></td><td>{{DATE}}</td></tr>
                  <tr><td><b>⏰ Время:</b></td><td>{{TIME}}</td></tr>
                  <tr><td><b>💼 Роль:</b></td><td>{{POSITION}}</td></tr>
                  <tr><td><b>⏳ Длительность:</b></td><td>60 минут</td></tr>
                </table>
            
                <p><b>🔗 Ссылка на встречу:</b><br>
                   <a href="{{URL}}" style="color:#1a73e8;">Перейти в комнату</a><br>
                   <span style="font-size: 0.9em; color: #555;">{{URL}}</span>
                </p>
            
                <p><b>📝 План встречи:</b><br>
                  • Короткое знакомство и вопросы о вашем опыте<br>
                  • Технический блок / кейс (при необходимости)<br>
                </p>
            
                <p><b>✅ Что подготовить:</b><br>
                  • Камера и микрофон<br>
                  • Тихое место и стабильный интернет<br>
                </p>
            
              </div>
            </body>
            </html>
            """;

    public static String getSchedule(String candidateName, LocalDateTime scheduledAt, String position, String meetingLink) {
        String date = scheduledAt.toLocalDate().format(DateTimeFormatter.ofPattern("d MMMM uuuu", new Locale("ru")));
        String time = scheduledAt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm", new Locale("ru")));

        return SCHEDULE.replace("{{NAME}}", candidateName)
                .replace("{{DATE}}", date)
                .replace("{{TIME}}", time)
                .replace("{{POSITION}}", position)
                .replace("{{URL}}", meetingLink);
    }

    private static final String INTERVIEW_NOTIFICATION = """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
              <meta charset="UTF-8" />
              <title>Напоминание о встрече</title>
            </head>
            <body style="font-family: Arial, sans-serif; color: #333; line-height: 1.5;">
              <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                <h2 style="color:#d35400;">⏰ Интервью начнётся через {{MINUTES}} минут</h2>
                <p><strong>{{NAME}}</strong>, встреча скоро начнётся!</p>
            
                <p><b>🔗 Присоединиться:</b><br>
                <a href="{{URL}}" style="color:#1a73e8;">Перейти в комнату</a><br>
                <span style="font-size: 0.9em; color: #555;">{{URL}}</span></p>
            
              </div>
            </body>
            </html>
            """;

    public static String getInterviewNotification(String candidateName, long interviewAfterMin, String meetingLink) {
        return INTERVIEW_NOTIFICATION.replace("{{MINUTES}}", Long.toString(interviewAfterMin))
                .replace("{{NAME}}", candidateName)
                .replace("{{URL}}", meetingLink);
    }

}
