package ua.ai_interviewer.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;

@Component
public class HttpRequestUtil {

    @Value("${chat.gpt.token}")
    private String apiToken;
    private static final String CHAT_URI = "https://api.openai.com/v1/chat/completions";
    public HttpRequest httpRequestToChatGPT (String body){
        return HttpRequest.newBuilder()
                .uri(URI.create(CHAT_URI))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

}
