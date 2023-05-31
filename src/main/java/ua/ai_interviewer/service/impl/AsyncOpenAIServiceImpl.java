package ua.ai_interviewer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ua.ai_interviewer.dto.chatgpt.ChatMessage;
import ua.ai_interviewer.exception.OpenAIRequestException;
import ua.ai_interviewer.util.WebClientUtil;

import java.time.Duration;
import java.util.List;

import static ua.ai_interviewer.util.WebClientUtil.CHAT_URI;
import static ua.ai_interviewer.util.WebClientUtil.createChatGPTRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncOpenAIServiceImpl {


    private final WebClient webClient;
    @Value("${chat.gpt.token}")
    private String apiToken;


    public Flux<ServerSentEvent<String>> getResponseFromChatGpt(List<ChatMessage> conversation) {
        var chatGPTRequest = createChatGPTRequest(conversation, true);
        log.debug("Sending POST to ChatGPT");
        log.debug("request {}", chatGPTRequest);

        return performPostRequest(chatGPTRequest);
    }

    private Flux<ServerSentEvent<String>> performPostRequest(Object bodyValue) {
        ParameterizedTypeReference<ServerSentEvent<String>> type
                = new ParameterizedTypeReference<>() {
        };

        return webClient.post()
                .uri(CHAT_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bodyValue)
                .retrieve()
                .onStatus(HttpStatusCode::isError, WebClientUtil::handleError)
                .bodyToFlux(type);

    }

}

