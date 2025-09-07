package ru.javaboys.huntyhr.ai;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

public interface OpenAiService {
    String talkToChatGPT(String conversationId, SystemMessage systemMessage, UserMessage userMessage);
    <T> T structuredTalkToChatGPT(String conversationId, SystemMessage systemMessage, UserMessage userMessage, Class<T> classType);
}
