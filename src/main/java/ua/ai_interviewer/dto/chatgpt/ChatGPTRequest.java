package ua.ai_interviewer.dto.chatgpt;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
public class ChatGPTRequest {

    private String model;
    private List<ChatMessage> messages;
    private Float temperature;

}
