package ua.ai_interviewer.dto.chatgpt;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@ToString
@NoArgsConstructor
public class ChatGPTResponse {

    private String model;
    @ToString.Exclude
    private List<Choices> choices;
    private Usage usage;
}
