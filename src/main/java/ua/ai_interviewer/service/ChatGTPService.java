package ua.ai_interviewer.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.ai_interviewer.enums.ChatGPTModel;
import ua.ai_interviewer.enums.Role;
import ua.ai_interviewer.util.HttpRequestUtil;
import ua.ai_interviewer.dto.chatgpt.ChatGPTMessages;
import ua.ai_interviewer.dto.chatgpt.ChatGPTRequest;
import ua.ai_interviewer.dto.chatgpt.ChatGPTResponse;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service
@Slf4j
public class ChatGTPService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final HttpRequestUtil httpRequestUtil;

    public ChatGTPService (HttpClient httpClient, ObjectMapper objectMapper, HttpRequestUtil httpRequestUtil) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.httpRequestUtil = httpRequestUtil;
    }

    @Transactional(readOnly = true)
    public ChatGPTResponse search(String search) {
        try {

            var messages = ChatGPTMessages.builder()
                    .role(Role.USER.value)
                    .content(search)
                    .build();
            var chatGPTRequest = ChatGPTRequest.builder()
                    .model(ChatGPTModel.GPT_TURBO.getValue())
                    .temperature(0.7f)
                    .messages(List.of( messages))
                    .build();

            HttpRequest httpRequest = httpRequestUtil.httpRequestToChatGPT(objectMapper.writeValueAsString(chatGPTRequest));

            log.info("Http request for to chat GPT is sending");

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            log.info("Response from chat GPT {} , {}",response,response.body());

            var chatGPTResponse = objectMapper.readValue(response.body(), ChatGPTResponse.class);
            chatGPTResponse.setHttpStatus(HttpStatus.valueOf(response.statusCode()));

            log.info("converted response {}",chatGPTResponse);
            return chatGPTResponse;
        } catch (JsonParseException e){
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
