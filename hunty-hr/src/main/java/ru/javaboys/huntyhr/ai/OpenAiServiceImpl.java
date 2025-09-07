package ru.javaboys.huntyhr.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiServiceImpl implements OpenAiService {

    private final ChatClient chatClient;

    @Override
    public String talkToChatGPT(String conversationId, SystemMessage systemMessage, UserMessage userMessage) {
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(systemMessage);
        promptMessages.add(userMessage);

        String fullResponse = chatClient
                .prompt(new Prompt(promptMessages))
                .advisors(advisor -> advisor
                        .param("chat_memory_conversation_id", conversationId)
                        .param("chat_memory_response_size", 1000))
                .call()
                .content();

        return fullResponse;
    }

    @Override
    public <T> T structuredTalkToChatGPT(String conversationId, SystemMessage systemMessage, UserMessage userMessage, Class<T> classType) {
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(systemMessage);
        promptMessages.add(userMessage);

        T fullResponse = chatClient
                .prompt(new Prompt(promptMessages))
                .advisors(advisor -> advisor
                        .param("chat_memory_conversation_id", conversationId)
                        .param("chat_memory_response_size", 1000))
                .call()
                .entity(classType);

        return fullResponse;
    }
}
