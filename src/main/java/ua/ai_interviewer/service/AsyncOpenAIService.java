package ua.ai_interviewer.service;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import ua.ai_interviewer.dto.chatgpt.ChatMessage;

import java.util.List;

public interface AsyncOpenAIService {

    Flux<ServerSentEvent<String>> getResponseFromChatGpt(List<ChatMessage> conversation);
}
