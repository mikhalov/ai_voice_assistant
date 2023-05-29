package ua.ai_interviewer.util;

import lombok.RequiredArgsConstructor;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.http.HttpRequest;

@Component
@RequiredArgsConstructor
public class HttpRequestUtil {

    private static final String TRANSCRIPT_URI = "https://api.openai.com/v1/audio/transcriptions";
    private static final String CHAT_URI = "https://api.openai.com/v1/chat/completions";
    @Value("${chat.gpt.token}")
    private String apiToken;

    public HttpRequest httpRequestToChatGPT (String body){
        return HttpRequest.newBuilder()
                .uri(URI.create(CHAT_URI))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    public Request requestToTranscribe(RequestBody requestBody) {
        return new Request.Builder()
                .url(TRANSCRIPT_URI)
                .post(requestBody)
                .addHeader("Authorization", "Bearer " + apiToken)
                .build();
    }



}
