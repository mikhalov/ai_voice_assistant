package ua.ai_interviewer.dto.chatgpt;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@Builder
@ToString
public class ChatGPTRequest {

    private String model;
    private List<ChatMessage> messages;
    private Float temperature;
    private boolean stream;

}
