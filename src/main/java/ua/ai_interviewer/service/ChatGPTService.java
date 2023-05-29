package ua.ai_interviewer.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.ai_interviewer.dto.chatgpt.ChatGPTMessages;
import ua.ai_interviewer.dto.chatgpt.ChatGPTRequest;
import ua.ai_interviewer.dto.chatgpt.ChatGPTResponse;
import ua.ai_interviewer.dto.wisper.TranscriptionResponse;
import ua.ai_interviewer.enums.ChatGPTModel;
import ua.ai_interviewer.enums.Role;
import ua.ai_interviewer.util.HttpRequestUtil;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static ua.ai_interviewer.enums.ChatGPTModel.WISPER;

@Service
@Slf4j
public class ChatGPTService {

    private final OkHttpClient client;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final HttpRequestUtil httpRequestUtil;

    public ChatGPTService(HttpClient httpClient, OkHttpClient client, ObjectMapper objectMapper, HttpRequestUtil httpRequestUtil) {
        this.httpClient = httpClient;
        this.client = client;
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
                    .messages(List.of(messages))
                    .build();

            HttpRequest httpRequest = httpRequestUtil.httpRequestToChatGPT(objectMapper.writeValueAsString(chatGPTRequest));

            log.info("Http request for to chat GPT is sending");

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            log.info("Response from chat GPT {} , {}", response, response.body());

            var chatGPTResponse = objectMapper.readValue(response.body(), ChatGPTResponse.class);
            chatGPTResponse.setHttpStatus(HttpStatus.valueOf(response.statusCode()));

            log.info("converted response {}", chatGPTResponse);
            return chatGPTResponse;
        } catch (JsonParseException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    public String transcribe(File file) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "openai.mp3",
                        RequestBody.create(MediaType.parse("audio/mpeg"), file))
                .addFormDataPart("model", WISPER.getValue())
                .build();

        var request = httpRequestUtil.requestToTranscribe(requestBody);
        log.trace("Request for to Wisper is sending");

        return executeRequest(request);


    }

    private String executeRequest(Request request) {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            String responseBody = Optional.ofNullable(response.body())
                    .orElseThrow()
                    .string();

            log.trace("Response from Wisper  {}", responseBody);

            return Optional.ofNullable(objectMapper.readValue(responseBody, TranscriptionResponse.class))
                    .orElseThrow()
                    .text();
        } catch (SocketTimeoutException e) {
            executeRequest(request);
        } catch (IOException e) {
            log.error("Unexpected error has occurred while executing request");
        }
        return null;
    }
}
