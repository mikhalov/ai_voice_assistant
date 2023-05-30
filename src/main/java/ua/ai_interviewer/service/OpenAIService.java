package ua.ai_interviewer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;
import ua.ai_interviewer.dto.chatgpt.ChatGPTMessage;
import ua.ai_interviewer.dto.chatgpt.ChatGPTRequest;
import ua.ai_interviewer.dto.chatgpt.ChatGPTResponse;
import ua.ai_interviewer.dto.wisper.WisperResponse;
import ua.ai_interviewer.enums.Role;
import ua.ai_interviewer.exception.OpenAIRequestException;
import ua.ai_interviewer.exception.TooManyRequestsException;

import java.io.File;
import java.time.Duration;
import java.util.List;

import static ua.ai_interviewer.enums.ChatGPTModel.GPT_TURBO;
import static ua.ai_interviewer.enums.ChatGPTModel.WISPER;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {


    private static final String TRANSCRIPT_URI = "https://api.openai.com/v1/audio/transcriptions";
    private static final String CHAT_URI = "https://api.openai.com/v1/chat/completions";
    private static final String EMPTY_RESPONSE_GPT = "Empty response from ChatGPT";
    private static final String EMPTY_RESPONSE_WISPER = "Empty response from Wisper";
    private static final Duration RETRY_BACKOFF_DURATION = Duration.ofSeconds(10);
    private static final int MAX_RETRIES = 5;
    private static final Float TEMPERATURE = 0.7f;
    private final WebClient webClient;
    @Value("${chat.gpt.token}")
    private String apiToken;

    public ChatGPTResponse search(String search) {
        var chatGPTRequest = createChatGPTRequest(search);
        log.debug("Sending POST to ChatGPT with message '{}'", search);

        return performPostRequest(
                CHAT_URI,
                chatGPTRequest,
                ChatGPTResponse.class,
                EMPTY_RESPONSE_GPT,
                MediaType.APPLICATION_JSON
        );
    }

    public WisperResponse transcribe(File file) {
        var body = createTranscriptionRequestBody(file);
        log.debug("Sending POST to Wisper");

        return performPostRequest(TRANSCRIPT_URI, body, WisperResponse.class,
                EMPTY_RESPONSE_WISPER, MediaType.MULTIPART_FORM_DATA);
    }

    private <T> T performPostRequest(
            String uri,
            Object bodyValue,
            Class<T> responseClass,
            String errorMessage,
            MediaType mediaType
    ) {
        return webClient.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .contentType(mediaType)
                .bodyValue(bodyValue)
                .retrieve()
                .onStatus(HttpStatusCode::isError, OpenAIService::handleError)
                .bodyToMono(responseClass)
                .timeout(Duration.ofSeconds(120), Mono.error(new OpenAIRequestException("Reached timeout of request")))
                .doOnError(e -> log.error("Error during WebClient call", e))
                .retryWhen(retryAfterTooManyRequests())
                .blockOptional()
                .orElseThrow(() -> new OpenAIRequestException(errorMessage));
    }

    private static Mono<? extends RuntimeException> handleError(ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class)
                .flatMap(body -> {
                    var statusCode = clientResponse.statusCode();
                    if (statusCode == HttpStatus.TOO_MANY_REQUESTS) {
                        log.error("Too many requests error, status code: {}, body: {}", statusCode, body);
                        return Mono.error(new TooManyRequestsException("Too Many Requests"));
                    }
                    log.error("Error during ChatGPT request, status code: {}, body: {}", statusCode, body);
                    return Mono.error(new OpenAIRequestException("Error during OpenAI API request: " + statusCode));
                });
    }

    @NotNull
    private static RetryBackoffSpec retryAfterTooManyRequests() {
        return Retry.backoff(MAX_RETRIES, RETRY_BACKOFF_DURATION)
                .filter(TooManyRequestsException.class::isInstance)
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                        new TooManyRequestsException("Too Many Requests after retrying"));
    }


    private ChatGPTRequest createChatGPTRequest(String search) {
        var message = ChatGPTMessage.builder()
                .role(Role.USER.value)
                .content(search)
                .build();

        return ChatGPTRequest.builder()
                .model(GPT_TURBO.getValue())
                .temperature(TEMPERATURE)
                .messages(List.of(message))
                .build();
    }

    private MultiValueMap<String, Object> createTranscriptionRequestBody(File file) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        body.add("model", WISPER.getValue());
        return body;
    }

}

