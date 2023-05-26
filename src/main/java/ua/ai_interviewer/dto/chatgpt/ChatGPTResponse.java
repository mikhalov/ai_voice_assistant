package ua.ai_interviewer.dto.chatgpt;

import lombok.*;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
@Setter
@RequiredArgsConstructor
@ToString
public class ChatGPTResponse {

    private String model;
    private List<ChatGPTChoices> choices;
    private HttpStatus httpStatus;
}
