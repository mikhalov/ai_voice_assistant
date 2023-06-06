package ua.ai_interviewer.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;
import ua.ai_interviewer.dto.chatgpt.ChatGPTRequest;
import ua.ai_interviewer.dto.chatgpt.ChatMessage;
import ua.ai_interviewer.exception.OpenAIRequestException;
import ua.ai_interviewer.exception.TokenLimitExceptions;
import ua.ai_interviewer.exception.TooManyRequestsException;
import ua.ai_interviewer.exception.UnauthorizedExeption;

import java.io.File;
import java.time.Duration;
import java.util.List;

import static ua.ai_interviewer.enums.ChatGPTModel.GPT_TURBO;
import static ua.ai_interviewer.enums.ChatGPTModel.WISPER;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WebClientUtil {
    public static final String TRANSCRIPT_URI = "https://api.openai.com/v1/audio/transcriptions";
    public static final String CHAT_URI = "https://api.openai.com/v1/chat/completions";
    private static final Duration RETRY_BACKOFF_DURATION = Duration.ofSeconds(10);
    private static final int MAX_RETRIES = 5;
    private static final Float TEMPERATURE = 0.7f;

    public static Mono<RuntimeException> handleError(ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class)
                .flatMap(body -> {
                    var statusCode = clientResponse.statusCode();
                    return switch (statusCode.value()) {
                        case 429 -> {
                            log.error("Too many requests error, status code: {}, body: {}", statusCode, body);
                            yield Mono.error(new TooManyRequestsException("Too Many Requests"));
                        }
                        case 400 -> {
                            log.error("Token limit has been reached request, status code: {}, body: {}", statusCode, body);
                            yield Mono.error(new TokenLimitExceptions("Token limit has been reached"));
                        }
                        case 401 -> {
                            log.error("Token limit has been reached request, status code: {}, body: {}", statusCode, body);
                            yield Mono.error(new UnauthorizedExeption("Invalid API key"));
                        }
                        default -> {
                            log.error("Error during ChatGPT request, status code: {}, body: {}", statusCode, body);
                            yield Mono.error(new OpenAIRequestException("Error during OpenAI API request: " + statusCode));
                        }
                    };
                });
    }

    public static RetryBackoffSpec retryAfterTooManyRequests() throws TooManyRequestsException {
        return Retry.backoff(MAX_RETRIES, RETRY_BACKOFF_DURATION)
                .filter(TooManyRequestsException.class::isInstance)
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                        new TooManyRequestsException("Too Many Requests after retrying"));
    }


    public static ChatGPTRequest createChatGPTRequest(List<ChatMessage> conversation, boolean stream) {
        return ChatGPTRequest.builder()
                .model(GPT_TURBO.getValue())
                .temperature(TEMPERATURE)
                .messages(conversation)
                .stream(stream)
                .build();
    }

    public static MultiValueMap<String, Object> createTranscriptionRequestBody(File file, String language) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        body.add("model", WISPER.getValue());
        body.add("language", language);
        return body;
    }
}
