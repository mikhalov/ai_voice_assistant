package ua.ai_interviewer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ua.ai_interviewer.dto.chatgpt.ChatGPTResponse;
import ua.ai_interviewer.dto.chatgpt.ChatMessage;
import ua.ai_interviewer.dto.wisper.WisperResponse;
import ua.ai_interviewer.enums.Role;
import ua.ai_interviewer.exception.OpenAIRequestException;
import ua.ai_interviewer.exception.TooManyRequestsException;
import ua.ai_interviewer.service.OpenAiService;
import ua.ai_interviewer.util.WebClientUtil;

import java.io.File;
import java.time.Duration;
import java.util.List;

import static ua.ai_interviewer.util.WebClientUtil.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiServiceImpl implements OpenAiService {

    private static final String EMPTY_RESPONSE_GPT = "Empty response from ChatGPT";
    private static final String EMPTY_RESPONSE_WISPER = "Empty response from Wisper";
    private final WebClient webClient;
    @Value("${chat.gpt.token}")
    private String apiToken;


    public ChatGPTResponse search(List<ChatMessage> conversation) throws OpenAIRequestException, TooManyRequestsException {
        var chatGPTRequest = createChatGPTRequest(conversation, false);
        log.debug("Sending POST to ChatGPT");

        return performPostRequest(
                CHAT_URI,
                chatGPTRequest,
                ChatGPTResponse.class,
                EMPTY_RESPONSE_GPT,
                MediaType.APPLICATION_JSON
        );
    }

    public WisperResponse transcribe(File file) throws OpenAIRequestException, TooManyRequestsException {
        var body = createTranscriptionRequestBody(file);
        log.debug("Sending POST to Wisper");

        return performPostRequest(TRANSCRIPT_URI, body, WisperResponse.class,
                EMPTY_RESPONSE_WISPER, MediaType.MULTIPART_FORM_DATA);
    }

    public ChatMessage createMessage(String content) {
        return ChatMessage.builder()
                .role(Role.USER.value)
                .content(content)
                .build();
    }

    private <T> T performPostRequest(
            String uri,
            Object bodyValue,
            Class<T> responseClass,
            String errorMessage,
            MediaType mediaType) throws OpenAIRequestException, TooManyRequestsException {
        return webClient.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .contentType(mediaType)
                .bodyValue(bodyValue)
                .retrieve()
                .onStatus(HttpStatusCode::isError, WebClientUtil::handleError)
                .bodyToMono(responseClass)
                .timeout(Duration.ofSeconds(120),
                        Mono.error(new OpenAIRequestException("Reached timeout of request")))
                .doOnError(e -> log.error("Error during WebClient call", e))
                .retryWhen(retryAfterTooManyRequests())
                .blockOptional()
                .orElseThrow(() -> new OpenAIRequestException(errorMessage));
    }


}

